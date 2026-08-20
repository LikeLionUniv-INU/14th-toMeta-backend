package com.likelion.tometa

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.gms.tasks.Task
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.likelion.tometa.config.ToMetaEndpoint
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
import com.likelion.tometa.push.FirebaseInstallationIdStore
import com.likelion.tometa.push.PushNotificationChannel
import com.likelion.tometa.push.network.PushTokenApiClient
import com.likelion.tometa.push.network.PushTokenRepository
import com.likelion.tometa.webview.HealthConnectWebBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {

    companion object {
        private const val WEB_VIEW_STATE_KEY = "web_view_state"
        private const val WEB_VIEW_URL_KEY = "web_view_url"
        private const val ANONYMOUS_SESSION_COOKIE_NAME = "anonymous_session"
        private const val MAX_WEB_VIEW_STATE_BYTES = 512 * 1024
    }

    private var currentWebView: WebView? = null
    private var pendingPermissionReplyProxy: JavaScriptReplyProxy? = null
    private var pendingSyncReplyProxy: JavaScriptReplyProxy? = null
    private var pendingPushPermissionReplyProxy: JavaScriptReplyProxy? = null
    private var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private var healthConnectJob: Job? = null
    private var healthSyncJob: Job? = null
    private var pushRegistrationJob: Job? = null

    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var healthDeviceTokenStore: HealthDeviceTokenStore
    private lateinit var healthConnectRepository: HealthConnectRepository
    private lateinit var healthSyncCoordinator: HealthSyncCoordinator
    private lateinit var pushTokenRepository: PushTokenRepository
    private lateinit var deviceIdProvider: DeviceIdProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceIdProvider = DeviceIdProvider(applicationContext)
        healthConnectManager = HealthConnectManager(applicationContext)
        healthDeviceTokenStore = HealthDeviceTokenStore(applicationContext)
        healthConnectRepository = HealthConnectRepository(
            api = HealthConnectApiClient.create(ToMetaEndpoint.API_BASE_URL),
            deviceIdProvider = deviceIdProvider,
            healthDeviceTokenStore = healthDeviceTokenStore
        )
        healthSyncCoordinator = HealthSyncCoordinator(
            requestFactory = HealthSyncRequestFactory(HealthConnectReader(healthConnectManager)),
            healthConnectRepository = healthConnectRepository
        )
        pushTokenRepository = PushTokenRepository(
            api = PushTokenApiClient.create(ToMetaEndpoint.API_BASE_URL),
            deviceIdProvider = deviceIdProvider
        )

        CookieManager.getInstance().setAcceptCookie(true)
        PushNotificationChannel.ensureCreated(applicationContext)

        lifecycleScope.launch {
            updateBackgroundSyncScheduleSafely()
        }

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

        currentWebView?.let { webView ->
            webView.url
                ?.takeIf { isTrustedUrl(Uri.parse(it)) }
                ?.let { outState.putString(WEB_VIEW_URL_KEY, it) }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAVE_STATE)) {
                val webViewState = Bundle()
                WebViewCompat.saveState(webView, webViewState, MAX_WEB_VIEW_STATE_BYTES, false)
                outState.putBundle(WEB_VIEW_STATE_KEY, webViewState)
            }
        }
    }

    private fun isTrustedUrl(uri: Uri): Boolean {
        val trustedUri = Uri.parse(ToMetaEndpoint.WEB_URL)

        return uri.scheme.equals(trustedUri.scheme, ignoreCase = true) &&
                uri.host.equals(trustedUri.host, ignoreCase = true) &&
                uri.port == trustedUri.port
    }

    private fun openExternalUrl(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 처리 가능한 앱이 없으면 현재 WebView 화면 유지
        }
    }

    private fun getAnonymousSessionCookieHeader(): String? {
        val cookieHeader = CookieManager.getInstance().getCookie(ToMetaEndpoint.WEB_URL)
        val anonymousSessionValue = cookieHeader
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull {
                it.substringBefore("=") == ANONYMOUS_SESSION_COOKIE_NAME
            }
            ?.substringAfter("=", missingDelimiterValue = "")
            ?.trim()

        return if (cookieHeader.isNullOrBlank() || anonymousSessionValue.isNullOrBlank()) {
            null
        } else {
            cookieHeader
        }
    }

    private fun replyHealthConnection(
        replyProxy: JavaScriptReplyProxy,
        status: String,
        connectionRegistered: Boolean = false
    ) {
        runCatching {
            replyProxy.postMessage(
                HealthConnectWebBridge.connectionResponse(
                    status = status,
                    connectionRegistered = connectionRegistered
                )
            )
        }
    }

    private fun replyHealthSync(
        replyProxy: JavaScriptReplyProxy,
        status: String,
        synced: Boolean = false
    ) {
        runCatching {
            replyProxy.postMessage(
                HealthConnectWebBridge.syncResponse(
                    status = status,
                    synced = synced
                )
            )
        }
    }

    private suspend fun updateBackgroundSyncSchedule() {
        val hasToken = try {
            !healthDeviceTokenStore.getToken().isNullOrBlank()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HealthSyncScheduler.cancel(applicationContext)
            return
        }

        if (!hasToken) {
            HealthSyncScheduler.cancel(applicationContext)
            return
        }

        /*
         * Health Connect Provider가 업데이트 중이거나 일시적으로
         * unavailable인 경우 기존 스케줄까지 제거하지 않는다.
         */
        if (!healthConnectManager.isAvailable()) {
            return
        }

        if (!healthConnectManager.isBackgroundReadAvailable()) {
            HealthSyncScheduler.cancel(applicationContext)
            return
        }

        val hasRequiredPermissions = try {
            healthConnectManager.hasAllPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }

        val hasBackgroundPermission = try {
            healthConnectManager.hasBackgroundReadPermission()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }

        if (!hasRequiredPermissions || !hasBackgroundPermission) {
            HealthSyncScheduler.cancel(applicationContext)
            return
        }

        HealthSyncScheduler.schedule(applicationContext)
    }

    private suspend fun updateBackgroundSyncScheduleSafely() {
        try {
            updateBackgroundSyncSchedule()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 스케줄 갱신 실패는 연결 또는 데이터 동기화 결과에 영향을 주지 않는다.
        }
    }

    private fun connectHealthDevice(replyProxy: JavaScriptReplyProxy) {
        val cookieHeader = getAnonymousSessionCookieHeader()

        if (cookieHeader == null) {
            replyHealthConnection(
                replyProxy = replyProxy,
                status = HealthConnectWebBridge.RESULT_SESSION_MISSING
            )

            if (pendingPermissionReplyProxy === replyProxy) {
                pendingPermissionReplyProxy = null
            }
            return
        }

        val job = lifecycleScope.launch {
            val connectionSucceeded = try {
                healthConnectRepository.connect(cookieHeader)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            if (connectionSucceeded) {
                updateBackgroundSyncScheduleSafely()
            }

            if (pendingPermissionReplyProxy !== replyProxy) {
                return@launch
            }

            if (connectionSucceeded) {
                replyHealthConnection(
                    replyProxy = replyProxy,
                    status = HealthConnectWebBridge.RESULT_GRANTED,
                    connectionRegistered = true
                )
            } else {
                replyHealthConnection(
                    replyProxy = replyProxy,
                    status = HealthConnectWebBridge.RESULT_CONNECTION_FAILED
                )
            }

            if (pendingPermissionReplyProxy === replyProxy) {
                pendingPermissionReplyProxy = null
            }
        }

        healthConnectJob = job

        job.invokeOnCompletion {
            if (healthConnectJob === job) {
                healthConnectJob = null
            }
        }
    }

    private fun syncHealthData(replyProxy: JavaScriptReplyProxy) {
        if (
            pendingPermissionReplyProxy != null ||
            healthConnectJob?.isActive == true ||
            pendingSyncReplyProxy != null ||
            healthSyncJob?.isActive == true ||
            pendingPushPermissionReplyProxy != null ||
            pushRegistrationJob?.isActive == true
        ) {
            replyHealthSync(
                replyProxy = replyProxy,
                status = HealthConnectWebBridge.RESULT_SYNC_BUSY
            )
            return
        }

        pendingSyncReplyProxy = replyProxy

        val job = lifecycleScope.launch {
            val result = try {
                if (!healthConnectManager.isAvailable()) {
                    HealthConnectWebBridge.RESULT_UNAVAILABLE
                } else if (!healthConnectManager.hasAllPermissions()) {
                    HealthConnectWebBridge.RESULT_SYNC_PERMISSION_MISSING
                } else {
                    healthSyncCoordinator.syncRecent()
                    HealthConnectWebBridge.RESULT_SYNC_SUCCESS
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                HealthConnectWebBridge.RESULT_SYNC_FAILED
            }

            if (result == HealthConnectWebBridge.RESULT_SYNC_SUCCESS) {
                updateBackgroundSyncScheduleSafely()
            }

            if (pendingSyncReplyProxy !== replyProxy) {
                return@launch
            }

            replyHealthSync(
                replyProxy = replyProxy,
                status = result,
                synced = result == HealthConnectWebBridge.RESULT_SYNC_SUCCESS
            )

            if (pendingSyncReplyProxy === replyProxy) {
                pendingSyncReplyProxy = null
            }
        }

        healthSyncJob = job

        job.invokeOnCompletion {
            if (healthSyncJob === job) {
                healthSyncJob = null
            }
        }
    }

    private fun registerPushNotification(replyProxy: JavaScriptReplyProxy) {
        val cookieHeader = getAnonymousSessionCookieHeader()

        if (cookieHeader == null) {
            completePushRequest(
                replyProxy,
                HealthConnectWebBridge.RESULT_UNAVAILABLE
            )
            return
        }

        val job = lifecycleScope.launch {
            val result = try {
                FirebaseMessaging.getInstance().register().awaitCompletion()
                val installationId = getFirebaseInstallationId()

                if (!FirebaseInstallationIdStore(applicationContext).save(installationId)) {
                    throw IllegalStateException(
                        "Firebase Installation ID 저장에 실패했습니다."
                    )
                }

                pushTokenRepository.register(
                    cookieHeader = cookieHeader,
                    firebaseInstallationId = installationId
                )

                HealthConnectWebBridge.RESULT_GRANTED
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                HealthConnectWebBridge.RESULT_UNAVAILABLE
            }

            completePushRequest(replyProxy, result)
        }

        pushRegistrationJob = job

        job.invokeOnCompletion {
            if (pushRegistrationJob === job) {
                pushRegistrationJob = null
            }
        }
    }

    private fun completePushRequest(
        replyProxy: JavaScriptReplyProxy,
        result: String
    ) {
        if (pendingPushPermissionReplyProxy !== replyProxy) {
            return
        }

        runCatching {
            replyProxy.postMessage(result)
        }

        if (pendingPushPermissionReplyProxy === replyProxy) {
            pendingPushPermissionReplyProxy = null
        }
    }

    private suspend fun Task<*>.awaitCompletion() {
        suspendCancellableCoroutine<Unit> { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                if (task.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        task.exception
                            ?: IllegalStateException("Firebase 등록에 실패했습니다.")
                    )
                }
            }
        }
    }

    private suspend fun getFirebaseInstallationId(): String {
        return suspendCancellableCoroutine { continuation ->
            FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                if (!task.isSuccessful) {
                    continuation.resumeWithException(
                        task.exception
                            ?: IllegalStateException(
                                "Firebase Installation ID 조회에 실패했습니다."
                            )
                    )
                    return@addOnCompleteListener
                }

                val installationId = task.result

                if (!installationId.isNullOrBlank()) {
                    continuation.resume(installationId)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Firebase Installation ID 조회에 실패했습니다."
                        )
                    )
                }
            }
        }
    }

    private fun createFileChooserIntent(
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Intent {
        val acceptTypes = fileChooserParams.acceptTypes
            .filter { it.isNotBlank() }

        val acceptsImage = acceptTypes.isEmpty() ||
                acceptTypes.any {
                    it == "*/*" ||
                            it.startsWith("image/")
                }

        val cameraIntent = if (acceptsImage) {
            createCameraCaptureIntent()
        } else {
            null
        }

        if (
            fileChooserParams.isCaptureEnabled &&
            cameraIntent != null
        ) {
            return cameraIntent
        }

        val fileIntent = runCatching {
            fileChooserParams.createIntent()
        }.getOrElse {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (acceptsImage) "image/*" else "*/*"
            }
        }

        if (
            fileChooserParams.mode ==
            WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        ) {
            fileIntent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                true
            )
        }

        if (cameraIntent == null) {
            return fileIntent
        }

        return Intent.createChooser(
            fileIntent,
            fileChooserParams.title ?: "사진 선택"
        ).apply {
            putExtra(
                Intent.EXTRA_INITIAL_INTENTS,
                arrayOf(cameraIntent)
            )
        }
    }

    private fun createCameraCaptureIntent(): Intent? {
        val cameraDirectory = File(
            cacheDir,
            "camera"
        )

        if (
            !cameraDirectory.exists() &&
            !cameraDirectory.mkdirs()
        ) {
            return null
        }

        val cameraFile = runCatching {
            File.createTempFile(
                "tometa_",
                ".jpg",
                cameraDirectory
            )
        }.getOrNull() ?: return null

        val cameraUri = runCatching {
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                cameraFile
            )
        }.getOrElse {
            cameraFile.delete()
            return null
        }

        val cameraIntent = Intent(
            MediaStore.ACTION_IMAGE_CAPTURE
        ).apply {
            putExtra(
                MediaStore.EXTRA_OUTPUT,
                cameraUri
            )

            clipData = ClipData.newRawUri(
                "camera_image",
                cameraUri
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        if (
            !packageManager.hasSystemFeature(
                PackageManager.FEATURE_CAMERA_ANY
            )
        ) {
            cameraFile.delete()
            return null
        }

        pendingCameraFile = cameraFile
        pendingCameraUri = cameraUri

        return cameraIntent
    }

    private fun isAllowedFileChooserUri(
        uri: Uri,
        cameraUri: Uri?
    ): Boolean {
        if (
            cameraUri != null &&
            uri == cameraUri
        ) {
            return (pendingCameraFile?.length() ?: 0L) > 0L
        }

        if (
            !uri.scheme.equals(
                "content",
                ignoreCase = true
            )
        ) {
            return false
        }

        if (
            uri.authority ==
            "$packageName.fileprovider"
        ) {
            return false
        }

        return runCatching {
            contentResolver
                .openFileDescriptor(
                    uri,
                    "r"
                )
                ?.use {
                    true
                }
                ?: false
        }.getOrDefault(false)
    }

    private fun completeFileChooser(
        resultCode: Int,
        data: Intent?
    ) {
        val callback =
            pendingFileChooserCallback
                ?: return

        val cameraUri =
            pendingCameraUri

        val parsedUris =
            if (
                resultCode ==
                Activity.RESULT_OK
            ) {
                data
                    ?.let {
                        WebChromeClient
                            .FileChooserParams
                            .parseResult(
                                resultCode,
                                it
                            )
                    }
                    ?.filter {
                        isAllowedFileChooserUri(
                            uri = it,
                            cameraUri = cameraUri
                        )
                    }
                    ?.toTypedArray()
            } else {
                null
            }

        val capturedImageAvailable =
            cameraUri != null &&
                    (pendingCameraFile?.length() ?: 0L) > 0L

        val selectedUris = when {
            resultCode !=
                    Activity.RESULT_OK ->
                null

            !parsedUris.isNullOrEmpty() ->
                parsedUris

            capturedImageAvailable ->
                arrayOf(cameraUri!!)

            else ->
                null
        }

        val cameraImageSelected =
            cameraUri != null &&
                    selectedUris
                        ?.any {
                            it == cameraUri
                        } == true

        val cameraFile =
            pendingCameraFile

        pendingFileChooserCallback =
            null
        pendingCameraUri =
            null
        pendingCameraFile =
            null

        if (!cameraImageSelected) {
            cameraFile?.delete()
        }

        callback.onReceiveValue(
            selectedUris
        )
    }

    private fun cancelPendingFileChooser() {
        val callback =
            pendingFileChooserCallback
        val cameraFile =
            pendingCameraFile

        pendingFileChooserCallback =
            null
        pendingCameraUri =
            null
        pendingCameraFile =
            null

        cameraFile?.delete()

        callback?.onReceiveValue(
            null
        )
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

        val fileChooserLauncher =
            rememberLauncherForActivityResult(
                contract =
                    ActivityResultContracts
                        .StartActivityForResult()
            ) { result ->

                completeFileChooser(
                    resultCode =
                        result.resultCode,
                    data =
                        result.data
                )
            }

        val pushPermissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    ActivityResultContracts
                        .RequestPermission()
            ) { granted ->

                val replyProxy =
                    pendingPushPermissionReplyProxy
                        ?: return@rememberLauncherForActivityResult

                if (!granted) {
                    completePushRequest(
                        replyProxy,
                        HealthConnectWebBridge
                            .RESULT_DENIED
                    )
                    return@rememberLauncherForActivityResult
                }

                if (
                    !NotificationManagerCompat
                        .from(this@MainActivity)
                        .areNotificationsEnabled()
                ) {
                    completePushRequest(
                        replyProxy,
                        HealthConnectWebBridge
                            .RESULT_DENIED
                    )
                    return@rememberLauncherForActivityResult
                }

                registerPushNotification(
                    replyProxy
                )
            }

        /*
         * Background 권한은 필수 권한이 아니므로
         * 사용자가 거부해도 Health Connect 연결은 계속 진행한다.
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

        /*
         * History 권한은 생리주기 계산을 위해 과거 데이터를 조회하는 데 사용한다.
         * 사용자가 거부해도 Health Connect 연결 자체는 계속 진행한다.
         */
        val historyPermissionLauncher =
            rememberLauncherForActivityResult(
                contract =
                    PermissionController
                        .createRequestPermissionResultContract()
            ) {
                val replyProxy =
                    pendingPermissionReplyProxy
                        ?: return@rememberLauncherForActivityResult

                lifecycleScope.launch {
                    val shouldRequestBackgroundPermission =
                        try {
                            healthConnectManager
                                .isBackgroundReadAvailable() &&
                                    !healthConnectManager
                                        .hasBackgroundReadPermission()
                        } catch (
                            e: CancellationException
                        ) {
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
                        backgroundPermissionLauncher
                            .launch(
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
                        HealthConnectPermissions
                            .READ_PERMISSIONS
                    )

                if (!allGranted) {
                    replyHealthConnection(
                        replyProxy = replyProxy,
                        status =
                            HealthConnectWebBridge
                                .RESULT_DENIED
                    )

                    pendingPermissionReplyProxy =
                        null

                    return@rememberLauncherForActivityResult
                }

                lifecycleScope.launch {
                    val shouldRequestHistoryPermission =
                        try {
                            healthConnectManager
                                .isHistoryReadAvailable() &&
                                    !healthConnectManager
                                        .hasHistoryReadPermission()
                        } catch (
                            e: CancellationException
                        ) {
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
                        shouldRequestHistoryPermission
                    ) {
                        historyPermissionLauncher
                            .launch(
                                HealthConnectPermissions
                                    .HISTORY_READ_PERMISSIONS
                            )

                        return@launch
                    }

                    val shouldRequestBackgroundPermission =
                        try {
                            healthConnectManager
                                .isBackgroundReadAvailable() &&
                                    !healthConnectManager
                                        .hasBackgroundReadPermission()
                        } catch (
                            e: CancellationException
                        ) {
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
                        backgroundPermissionLauncher
                            .launch(
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
                        WebSettings
                            .MIXED_CONTENT_NEVER_ALLOW
                    settings.allowFileAccess =
                        false
                    settings.allowContentAccess =
                        true

                    val bridgeAttached =
                        HealthConnectWebBridge(
                            trustedOrigin =
                                ToMetaEndpoint.WEB_URL,
                            healthConnectManager =
                                healthConnectManager,
                            onRequestPermissions = {
                                    replyProxy ->

                                if (
                                    pendingPermissionReplyProxy != null ||
                                    pendingPushPermissionReplyProxy != null
                                ) {
                                    replyHealthConnection(
                                        replyProxy =
                                            replyProxy,
                                        status =
                                            HealthConnectWebBridge
                                                .RESULT_BUSY
                                    )
                                } else {
                                    pendingPermissionReplyProxy =
                                        replyProxy

                                    foregroundPermissionLauncher
                                        .launch(
                                            HealthConnectPermissions
                                                .READ_PERMISSIONS
                                        )
                                }
                            },
                            onRequestSync = {
                                    replyProxy ->

                                syncHealthData(
                                    replyProxy
                                )
                            },
                            onRequestPushPermission = {
                                    replyProxy ->

                                if (
                                    pendingPermissionReplyProxy != null ||
                                    pendingPushPermissionReplyProxy != null
                                ) {
                                    replyProxy.postMessage(
                                        HealthConnectWebBridge
                                            .RESULT_BUSY
                                    )
                                } else {
                                    pendingPushPermissionReplyProxy =
                                        replyProxy

                                    if (
                                        Build.VERSION.SDK_INT >=
                                        Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat
                                            .checkSelfPermission(
                                                this@MainActivity,
                                                Manifest.permission
                                                    .POST_NOTIFICATIONS
                                            ) !=
                                        PackageManager
                                            .PERMISSION_GRANTED
                                    ) {
                                        pushPermissionLauncher
                                            .launch(
                                                Manifest.permission
                                                    .POST_NOTIFICATIONS
                                            )
                                    } else if (
                                        !NotificationManagerCompat
                                            .from(
                                                this@MainActivity
                                            )
                                            .areNotificationsEnabled()
                                    ) {
                                        completePushRequest(
                                            replyProxy,
                                            HealthConnectWebBridge
                                                .RESULT_DENIED
                                        )
                                    } else {
                                        registerPushNotification(
                                            replyProxy
                                        )
                                    }
                                }
                            }
                        ).attach(this)

                    webChromeClient =
                        object :
                            WebChromeClient() {

                            override fun onShowFileChooser(
                                webView: WebView,
                                filePathCallback:
                                ValueCallback<Array<Uri>>,
                                fileChooserParams:
                                FileChooserParams
                            ): Boolean {
                                cancelPendingFileChooser()

                                pendingFileChooserCallback =
                                    filePathCallback

                                val chooserIntent =
                                    runCatching {
                                        createFileChooserIntent(
                                            fileChooserParams
                                        )
                                    }.getOrElse {
                                        cancelPendingFileChooser()
                                        return true
                                    }

                                return try {
                                    fileChooserLauncher.launch(
                                        chooserIntent
                                    )
                                    true
                                } catch (
                                    _: ActivityNotFoundException
                                ) {
                                    cancelPendingFileChooser()
                                    true
                                } catch (
                                    _: SecurityException
                                ) {
                                    cancelPendingFileChooser()
                                    true
                                }
                            }
                        }

                    webViewClient =
                        object :
                            WebViewClient() {

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
                                request:
                                WebResourceRequest
                            ): Boolean {
                                val uri =
                                    request.url

                                if (
                                    isTrustedUrl(uri)
                                ) {
                                    return false
                                }

                                if (
                                    !request
                                        .isForMainFrame
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
                                restoreState(it)
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
                                ?: ToMetaEndpoint
                                    .WEB_URL

                        loadUrl(urlToLoad)
                    }
                }
            },
            update = { view ->
                webView = view
                currentWebView = view
                canGoBack =
                    view.canGoBack()
            },
            onRelease = { view ->

                if (
                    currentWebView === view
                ) {
                    currentWebView = null
                }

                cancelPendingFileChooser()

                pendingPermissionReplyProxy
                    ?.let { replyProxy ->
                        replyHealthConnection(
                            replyProxy =
                                replyProxy,
                            status =
                                HealthConnectWebBridge
                                    .RESULT_CANCELLED
                        )
                    }

                pendingSyncReplyProxy
                    ?.let { replyProxy ->
                        replyHealthSync(
                            replyProxy =
                                replyProxy,
                            status =
                                HealthConnectWebBridge
                                    .RESULT_CANCELLED
                        )
                    }

                pendingPushPermissionReplyProxy
                    ?.let { replyProxy ->
                        runCatching {
                            replyProxy.postMessage(
                                HealthConnectWebBridge
                                    .RESULT_CANCELLED
                            )
                        }
                    }

                pendingPermissionReplyProxy =
                    null
                pendingSyncReplyProxy =
                    null
                pendingPushPermissionReplyProxy =
                    null

                healthConnectJob?.cancel()
                healthConnectJob = null

                healthSyncJob?.cancel()
                healthSyncJob = null

                pushRegistrationJob?.cancel()
                pushRegistrationJob = null

                view.stopLoading()
                view.removeAllViews()
                view.destroy()
            }
        )
    }
}
