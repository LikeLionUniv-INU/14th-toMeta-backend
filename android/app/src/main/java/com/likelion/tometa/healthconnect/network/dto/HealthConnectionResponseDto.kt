package com.likelion.tometa.healthconnect.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthConnectionResponseDto(
    val healthDeviceToken: String
)