package com.likelion.tometa.healthconnect.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthConnectionRequestDto(
    val deviceId: String
)