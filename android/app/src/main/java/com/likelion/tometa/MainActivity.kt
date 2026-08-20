package com.likelion.tometa

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.likelion.tometa.healthconnect.HealthConnectManager
import com.likelion.tometa.healthconnect.HealthConnectPermissions
import com.likelion.tometa.webview.HealthConnectWebBridge
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    companion object {
        private const val WEB_URL = "https://14th-to-meta-frontend.vercel.app"
        private const val WEB_VIEW_STATE_KEY = "web_view_state"
        private const val WEB_VIEW_URL_KEY = "web_view_url"
        private const val MAX_WEB_VIEW_STATE_BYTES = 512 * 1024
    }

    private var currentWebView: WebView? = null
    private var pendingPermissionReplyProxy: JavaScriptReplyProxy? = null
    private lateinit var healthConnectManager: HealthConnectManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectManager = HealthConnectManager(applicationContext)

        // WebView에서 anonymous_session 등의 Cookie 저장 허용
        CookieManager.getInstance().setAcceptCookie(true)

        setContent {
            ToMetaWebView(
                savedWebViewState = savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY),
                savedUrl = savedInstanceState?.getString(WEB_VIEW_URL_KEY)
            )
        }
    }

    @WebViewCompat.ExperimentalSaveState
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // 화면 재생성 시 현재 WebView 페이지와 방문 기록 보존
        currentWebView?.let { webView ->
            webView.url
                ?.takeIf { isTrustedUrl(Uri.parse(it)) }
                ?.let { outState.putString(WEB_VIEW_URL_KEY, it) }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
                val webViewState = Bundle()

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

    private fun isTrustedUrl(uri: Uri): Boolean {
        val trustedUri = Uri.parse(WEB_URL)

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
        val intent = Intent(
            Intent.ACTION_VIEW,
            uri
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        try {
            if (
                intent.resolveActivity(
                    context.packageManager
                ) != null
            ) {
                context.startActivity(intent)
            }
        } catch (_: ActivityNotFoundException) {
            // 처리 가능한 앱이 없으면 현재 WebView 화면 유지
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ToMetaWebView(
        savedWebViewState: Bundle?,
        savedUrl: String?
    ) {
        var webView by remember {
            mutableStateOf<WebView?>(null)
        }

        var canGoBack by remember {
            mutableStateOf(false)
        }

        val permissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    PermissionController
                        .createRequestPermissionResultContract()
            ) { grantedPermissions ->

                val result =
                    if (
                        grantedPermissions.containsAll(
                            HealthConnectPermissions.READ_PERMISSIONS
                        )
                    ) {
                        HealthConnectWebBridge.RESULT_GRANTED
                    } else {
                        HealthConnectWebBridge.RESULT_DENIED
                    }

                pendingPermissionReplyProxy?.let { replyProxy ->
                    runCatching {
                        replyProxy.postMessage(result)
                    }
                }

                pendingPermissionReplyProxy = null
            }

        BackHandler(enabled = canGoBack) {
            webView?.goBack()
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {

                    // React 실행을 위해 JavaScript 활성화
                    settings.javaScriptEnabled = true

                    // localStorage, sessionStorage 사용 허용
                    settings.domStorageEnabled = true

                    // HTTPS 페이지에서 HTTP 리소스 로드 차단
                    settings.mixedContentMode =
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // 신뢰된 React Origin에서만 Health Connect Native Bridge 허용
                    HealthConnectWebBridge(
                        trustedOrigin = WEB_URL,
                        healthConnectManager = healthConnectManager,
                        onRequestPermissions = { replyProxy ->

                            if (pendingPermissionReplyProxy != null) {
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
                                    view?.canGoBack() == true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val uri = request.url

                                if (isTrustedUrl(uri)) {
                                    return false
                                }

                                // 신뢰하지 않는 서브프레임 요청 차단
                                if (!request.isForMainFrame) {
                                    return true
                                }

                                // 메인 프레임의 외부 HTTP(S) 링크는 시스템 브라우저로 처리
                                if (
                                    uri.scheme == "http" ||
                                    uri.scheme == "https"
                                ) {
                                    openExternalUrl(
                                        context,
                                        uri
                                    )
                                }

                                return true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                // 외부 도메인으로의 메인 프레임 POST 요청 차단
                                if (
                                    request.isForMainFrame &&
                                    request.method.equals(
                                        "POST",
                                        ignoreCase = true
                                    ) &&
                                    !isTrustedUrl(request.url)
                                ) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        403,
                                        "Forbidden",
                                        emptyMap(),
                                        ByteArrayInputStream(
                                            ByteArray(0)
                                        )
                                    )
                                }

                                return super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        }

                    // 저장된 WebView 상태가 있으면 우선 복원
                    val restored =
                        savedWebViewState?.let {
                            restoreState(it)
                        } != null

                    if (!restored) {
                        // 저장 URL도 신뢰 주소인지 검증 후 로드
                        val urlToLoad =
                            savedUrl
                                ?.let(Uri::parse)
                                ?.takeIf(::isTrustedUrl)
                                ?.toString()
                                ?: WEB_URL

                        loadUrl(urlToLoad)
                    }
                }
            },
            update = { view ->
                webView = view
                currentWebView = view
                canGoBack = view.canGoBack()
            },
            onRelease = { view ->
                if (currentWebView === view) {
                    currentWebView = null
                }

                pendingPermissionReplyProxy = null

                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            }
        )
    }
}
