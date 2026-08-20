package com.likelion.tometa.healthconnect.sync

import com.likelion.tometa.healthconnect.HealthConnectReader
import com.likelion.tometa.healthconnect.sync.dto.HealthSyncRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthSyncRequestFactory(
    private val healthConnectReader: HealthConnectReader
) {

    suspend fun create(
        startTime: Instant,
        endTime: Instant,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId
    ): HealthSyncRequestDto {
        val sleepRecords =
            healthConnectReader.readSleepRecords(
                startTime,
                endTime
            )

        val dailyHealthSummaries =
            healthConnectReader.readDailyHealthSummaries(
                startDate = startDate,
                endDateExclusive = endDateExclusive,
                endTime = endTime,
                zoneId = zoneId
            )

        return withContext(Dispatchers.Default) {
            HealthSyncRequestDto(
                records = sleepRecords.map(
                    HealthSyncMapper::fromSleep
                ),
                dailyHealthSummaries =
                    dailyHealthSummaries.map(
                        HealthSyncMapper::fromDailyHealthSummary
                    )
            )
        }
    }
}