package com.likelion.tometa.healthconnect.model

import java.time.LocalDate

data class DailyHealthSummary(
    val date: LocalDate,
    val sleepMinutes: Int?,
    val skinTemperatureCelsius: Double?,
    val exerciseMinutes: Int?,
    val totalCaloriesBurned: Int?,
    val menstrualCycleDay: Int?,
    val avgSpo2: Double?
)
