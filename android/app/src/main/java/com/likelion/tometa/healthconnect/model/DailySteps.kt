package com.likelion.tometa.healthconnect.model

import java.time.LocalDate

data class DailySteps(
    val date: LocalDate,
    val totalSteps: Long
)