package com.likelion.tometa

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

    // WebView에서 로드할 React 배포 주소
    companion object {
        private const val WEB_URL = "https://14th-to-meta-frontend.vercel.app"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 실행 시 React WebView 화면을 표시
        setContent {
            ToMetaWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun ToMetaWebView() {
        // 뒤로가기 처리를 위해 현재 WebView 인스턴스를 저장
        var webView by remember {
            mutableStateOf<WebView?>(null)
        }

        // WebView 방문 기록이 있으면 이전 웹 페이지로 이동
        BackHandler(
            enabled = webView?.canGoBack() == true
        ) {
            webView?.goBack()
        }

        // Compose 화면에 Android WebView를 생성
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // React 실행을 위해 JavaScript를 활성화
                    settings.javaScriptEnabled = true

                    // localStorage와 sessionStorage 사용을 허용
                    settings.domStorageEnabled = true

                    // HTTPS 페이지에서 HTTP 리소스 로드를 차단
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    // anonymous_session 등 WebView 쿠키 저장을 허용
                    CookieManager.getInstance().setAcceptCookie(true)

                    // 페이지 이동을 외부 브라우저가 아닌 WebView 내부에서 처리
                    webViewClient = WebViewClient()

                    // Vercel에 배포된 React 서비스를 로드
                    loadUrl(WEB_URL)

                    // 생성한 WebView를 뒤로가기 처리에 사용
                    webView = this
                }
            }
        )
    }
}