package com.likelion.tometa.healthconnect.sync

import androidx.health.connect.client.records.SleepSessionRecord
import com.likelion.tometa.healthconnect.model.DailyHealthSummary
import com.likelion.tometa.healthconnect.sync.dto.DailyHealthSummarySyncDto
import com.likelion.tometa.healthconnect.sync.dto.HealthRawRecordSyncDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

object HealthSyncMapper {

    fun fromSleep(
        record: SleepSessionRecord
    ): HealthRawRecordSyncDto {
        return HealthRawRecordSyncDto(
            hcRecordId = record.metadata.id,
            recordType = "SleepSessionRecord",
            startTime = record.startTime.toString(),
            endTime = record.endTime.toString(),
            payload = mapSleepPayload(record)
        )
    }

    fun fromDailyHealthSummary(
        summary: DailyHealthSummary
    ): DailyHealthSummarySyncDto {
        return DailyHealthSummarySyncDto(
            date = summary.date.toString(),
            sleepMinutes = summary.sleepMinutes,
            skinTemperatureCelsius =
                summary.skinTemperatureCelsius,
            exerciseMinutes = summary.exerciseMinutes,
            totalCaloriesBurned =
                summary.totalCaloriesBurned,
            menstrualCycleDay =
                summary.menstrualCycleDay,
            avgSpo2 = summary.avgSpo2
        )
    }

    private fun mapSleepPayload(
        record: SleepSessionRecord
    ): JsonObject {
        return buildJsonObject {
            put(
                "stageCount",
                record.stages.size
            )

            duration(
                "totalDuration",
                Duration.between(
                    record.startTime,
                    record.endTime
                )
            )

            put(
                "stages",
                buildJsonObject {
                    record.stages.forEachIndexed { index, stage ->
                        put(
                            index.toString(),
                            buildJsonObject {
                                instant(
                                    "startTime",
                                    stage.startTime
                                )

                                instant(
                                    "endTime",
                                    stage.endTime
                                )

                                put(
                                    "stageType",
                                    stage.stage
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    private fun JsonObjectBuilder.instant(
        key: String,
        value: Instant
    ) {
        put(
            key,
            value.toString()
        )
    }

    private fun JsonObjectBuilder.duration(
        key: String,
        value: Duration
    ) {
        put(
            key,
            buildJsonObject {
                put(
                    "seconds",
                    value.seconds
                )

                put(
                    "iso8601",
                    value.toString()
                )
            }
        )
    }
}