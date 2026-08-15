package com.likelion.tometa.healthconnect.network

import kotlinx.serialization.json.Json
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

object HealthConnectApiClient {

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    fun create(
        baseUrl: String
    ): HealthConnectApi {

        val contentType =
            "application/json"
                .toMediaType()

        val retrofit =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(
                    json.asConverterFactory(
                        contentType
                    )
                )
                .build()

        return retrofit.create(
            HealthConnectApi::class.java
        )
    }
}