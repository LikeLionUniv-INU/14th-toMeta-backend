package com.likelion.tometa.healthconnect.network

import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.healthconnect.network.dto.HealthConnectionRequestDto
import com.likelion.tometa.healthconnect.sync.dto.HealthSyncRequestDto

class HealthConnectRepository(
    private val api: HealthConnectApi,
    private val deviceIdProvider: DeviceIdProvider
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

        return response.result
            ?.healthDeviceToken
            ?: throw IllegalStateException(
                "healthDeviceToken이 응답에 없습니다."
            )
    }

    suspend fun sync(
        healthDeviceToken: String,
        request: HealthSyncRequestDto
    ) {

        require(
            healthDeviceToken.isNotBlank()
        ) {
            "healthDeviceToken이 비어 있습니다."
        }

        val response =
            api.sync(
                authorizationHeader =
                    "Bearer $healthDeviceToken",
                request = request
            )

        if (!response.isSuccess) {
            throw IllegalStateException(
                "${response.code}: ${response.message}"
            )
        }
    }
}