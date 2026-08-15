package com.likelion.tometa.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.likelion.tometa.healthconnect.model.DailySteps
import com.likelion.tometa.healthconnect.model.HealthConnectReadSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

class HealthConnectReader(
    private val healthConnectManager: HealthConnectManager
) {

    suspend fun readSleepRecords(
        startTime: Instant,
        endTime: Instant
    ): List<SleepSessionRecord> {
        return readAllRecords(
            recordType = SleepSessionRecord::class,
            startTime = startTime,
            endTime = endTime
        )
    }

    suspend fun readHeartRateRecords(
        startTime: Instant,
        endTime: Instant
    ): List<HeartRateRecord> {
        return readAllRecords(
            recordType = HeartRateRecord::class,
            startTime = startTime,
            endTime = endTime
        )
    }

    suspend fun readExerciseRecords(
        startTime: Instant,
        endTime: Instant
    ): List<ExerciseSessionRecord> {
        return readAllRecords(
            recordType = ExerciseSessionRecord::class,
            startTime = startTime,
            endTime = endTime
        )
    }

    suspend fun readDailySteps(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        endTime: Instant,
        zoneId: ZoneId
    ): List<DailySteps> {

        val results = mutableListOf<DailySteps>()

        var date = startDate

        while (date.isBefore(endDateExclusive)) {

            val dayStartTime = date
                .atStartOfDay(zoneId)
                .toInstant()

            val nextDayStartTime = date
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()

            val dayEndTime =
                if (nextDayStartTime.isAfter(endTime)) {
                    endTime
                } else {
                    nextDayStartTime
                }

            if (!dayStartTime.isBefore(dayEndTime)) {
                break
            }

            val response = healthConnectManager
                .getClient()
                .aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL
                        ),
                        timeRangeFilter = TimeRangeFilter.between(
                            dayStartTime,
                            dayEndTime
                        )
                    )
                )

            val totalSteps =
                response[StepsRecord.COUNT_TOTAL] ?: 0L

            results.add(
                DailySteps(
                    date = date,
                    totalSteps = totalSteps
                )
            )

            date = date.plusDays(1)
        }

        return results
    }

    suspend fun readSummary(
        startTime: Instant,
        endTime: Instant,
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zoneId: ZoneId
    ): HealthConnectReadSummary {

        val sleepRecords =
            readSleepRecords(startTime, endTime)

        val heartRateRecords =
            readHeartRateRecords(startTime, endTime)

        val exerciseRecords =
            readExerciseRecords(startTime, endTime)

        val dailySteps =
            readDailySteps(
                startDate = startDate,
                endDateExclusive = endDateExclusive,
                endTime = endTime,
                zoneId = zoneId
            )

        return HealthConnectReadSummary(
            sleepRecordCount = sleepRecords.size,
            heartRateRecordCount = heartRateRecords.size,
            heartRateSampleCount =
                heartRateRecords.sumOf {
                    it.samples.size
                },
            exerciseRecordCount = exerciseRecords.size,
            dailySteps = dailySteps
        )
    }

    private suspend fun <T : Record> readAllRecords(
        recordType: KClass<T>,
        startTime: Instant,
        endTime: Instant
    ): List<T> {

        val client: HealthConnectClient =
            healthConnectManager.getClient()

        val records = mutableListOf<T>()

        var pageToken: String? = null

        do {

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(
                        startTime,
                        endTime
                    ),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken
                )
            )

            records.addAll(response.records)

            pageToken = response.pageToken

        } while (pageToken != null)

        return records
    }

    companion object {
        private const val PAGE_SIZE = 1000
    }
}