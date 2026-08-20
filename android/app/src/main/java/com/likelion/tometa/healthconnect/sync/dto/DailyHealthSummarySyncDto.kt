package com.likelion.tometa.healthconnect.sync.dto

import kotlinx.serialization.Serializable

@Serializable
data class DailyHealthSummarySyncDto(
    val date: String,
    val sleepMinutes: Int?,
    val skinTemperatureCelsius: Double?,
    val exerciseMinutes: Int?,
    val totalCaloriesBurned: Int?,
    val menstrualCycleDay: Int?,
    val avgSpo2: Double?
)