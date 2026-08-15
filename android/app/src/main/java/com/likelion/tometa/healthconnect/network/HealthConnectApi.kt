package com.likelion.tometa.healthconnect.network

import com.likelion.tometa.healthconnect.network.dto.HealthConnectionRequestDto
import com.likelion.tometa.healthconnect.network.dto.HealthConnectionResponseDto
import com.likelion.tometa.healthconnect.sync.dto.HealthSyncRequestDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface HealthConnectApi {

    @POST("api/health-connect/connections")
    suspend fun connect(
        @Header("Cookie")
        cookieHeader: String,

        @Body
        request: HealthConnectionRequestDto
    ): ApiResponseDto<HealthConnectionResponseDto>

    @POST("api/health-connect/sync")
    suspend fun sync(
        @Header("Authorization")
        authorizationHeader: String,

        @Body
        request: HealthSyncRequestDto
    ): ApiResponseDto<Unit>
}