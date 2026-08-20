package com.likelion.tometa.push.network

import com.likelion.tometa.healthconnect.device.DeviceIdProvider
import com.likelion.tometa.push.network.dto.PushTokenRegisterRequestDto

class PushTokenRepository(
    private val api: PushTokenApi,
    private val deviceIdProvider: DeviceIdProvider
) {

    suspend fun register(cookieHeader: String, firebaseInstallationId: String) {
        val response = api.register(
            cookieHeader = cookieHeader,
            request = PushTokenRegisterRequestDto(
                deviceId = deviceIdProvider.getOrCreateDeviceId(),
                firebaseInstallationId = firebaseInstallationId
            )
        )

        if (!response.isSuccess || response.result == null) {
            throw IllegalStateException("푸시 토큰 등록에 실패했습니다.")
        }
    }
}