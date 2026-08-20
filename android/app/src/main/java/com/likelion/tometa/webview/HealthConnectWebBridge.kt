package com.likelion.tometa.webview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.likelion.tometa.healthconnect.HealthConnectManager
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HealthConnectWebBridge(
    private val trustedOrigin: String,
    private val healthConnectManager: HealthConnectManager,
    private val onRequestPermissions: (JavaScriptReplyProxy) -> Unit,
    private val onRequestSync: (JavaScriptReplyProxy) -> Unit,
    private val onRequestPushPermission: (JavaScriptReplyProxy) -> Unit
) {

    companion object {
        const val JS_OBJECT_NAME = "ToMetaNative"

        const val REQUEST_PERMISSIONS = "requestHealthConnectPermissions"
        const val REQUEST_SYNC = "syncHealthConnectData"
        const val REQUEST_PUSH_PERMISSION = "requestPushPermission"

        const val RESULT_GRANTED = "granted"
        const val RESULT_DENIED = "denied"
        const val RESULT_UNAVAILABLE = "unavailable"
        const val RESULT_BUSY = "busy"
        const val RESULT_UNSUPPORTED = "unsupported"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_SESSION_MISSING = "session_missing"
        const val RESULT_CONNECTION_FAILED = "connection_failed"
        const val RESULT_SYNC_SUCCESS = "sync_success"
        const val RESULT_SYNC_FAILED = "sync_failed"
        const val RESULT_SYNC_BUSY = "sync_busy"
        const val RESULT_SYNC_PERMISSION_MISSING = "sync_permission_missing"

        const val RESPONSE_TYPE_CONNECTION = "healthConnectConnection"
        const val RESPONSE_TYPE_SYNC = "healthConnectSync"

        fun connectionResponse(
            status: String,
            connectionRegistered: Boolean = false
        ): String {
            return buildJsonObject {
                put("type", RESPONSE_TYPE_CONNECTION)
                put("status", status)
                put("connectionRegistered", connectionRegistered)
            }.toString()
        }

        fun syncResponse(
            status: String,
            synced: Boolean = false
        ): String {
            return buildJsonObject {
                put("type", RESPONSE_TYPE_SYNC)
                put("status", status)
                put("synced", synced)
            }.toString()
        }
    }

    fun attach(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return false
        }

        WebViewCompat.addWebMessageListener(
            webView,
            JS_OBJECT_NAME,
            setOf(trustedOrigin)
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (!isMainFrame || !isTrustedOrigin(sourceOrigin)) {
                return@addWebMessageListener
            }

            if (message.type != WebMessageCompat.TYPE_STRING) {
                replyProxy.postMessage(RESULT_UNSUPPORTED)
                return@addWebMessageListener
            }

            when (message.data) {
                REQUEST_PERMISSIONS -> {
                    if (!healthConnectManager.isAvailable()) {
                        replyProxy.postMessage(
                            connectionResponse(
                                status = RESULT_UNAVAILABLE
                            )
                        )
                    } else {
                        onRequestPermissions(replyProxy)
                    }
                }

                REQUEST_SYNC -> {
                    if (!healthConnectManager.isAvailable()) {
                        replyProxy.postMessage(
                            syncResponse(
                                status = RESULT_UNAVAILABLE
                            )
                        )
                    } else {
                        onRequestSync(replyProxy)
                    }
                }

                REQUEST_PUSH_PERMISSION -> {
                    onRequestPushPermission(replyProxy)
                }

                else -> {
                    replyProxy.postMessage(RESULT_UNSUPPORTED)
                }
            }
        }

        return true
    }

    private fun isTrustedOrigin(sourceOrigin: Uri): Boolean {
        val trustedUri = Uri.parse(trustedOrigin)

        return sourceOrigin.scheme.equals(
            trustedUri.scheme,
            ignoreCase = true
        ) &&
                sourceOrigin.host.equals(
                    trustedUri.host,
                    ignoreCase = true
                ) &&
                sourceOrigin.port == trustedUri.port
    }
}
