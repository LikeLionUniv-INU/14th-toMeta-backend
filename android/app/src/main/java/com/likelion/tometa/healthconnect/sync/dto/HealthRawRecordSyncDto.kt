package com.likelion.tometa.healthconnect.sync.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthRawRecordSyncDto(
    val hcRecordId: String,
    val recordType: String,
    val startTime: String,
    val endTime: String? = null,
    val payload: JsonObject
)