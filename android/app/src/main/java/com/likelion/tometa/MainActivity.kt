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
import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.healthconnect.network.HealthConnectApiClient
import com.likelion.tometa.healthconnect.network.HealthConnectRepository
import com.likelion.tometa.healthconnect.token.HealthDeviceTokenStore
import com.likelion.tometa.webview.HealthConnectWebBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val WEB_URL = "https://14th-to-meta-frontend.vercel.app"
        private const val WEB_VIEW_STATE_KEY = "web_view_state"
        private const val WEB_VIEW_URL_KEY = "web_view_url"
        private const val ANONYMOUS_SESSION_COOKIE_NAME = "anonymous_session"
        private const val MAX_WEB_VIEW_STATE_BYTES = 512 * 1024
    }

    private var currentWebView: WebView? = null
    private var pendingPermissionReplyProxy: JavaScriptReplyProxy? = null
    private var healthConnectJob: Job? = null
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var healthConnectRepository: HealthConnectRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectManager =
            HealthConnectManager(applicationContext)

        healthConnectRepository =
            HealthConnectRepository(
                api = HealthConnectApiClient.create(
                    "$WEB_URL/"
                ),
                deviceIdProvider =
                    DeviceIdProvider(applicationContext),
                healthDeviceTokenStore =
                    HealthDeviceTokenStore(applicationContext)
            )

        // WebView에서 anonymous_session 등의 Cookie 저장 허용
        CookieManager.getInstance().setAcceptCookie(true)

        setContent {
            ToMetaWebView(
                savedWebViewState =
                    savedInstanceState?.getBundle(
                        WEB_VIEW_STATE_KEY
                    ),
                savedUrl =
                    savedInstanceState?.getString(
                        WEB_VIEW_URL_KEY
                    )
            )
        }
    }

    @WebViewCompat.ExperimentalSaveState
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // 화면 재생성 시 현재 WebView 페이지와 방문 기록 보존
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

                // WebView 상태 크기를 제한해 savedInstanceState 초과 방지
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
            Uri.parse(WEB_URL)

        return uri.scheme.equals(
            trustedUri.scheme,
            ignoreCase = true
        ) &&
                uri.host.equals(
                    trustedUri.host,
                    ignoreCase = true
                ) &&
                uri.port == trustedUri.port
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

    private fun connectHealthDevice(
        replyProxy: JavaScriptReplyProxy
    ) {
        val cookieHeader =
            CookieManager.getInstance()
                .getCookie(WEB_URL)

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

                        true
                    } catch (e: CancellationException) {
                        // Coroutine 취소를 일반 연결 실패로 처리하지 않음
                        throw e
                    } catch (_: Exception) {
                        false
                    }

                // WebView가 이미 종료되거나 새로운 요청으로 교체된 경우
                // 기존 ReplyProxy에는 응답하지 않음
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

        val permissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    PermissionController
                        .createRequestPermissionResultContract()
            ) { grantedPermissions ->

                val replyProxy =
                    pendingPermissionReplyProxy

                if (replyProxy != null) {
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
                    } else {
                        // Health Connect 권한 허용 후 서버 연결 등록
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

                    // React 실행을 위해 JavaScript 활성화
                    settings.javaScriptEnabled =
                        true

                    // localStorage, sessionStorage 사용 허용
                    settings.domStorageEnabled =
                        true

                    // HTTPS 페이지에서 HTTP 리소스 로드 차단
                    settings.mixedContentMode =
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // 원격 웹 콘텐츠만 사용하므로 로컬 파일 접근 차단
                    settings.allowFileAccess =
                        false

                    settings.allowContentAccess =
                        false

                    // 신뢰된 React Origin에서만 Health Connect Native Bridge 허용
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

                                    permissionLauncher.launch(
                                        HealthConnectPermissions.READ_PERMISSIONS
                                    )
                                }
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
                                            Uri.parse(url)
                                        )
                                    }.getOrDefault(false)
                                ) {
                                    // WebMessageListener 미지원 상태를 웹에 노출
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

                                // 신뢰하지 않는 서브프레임 요청 차단
                                if (
                                    !request.isForMainFrame
                                ) {
                                    return true
                                }

                                // 메인 프레임의 외부 HTTP(S) 링크는 시스템 브라우저로 처리
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

                    // 저장된 WebView 상태가 있으면 우선 복원
                    val restored =
                        savedWebViewState?.let {
                            restoreState(
                                it
                            )
                        } != null

                    if (!restored) {
                        // 저장 URL도 신뢰 주소인지 검증 후 로드
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

                // React에 먼저 요청 종료 상태 전달
                pendingPermissionReplyProxy?.let { replyProxy ->
                    runCatching {
                        replyProxy.postMessage(
                            HealthConnectWebBridge.RESULT_CANCELLED
                        )
                    }
                }

                pendingPermissionReplyProxy =
                    null

                // WebView 수명 종료 시 진행 중인 서버 연결 작업도 취소
                healthConnectJob?.cancel()
                healthConnectJob =
                    null

                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            }
        )
    }
}
