package com.likelion.tometa

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.likelion.tometa.healthconnect.HealthConnectManager
import com.likelion.tometa.healthconnect.HealthConnectPermissions
import com.likelion.tometa.healthconnect.HealthConnectReader
import com.likelion.tometa.healthconnect.background.HealthSyncScheduler
import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.healthconnect.network.HealthConnectApiClient
import com.likelion.tometa.healthconnect.network.HealthConnectRepository
import com.likelion.tometa.healthconnect.sync.HealthSyncCoordinator
import com.likelion.tometa.healthconnect.sync.HealthSyncRequestFactory
import com.likelion.tometa.healthconnect.token.HealthDeviceTokenStore
import com.likelion.tometa.webview.HealthConnectWebBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val WEB_URL =
            "https://14th-to-meta-frontend.vercel.app"

        private const val WEB_VIEW_STATE_KEY =
            "web_view_state"

        private const val WEB_VIEW_URL_KEY =
            "web_view_url"

        private const val ANONYMOUS_SESSION_COOKIE_NAME =
            "anonymous_session"

        private const val MAX_WEB_VIEW_STATE_BYTES =
            512 * 1024
    }

    private var currentWebView: WebView? = null
    private var pendingPermissionReplyProxy: JavaScriptReplyProxy? = null
    private var pendingSyncReplyProxy: JavaScriptReplyProxy? = null
    private var healthConnectJob: Job? = null
    private var healthSyncJob: Job? = null

    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var healthDeviceTokenStore: HealthDeviceTokenStore
    private lateinit var healthConnectRepository: HealthConnectRepository
    private lateinit var healthSyncCoordinator: HealthSyncCoordinator

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        healthConnectManager =
            HealthConnectManager(
                applicationContext
            )

        healthDeviceTokenStore =
            HealthDeviceTokenStore(
                applicationContext
            )

        healthConnectRepository =
            HealthConnectRepository(
                api =
                    HealthConnectApiClient.create(
                        "$WEB_URL/"
                    ),
                deviceIdProvider =
                    DeviceIdProvider(
                        applicationContext
                    ),
                healthDeviceTokenStore =
                    healthDeviceTokenStore
            )

        healthSyncCoordinator =
            HealthSyncCoordinator(
                requestFactory =
                    HealthSyncRequestFactory(
                        HealthConnectReader(
                            healthConnectManager
                        )
                    ),
                healthConnectRepository =
                    healthConnectRepository
            )

        CookieManager.getInstance()
            .setAcceptCookie(true)

        lifecycleScope.launch {
            updateBackgroundSyncSchedule()
        }

        setContent {
            ToMetaWebView(
                savedWebViewState =
                    savedInstanceState
                        ?.getBundle(
                            WEB_VIEW_STATE_KEY
                        ),
                savedUrl =
                    savedInstanceState
                        ?.getString(
                            WEB_VIEW_URL_KEY
                        )
            )
        }
    }

    @WebViewCompat.ExperimentalSaveState
    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        super.onSaveInstanceState(
            outState
        )

        currentWebView?.let { webView ->
            webView.url
                ?.takeIf {
                    isTrustedUrl(
                        Uri.parse(it)
                    )
                }
                ?.let {
                    outState.putString(
                        WEB_VIEW_URL_KEY,
                        it
                    )
                }

            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.SAVE_STATE
                )
            ) {
                val webViewState =
                    Bundle()

                WebViewCompat.saveState(
                    webView,
                    webViewState,
                    MAX_WEB_VIEW_STATE_BYTES,
                    false
                )

                outState.putBundle(
                    WEB_VIEW_STATE_KEY,
                    webViewState
                )
            }
        }
    }

    private fun isTrustedUrl(
        uri: Uri
    ): Boolean {
        val trustedUri =
            Uri.parse(
                WEB_URL
            )

        return uri.scheme.equals(
            trustedUri.scheme,
            ignoreCase = true
        ) &&
                uri.host.equals(
                    trustedUri.host,
                    ignoreCase = true
                ) &&
                uri.port ==
                trustedUri.port
    }

    private fun openExternalUrl(
        context: Context,
        uri: Uri
    ) {
        val intent =
            Intent(
                Intent.ACTION_VIEW,
                uri
            ).apply {
                addCategory(
                    Intent.CATEGORY_BROWSABLE
                )
            }

        try {
            context.startActivity(
                intent
            )
        } catch (_: ActivityNotFoundException) {
            // 처리 가능한 앱이 없으면 현재 WebView 화면 유지
        }
    }

    private suspend fun updateBackgroundSyncSchedule() {
        val hasToken =
            try {
                !healthDeviceTokenStore
                    .getToken()
                    .isNullOrBlank()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                HealthSyncScheduler.cancel(
                    applicationContext
                )

                return
            }

        if (!hasToken) {
            HealthSyncScheduler.cancel(
                applicationContext
            )

            return
        }

        /*
         * Health Connect Provider가 업데이트 중이거나 일시적으로
         * unavailable인 경우 기존 스케줄까지 제거하지 않는다.
         */
        if (!healthConnectManager.isAvailable()) {
            return
        }

        if (
            !healthConnectManager
                .isBackgroundReadAvailable()
        ) {
            HealthSyncScheduler.cancel(
                applicationContext
            )

            return
        }

        val hasRequiredPermissions =
            try {
                healthConnectManager
                    .hasAllPermissions()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }

        val hasBackgroundPermission =
            try {
                healthConnectManager
                    .hasBackgroundReadPermission()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }

        if (
            !hasRequiredPermissions ||
            !hasBackgroundPermission
        ) {
            HealthSyncScheduler.cancel(
                applicationContext
            )

            return
        }

        HealthSyncScheduler.schedule(
            applicationContext
        )
    }

    private fun connectHealthDevice(
        replyProxy: JavaScriptReplyProxy
    ) {
        val cookieHeader =
            CookieManager.getInstance()
                .getCookie(
                    WEB_URL
                )

        val anonymousSessionValue =
            cookieHeader
                ?.split(";")
                ?.map { cookie ->
                    cookie.trim()
                }
                ?.firstOrNull { cookie ->
                    cookie.substringBefore("=") ==
                            ANONYMOUS_SESSION_COOKIE_NAME
                }
                ?.substringAfter(
                    "=",
                    missingDelimiterValue = ""
                )
                ?.trim()

        if (
            cookieHeader.isNullOrBlank() ||
            anonymousSessionValue.isNullOrBlank()
        ) {
            runCatching {
                replyProxy.postMessage(
                    HealthConnectWebBridge.RESULT_SESSION_MISSING
                )
            }

            if (
                pendingPermissionReplyProxy ===
                replyProxy
            ) {
                pendingPermissionReplyProxy =
                    null
            }

            return
        }

        val job =
            lifecycleScope.launch {
                val connectionSucceeded =
                    try {
                        healthConnectRepository.connect(
                            cookieHeader
                        )

                        updateBackgroundSyncSchedule()

                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }

                if (
                    pendingPermissionReplyProxy !==
                    replyProxy
                ) {
                    return@launch
                }

                runCatching {
                    replyProxy.postMessage(
                        if (connectionSucceeded) {
                            HealthConnectWebBridge.RESULT_GRANTED
                        } else {
                            HealthConnectWebBridge.RESULT_CONNECTION_FAILED
                        }
                    )
                }

                if (
                    pendingPermissionReplyProxy ===
                    replyProxy
                ) {
                    pendingPermissionReplyProxy =
                        null
                }
            }

        healthConnectJob =
            job

        job.invokeOnCompletion {
            if (
                healthConnectJob ===
                job
            ) {
                healthConnectJob =
                    null
            }
        }
    }

    private fun syncHealthData(
        replyProxy: JavaScriptReplyProxy
    ) {
        if (
            pendingPermissionReplyProxy != null ||
            healthConnectJob?.isActive == true ||
            pendingSyncReplyProxy != null ||
            healthSyncJob?.isActive == true
        ) {
            runCatching {
                replyProxy.postMessage(
                    HealthConnectWebBridge.RESULT_SYNC_BUSY
                )
            }

            return
        }

        pendingSyncReplyProxy =
            replyProxy

        val job =
            lifecycleScope.launch {
                val result =
                    try {
                        if (
                            !healthConnectManager
                                .isAvailable()
                        ) {
                            HealthConnectWebBridge
                                .RESULT_UNAVAILABLE
                        } else if (
                            !healthConnectManager
                                .hasAllPermissions()
                        ) {
                            HealthConnectWebBridge
                                .RESULT_SYNC_PERMISSION_MISSING
                        } else {
                            healthSyncCoordinator
                                .syncRecent()

                            updateBackgroundSyncSchedule()

                            HealthConnectWebBridge
                                .RESULT_SYNC_SUCCESS
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        HealthConnectWebBridge
                            .RESULT_SYNC_FAILED
                    }

                if (
                    pendingSyncReplyProxy !==
                    replyProxy
                ) {
                    return@launch
                }

                runCatching {
                    replyProxy.postMessage(
                        result
                    )
                }

                if (
                    pendingSyncReplyProxy ===
                    replyProxy
                ) {
                    pendingSyncReplyProxy =
                        null
                }
            }

        healthSyncJob =
            job

        job.invokeOnCompletion {
            if (
                healthSyncJob ===
                job
            ) {
                healthSyncJob =
                    null
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ToMetaWebView(
        savedWebViewState: Bundle?,
        savedUrl: String?
    ) {
        var webView by remember {
            mutableStateOf<WebView?>(
                null
            )
        }

        var canGoBack by remember {
            mutableStateOf(
                false
            )
        }

        /*
         * Background 권한은 서비스 필수 권한이 아니므로
         * 사용자가 거부해도 서버 연결 자체는 계속 진행한다.
         */
        val backgroundPermissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    PermissionController
                        .createRequestPermissionResultContract()
            ) {
                val replyProxy =
                    pendingPermissionReplyProxy
                        ?: return@rememberLauncherForActivityResult

                connectHealthDevice(
                    replyProxy
                )
            }

        val foregroundPermissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    PermissionController
                        .createRequestPermissionResultContract()
            ) { grantedPermissions ->

                val replyProxy =
                    pendingPermissionReplyProxy
                        ?: return@rememberLauncherForActivityResult

                val allGranted =
                    grantedPermissions.containsAll(
                        HealthConnectPermissions.READ_PERMISSIONS
                    )

                if (!allGranted) {
                    runCatching {
                        replyProxy.postMessage(
                            HealthConnectWebBridge.RESULT_DENIED
                        )
                    }

                    pendingPermissionReplyProxy =
                        null

                    return@rememberLauncherForActivityResult
                }

                lifecycleScope.launch {
                    val shouldRequestBackgroundPermission =
                        try {
                            healthConnectManager
                                .isBackgroundReadAvailable() &&
                                    !healthConnectManager
                                        .hasBackgroundReadPermission()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false
                        }

                    if (
                        pendingPermissionReplyProxy !==
                        replyProxy
                    ) {
                        return@launch
                    }

                    if (
                        shouldRequestBackgroundPermission
                    ) {
                        backgroundPermissionLauncher.launch(
                            HealthConnectPermissions
                                .BACKGROUND_READ_PERMISSIONS
                        )
                    } else {
                        connectHealthDevice(
                            replyProxy
                        )
                    }
                }
            }

        BackHandler(
            enabled = canGoBack
        ) {
            webView?.goBack()
        }

        AndroidView(
            modifier =
                Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled =
                        true

                    settings.domStorageEnabled =
                        true

                    settings.mixedContentMode =
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    settings.allowFileAccess =
                        false

                    settings.allowContentAccess =
                        false

                    val bridgeAttached =
                        HealthConnectWebBridge(
                            trustedOrigin =
                                WEB_URL,
                            healthConnectManager =
                                healthConnectManager,
                            onRequestPermissions = { replyProxy ->
                                if (
                                    pendingPermissionReplyProxy !=
                                    null
                                ) {
                                    replyProxy.postMessage(
                                        HealthConnectWebBridge.RESULT_BUSY
                                    )
                                } else {
                                    pendingPermissionReplyProxy =
                                        replyProxy

                                    foregroundPermissionLauncher.launch(
                                        HealthConnectPermissions.READ_PERMISSIONS
                                    )
                                }
                            },
                            onRequestSync = { replyProxy ->
                                syncHealthData(
                                    replyProxy
                                )
                            }
                        ).attach(this)

                    webViewClient =
                        object : WebViewClient() {

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?
                            ) {
                                super.onPageFinished(
                                    view,
                                    url
                                )

                                if (
                                    !bridgeAttached &&
                                    view != null &&
                                    url != null &&
                                    runCatching {
                                        isTrustedUrl(
                                            Uri.parse(
                                                url
                                            )
                                        )
                                    }.getOrDefault(
                                        false
                                    )
                                ) {
                                    view.evaluateJavascript(
                                        """
                                        window.__TOMETA_NATIVE_BRIDGE_STATUS__ = 'unsupported';
                                        window.dispatchEvent(
                                            new CustomEvent(
                                                'tometa-native-bridge-status',
                                                { detail: 'unsupported' }
                                            )
                                        );
                                        """.trimIndent(),
                                        null
                                    )
                                }
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean
                            ) {
                                super.doUpdateVisitedHistory(
                                    view,
                                    url,
                                    isReload
                                )

                                canGoBack =
                                    view?.canGoBack() ==
                                            true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val uri =
                                    request.url

                                if (
                                    isTrustedUrl(
                                        uri
                                    )
                                ) {
                                    return false
                                }

                                if (
                                    !request.isForMainFrame
                                ) {
                                    return true
                                }

                                if (
                                    uri.scheme ==
                                    "http" ||
                                    uri.scheme ==
                                    "https"
                                ) {
                                    openExternalUrl(
                                        context,
                                        uri
                                    )
                                }

                                return true
                            }
                        }

                    val restored =
                        savedWebViewState
                            ?.let {
                                restoreState(
                                    it
                                )
                            } != null

                    if (!restored) {
                        val urlToLoad =
                            savedUrl
                                ?.let(
                                    Uri::parse
                                )
                                ?.takeIf(
                                    ::isTrustedUrl
                                )
                                ?.toString()
                                ?: WEB_URL

                        loadUrl(
                            urlToLoad
                        )
                    }
                }
            },
            update = { view ->
                webView =
                    view

                currentWebView =
                    view

                canGoBack =
                    view.canGoBack()
            },
            onRelease = { view ->
                if (
                    currentWebView ===
                    view
                ) {
                    currentWebView =
                        null
                }

                pendingPermissionReplyProxy
                    ?.let { replyProxy ->
                        runCatching {
                            replyProxy.postMessage(
                                HealthConnectWebBridge.RESULT_CANCELLED
                            )
                        }
                    }

                pendingSyncReplyProxy
                    ?.let { replyProxy ->
                        runCatching {
                            replyProxy.postMessage(
                                HealthConnectWebBridge.RESULT_CANCELLED
                            )
                        }
                    }

                pendingPermissionReplyProxy =
                    null

                pendingSyncReplyProxy =
                    null

                healthConnectJob?.cancel()
                healthConnectJob =
                    null

                healthSyncJob?.cancel()
                healthSyncJob =
                    null

                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            }
        )
    }
}
