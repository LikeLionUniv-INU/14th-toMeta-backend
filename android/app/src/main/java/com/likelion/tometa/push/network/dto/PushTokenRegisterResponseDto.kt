package com.likelion.tometa.push.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRegisterResponseDto(
    val pushTokenId: Long
)