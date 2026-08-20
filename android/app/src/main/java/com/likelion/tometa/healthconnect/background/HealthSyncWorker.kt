package com.likelion.tometa.healthconnect.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
) : CoroutineWorker(
    appContext,
    workerParams
) {

    override suspend fun doWork(): Result {
        val healthConnectManager =
            HealthConnectManager(
                applicationContext
            )

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
            HealthDeviceTokenStore(
                applicationContext
            )

        val healthConnectRepository =
            HealthConnectRepository(
                api =
                    HealthConnectApiClient.create(
                        API_BASE_URL
                    ),
                deviceIdProvider =
                    DeviceIdProvider(
                        applicationContext
                    ),
                healthDeviceTokenStore =
                    healthDeviceTokenStore
            )

        val healthSyncCoordinator =
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
            val httpException =
                e.findHttpException()

            if (
                httpException != null &&
                httpException.isRetryable()
            ) {
                retryOrWaitNextPeriod()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            retryOrWaitNextPeriod()
        }
    }

    private fun retryOrWaitNextPeriod(): Result {
        return if (
            runAttemptCount <
            MAX_RETRY_ATTEMPTS
        ) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun Throwable.findHttpException(): HttpException? {
        var current: Throwable? =
            this

        while (current != null) {
            if (current is HttpException) {
                return current
            }

            current =
                current.cause
        }

        return null
    }

    private fun HttpException.isRetryable(): Boolean {
        return code() == 408 ||
                code() == 425 ||
                code() == 429 ||
                code() in 500..599
    }

    private companion object {
        const val API_BASE_URL =
            "https://14th-to-meta-frontend.vercel.app/"

        const val BACKGROUND_SYNC_DAYS =
            2L

        const val MAX_RETRY_ATTEMPTS =
            3
    }
}