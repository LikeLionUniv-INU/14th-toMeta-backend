package com.likelion.tometa.healthconnect.network

import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.healthconnect.network.dto.HealthConnectionRequestDto
import com.likelion.tometa.healthconnect.sync.dto.HealthSyncRequestDto
import com.likelion.tometa.healthconnect.token.HealthDeviceTokenStore
import retrofit2.HttpException

class HealthConnectRepository(
    private val api: HealthConnectApi,
    private val deviceIdProvider: DeviceIdProvider,
    private val healthDeviceTokenStore: HealthDeviceTokenStore
) {

    suspend fun connect(
        cookieHeader: String
    ): String {

        val deviceId =
            deviceIdProvider
                .getOrCreateDeviceId()

        val response =
            api.connect(
                cookieHeader = cookieHeader,
                request = HealthConnectionRequestDto(
                    deviceId = deviceId
                )
            )

        if (!response.isSuccess) {
            throw IllegalStateException(
                "${response.code}: ${response.message}"
            )
        }

        val healthDeviceToken =
            response.result
                ?.healthDeviceToken
                ?: throw IllegalStateException(
                    "healthDeviceToken이 응답에 없습니다."
                )

        healthDeviceTokenStore.saveToken(
            healthDeviceToken
        )

        return healthDeviceToken
    }

    suspend fun sync(
        request: HealthSyncRequestDto
    ) {

        val healthDeviceToken =
            healthDeviceTokenStore
                .getToken()
                ?: throw IllegalStateException(
                    "저장된 healthDeviceToken이 없습니다."
                )

        val response =
            try {
                api.sync(
                    authorizationHeader =
                        "Bearer $healthDeviceToken",
                    request = request
                )
            } catch (e: HttpException) {
                throw IllegalStateException(
                    "HTTP ${e.code()}: ${e.message()}",
                    e
                )
            }

        if (!response.isSuccess) {
            throw IllegalStateException(
                "${response.code}: ${response.message}"
            )
        }
    }
}