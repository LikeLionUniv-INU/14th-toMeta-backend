package com.likelion.tometa.healthconnect.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.likelion.tometa.config.ToMetaEndpoint
import com.likelion.tometa.healthconnect.HealthConnectManager
import com.likelion.tometa.healthconnect.HealthConnectReader
import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.healthconnect.network.HealthConnectApiClient
import com.likelion.tometa.healthconnect.network.HealthConnectRepository
import com.likelion.tometa.healthconnect.sync.HealthSyncCoordinator
import com.likelion.tometa.healthconnect.sync.HealthSyncRequestFactory
import com.likelion.tometa.healthconnect.token.HealthDeviceTokenStore
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class HealthSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val healthConnectManager = HealthConnectManager(applicationContext)

        if (!healthConnectManager.isAvailable()) {
            return Result.success()
        }

        if (!healthConnectManager.isBackgroundReadAvailable()) {
            return Result.success()
        }

        if (!healthConnectManager.hasAllPermissions()) {
            return Result.success()
        }

        if (!healthConnectManager.hasBackgroundReadPermission()) {
            return Result.success()
        }

        val healthDeviceTokenStore =
            HealthDeviceTokenStore(applicationContext)

        val healthConnectRepository = HealthConnectRepository(
            api = HealthConnectApiClient.create(ToMetaEndpoint.API_BASE_URL),
            deviceIdProvider = DeviceIdProvider(applicationContext),
            healthDeviceTokenStore = healthDeviceTokenStore
        )

        val healthSyncCoordinator = HealthSyncCoordinator(
            requestFactory = HealthSyncRequestFactory(
                HealthConnectReader(healthConnectManager)
            ),
            healthConnectRepository = healthConnectRepository
        )

        return try {
            healthSyncCoordinator.syncRecent(
                days = BACKGROUND_SYNC_DAYS
            )

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            retryOrWaitNextPeriod()
        } catch (e: IllegalStateException) {
            val httpException = e.findHttpException()

            when {
                httpException?.isAuthenticationFailure() == true -> {
                    handleAuthenticationFailure(
                        healthDeviceTokenStore
                    )
                }

                httpException?.isRetryable() == true -> {
                    retryOrWaitNextPeriod()
                }

                else -> {
                    Result.success()
                }
            }
        } catch (_: Exception) {
            retryOrWaitNextPeriod()
        }
    }

    private suspend fun handleAuthenticationFailure(
        healthDeviceTokenStore: HealthDeviceTokenStore
    ): Result {
        try {
            healthDeviceTokenStore.clearToken()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 토큰 삭제에 실패하더라도 반복 인증 실패를 막기 위해 Worker는 취소한다.
        }

        HealthSyncScheduler.cancel(
            applicationContext
        )

        return Result.success()
    }

    private fun retryOrWaitNextPeriod(): Result {
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun Throwable.findHttpException(): HttpException? {
        var current: Throwable? = this

        while (current != null) {
            if (current is HttpException) {
                return current
            }

            current = current.cause
        }

        return null
    }

    private fun HttpException.isAuthenticationFailure(): Boolean {
        return code() == 401 || code() == 403
    }

    private fun HttpException.isRetryable(): Boolean {
        return code() == 408 ||
                code() == 425 ||
                code() == 429 ||
                code() in 500..599
    }

    private companion object {
        const val BACKGROUND_SYNC_DAYS = 2L
        const val MAX_RETRY_ATTEMPTS = 3
    }
}