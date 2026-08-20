package com.likelion.tometa.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.likelion.tometa.healthconnect.model.DailyHealthSummary
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.reflect.KClass

internal const val MENSTRUAL_CYCLE_LENGTH = 28

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

    suspend fun readDailyHealthSummaries(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        endTime: Instant,
        zoneId: ZoneId
    ): List<DailyHealthSummary> {
        val client = healthConnectManager.getClient()

        val startTime = startDate
            .atStartOfDay(zoneId)
            .toInstant()

        val oxygenSaturationRecords = readAllRecords(
            recordType = OxygenSaturationRecord::class,
            startTime = startTime,
            endTime = endTime
        )

        val previousMenstruationPeriod =
            readLatestMenstruationPeriodBefore(startTime)

        val menstruationPeriodStarts = buildList {
            previousMenstruationPeriod?.let {
                add(
                    it.startTime
                        .atZone(zoneId)
                        .toLocalDate()
                )
            }

            addAll(
                readAllRecords(
                    recordType = MenstruationPeriodRecord::class,
                    startTime = startTime,
                    endTime = endTime
                )
                    .map {
                        it.startTime
                            .atZone(zoneId)
                            .toLocalDate()
                    }
            )
        }
            .distinct()
            .sorted()

        val skinTemperatureRecords =
            if (
                client.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE
                ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            ) {
                readAllRecords(
                    recordType = SkinTemperatureRecord::class,
                    startTime = startTime,
                    endTime = endTime
                )
            } else {
                emptyList()
            }

        val results = mutableListOf<DailyHealthSummary>()
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

            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        SleepSessionRecord.SLEEP_DURATION_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        dayStartTime,
                        dayEndTime
                    )
                )
            )

            results.add(
                DailyHealthSummary(
                    date = date,
                    sleepMinutes = aggregate[
                        SleepSessionRecord.SLEEP_DURATION_TOTAL
                    ]?.toMinutes()?.toInt(),
                    skinTemperatureCelsius = calculateAverageSkinTemperature(
                        records = skinTemperatureRecords,
                        startTime = dayStartTime,
                        endTime = dayEndTime
                    ),
                    exerciseMinutes = aggregate[
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
                    ]?.toMinutes()?.toInt(),
                    totalCaloriesBurned = aggregate[
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL
                    ]?.inKilocalories?.roundToInt(),
                    menstrualCycleDay = calculateMenstrualCycleDay(
                        date = date,
                        periodStarts = menstruationPeriodStarts
                    ),
                    avgSpo2 = calculateAverageSpo2(
                        records = oxygenSaturationRecords,
                        startTime = dayStartTime,
                        endTime = dayEndTime
                    )
                )
            )

            date = date.plusDays(1)
        }

        return results
    }

    private suspend fun readLatestMenstruationPeriodBefore(
        endTime: Instant
    ): MenstruationPeriodRecord? {
        if (!healthConnectManager.hasHistoryReadPermission()) {
            return null
        }

        return try {
            healthConnectManager
                .getClient()
                .readRecords(
                    ReadRecordsRequest(
                        recordType = MenstruationPeriodRecord::class,
                        timeRangeFilter = TimeRangeFilter.before(endTime),
                        ascendingOrder = false,
                        pageSize = 1
                    )
                )
                .records
                .firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateAverageSpo2(
        records: List<OxygenSaturationRecord>,
        startTime: Instant,
        endTime: Instant
    ): Double? {
        val values = records
            .asSequence()
            .filter {
                !it.time.isBefore(startTime) &&
                        it.time.isBefore(endTime)
            }
            .map {
                it.percentage.value
            }
            .toList()

        return values
            .takeIf {
                it.isNotEmpty()
            }
            ?.average()
            ?.roundToTwoDecimals()
    }

    private fun calculateAverageSkinTemperature(
        records: List<SkinTemperatureRecord>,
        startTime: Instant,
        endTime: Instant
    ): Double? {
        val values = mutableListOf<Double>()

        records.forEach { record ->
            val baseline = record.baseline?.inCelsius
                ?: return@forEach

            record.deltas
                .filter {
                    !it.time.isBefore(startTime) &&
                            it.time.isBefore(endTime)
                }
                .forEach {
                    values.add(
                        baseline + it.delta.inCelsius
                    )
                }
        }

        return values
            .takeIf {
                it.isNotEmpty()
            }
            ?.average()
            ?.roundToTwoDecimals()
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

    private fun Double.roundToTwoDecimals(): Double {
        return (this * 100.0).roundToInt() / 100.0
    }

    companion object {
        private const val PAGE_SIZE = 1000
    }
}

internal fun calculateMenstrualCycleDay(
    date: LocalDate,
    periodStarts: List<LocalDate>
): Int? {
    val latestPeriodStart = periodStarts
        .lastOrNull {
            !it.isAfter(date)
        }
        ?: return null

    val daysFromStart = ChronoUnit.DAYS.between(
        latestPeriodStart,
        date
    ).toInt()

    return Math.floorMod(
        daysFromStart,
        MENSTRUAL_CYCLE_LENGTH
    ) + 1
}