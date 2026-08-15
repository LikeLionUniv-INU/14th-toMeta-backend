package com.likelion.tometa.healthconnect.model

data class HealthConnectReadSummary(
    val sleepRecordCount: Int,
    val heartRateRecordCount: Int,
    val heartRateSampleCount: Int,
    val exerciseRecordCount: Int,
    val dailySteps: List<DailySteps>
)