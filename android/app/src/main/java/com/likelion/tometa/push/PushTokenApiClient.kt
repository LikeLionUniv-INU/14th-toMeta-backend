package com.likelion.tometa.push.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object PushTokenApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun create(baseUrl: String): PushTokenApi {
        val contentType = "application/json".toMediaType()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        return retrofit.create(PushTokenApi::class.java)
    }
}