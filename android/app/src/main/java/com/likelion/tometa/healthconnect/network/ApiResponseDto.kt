package com.likelion.tometa.healthconnect.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponseDto<T>(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: T? = null
)