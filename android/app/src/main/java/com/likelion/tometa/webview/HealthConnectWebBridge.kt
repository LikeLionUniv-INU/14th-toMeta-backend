package com.likelion.tometa.webview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.likelion.tometa.healthconnect.HealthConnectManager

class HealthConnectWebBridge(
    private val trustedOrigin: String,
    private val healthConnectManager: HealthConnectManager,
    private val onRequestPermissions: (JavaScriptReplyProxy) -> Unit
) {

    companion object {
        const val JS_OBJECT_NAME =
            "ToMetaNative"

        const val REQUEST_PERMISSIONS =
            "requestHealthConnectPermissions"

        const val RESULT_GRANTED =
            "granted"

        const val RESULT_DENIED =
            "denied"

        const val RESULT_UNAVAILABLE =
            "unavailable"

        const val RESULT_BUSY =
            "busy"

        const val RESULT_UNSUPPORTED =
            "unsupported"

        const val RESULT_CANCELLED =
            "cancelled"

        const val RESULT_SESSION_MISSING =
            "session_missing"

        const val RESULT_CONNECTION_FAILED =
            "connection_failed"
    }

    fun attach(
        webView: WebView
    ): Boolean {
        if (
            !WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER
            )
        ) {
            return false
        }

        WebViewCompat.addWebMessageListener(
            webView,
            JS_OBJECT_NAME,
            setOf(trustedOrigin)
        ) {
                _,
                message,
                sourceOrigin,
                isMainFrame,
                replyProxy ->

            // 신뢰된 Origin의 Main Frame 메시지만 처리
            if (
                !isMainFrame ||
                !isTrustedOrigin(
                    sourceOrigin
                )
            ) {
                return@addWebMessageListener
            }

            // 문자열 메시지만 지원
            if (
                message.type !=
                WebMessageCompat.TYPE_STRING
            ) {
                replyProxy.postMessage(
                    RESULT_UNSUPPORTED
                )

                return@addWebMessageListener
            }

            when (message.data) {
                REQUEST_PERMISSIONS -> {
                    if (
                        !healthConnectManager
                            .isAvailable()
                    ) {
                        replyProxy.postMessage(
                            RESULT_UNAVAILABLE
                        )
                    } else {
                        onRequestPermissions(
                            replyProxy
                        )
                    }
                }

                else -> {
                    replyProxy.postMessage(
                        RESULT_UNSUPPORTED
                    )
                }
            }
        }

        return true
    }

    private fun isTrustedOrigin(
        sourceOrigin: Uri
    ): Boolean {
        val trustedUri =
            Uri.parse(
                trustedOrigin
            )

        return sourceOrigin.scheme.equals(
            trustedUri.scheme,
            ignoreCase = true
        ) &&
                sourceOrigin.host.equals(
                    trustedUri.host,
                    ignoreCase = true
                ) &&
                sourceOrigin.port ==
                trustedUri.port
    }
}
