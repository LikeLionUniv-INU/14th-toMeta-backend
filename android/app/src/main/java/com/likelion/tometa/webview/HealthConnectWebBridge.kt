package com.likelion.tometa.webview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.likelion.tometa.healthconnect.HealthConnectManager

class HealthConnectWebBridge(
    private val trustedOrigin: String,
    private val healthConnectManager: HealthConnectManager,
    private val onRequestPermissions: (JavaScriptReplyProxy) -> Unit
) {

    companion object {
        const val JS_OBJECT_NAME = "ToMetaNative"
        const val REQUEST_PERMISSIONS = "requestHealthConnectPermissions"
        const val RESULT_GRANTED = "granted"
        const val RESULT_DENIED = "denied"
        const val RESULT_UNAVAILABLE = "unavailable"
        const val RESULT_BUSY = "busy"
        const val RESULT_UNSUPPORTED = "unsupported"
    }

    fun attach(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return
        }

        WebViewCompat.addWebMessageListener(
            webView,
            JS_OBJECT_NAME,
            setOf(trustedOrigin)
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (isMainFrame && isTrustedOrigin(sourceOrigin)) {
                when (message.data) {
                    REQUEST_PERMISSIONS -> {
                        if (!healthConnectManager.isAvailable()) {
                            replyProxy.postMessage(RESULT_UNAVAILABLE)
                        } else {
                            onRequestPermissions(replyProxy)
                        }
                    }

                    else -> replyProxy.postMessage(RESULT_UNSUPPORTED)
                }
            }
        }
    }

    private fun isTrustedOrigin(sourceOrigin: Uri): Boolean {
        val trustedUri = Uri.parse(trustedOrigin)

        return sourceOrigin.scheme.equals(trustedUri.scheme, ignoreCase = true) &&
                sourceOrigin.host.equals(trustedUri.host, ignoreCase = true) &&
                sourceOrigin.port == trustedUri.port
    }
}
