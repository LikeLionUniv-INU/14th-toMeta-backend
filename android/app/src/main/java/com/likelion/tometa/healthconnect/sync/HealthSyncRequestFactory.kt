package com.likelion.tometa.healthconnect.sync

import com.likelion.tometa.healthconnect.HealthConnectReader
import com.likelion.tometa.healthconnect.sync.dto.HealthSyncRequestDto
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
                startTime = startTime,
                endTime = endTime
            )

        val heartRateRecords =
            healthConnectReader.readHeartRateRecords(
                startTime = startTime,
                endTime = endTime
            )

        val exerciseRecords =
            healthConnectReader.readExerciseRecords(
                startTime = startTime,
                endTime = endTime
            )

        val dailySteps =
            healthConnectReader.readDailySteps(
                startDate = startDate,
                endDateExclusive = endDateExclusive,
                endTime = endTime,
                zoneId = zoneId
            )

        val records = buildList {

            addAll(
                sleepRecords.map(
                    HealthSyncMapper::fromSleep
                )
            )

            addAll(
                heartRateRecords.map(
                    HealthSyncMapper::fromHeartRate
                )
            )

            addAll(
                exerciseRecords.map(
                    HealthSyncMapper::fromExercise
                )
            )
        }

        return HealthSyncRequestDto(
            records = records,
            dailySteps = dailySteps.map(
                HealthSyncMapper::fromDailySteps
            )
        )
    }
}