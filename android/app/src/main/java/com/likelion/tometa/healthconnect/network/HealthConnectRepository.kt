package com.likelion.tometa.healthconnect.network

import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.healthconnect.network.dto.HealthConnectionRequestDto

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
}