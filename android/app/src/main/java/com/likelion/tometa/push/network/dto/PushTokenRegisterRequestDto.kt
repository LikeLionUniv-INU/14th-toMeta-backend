package com.likelion.tometa.push.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRegisterRequestDto(
    val deviceId: String,
    val firebaseInstallationId: String
)