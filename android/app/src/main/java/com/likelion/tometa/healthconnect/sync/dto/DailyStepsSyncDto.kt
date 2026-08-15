package com.likelion.tometa.healthconnect.sync.dto

import kotlinx.serialization.Serializable

@Serializable
data class DailyStepsSyncDto(
    val date: String,
    val totalSteps: Long
)