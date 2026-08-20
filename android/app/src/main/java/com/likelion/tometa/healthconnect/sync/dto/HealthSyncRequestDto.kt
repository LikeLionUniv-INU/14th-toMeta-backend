package com.likelion.tometa.healthconnect.sync.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthSyncRequestDto(
    val records: List<HealthRawRecordSyncDto>,
    val dailyHealthSummaries: List<DailyHealthSummarySyncDto>
)