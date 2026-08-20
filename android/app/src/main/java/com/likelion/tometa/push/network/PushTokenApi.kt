package com.likelion.tometa.push.network

import com.likelion.tometa.healthconnect.network.ApiResponseDto
import com.likelion.tometa.push.network.dto.PushTokenRegisterRequestDto
import com.likelion.tometa.push.network.dto.PushTokenRegisterResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PushTokenApi {

    @POST("api/push-tokens")
    suspend fun register(
        @Header("Cookie") cookieHeader: String,
        @Body request: PushTokenRegisterRequestDto
    ): ApiResponseDto<PushTokenRegisterResponseDto>
}