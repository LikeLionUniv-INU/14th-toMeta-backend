package com.likelion.tometa.healthconnect.sync

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.likelion.tometa.healthconnect.model.DailySteps
import com.likelion.tometa.healthconnect.sync.dto.DailyStepsSyncDto
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

    fun fromHeartRate(
        record: HeartRateRecord
    ): HealthRawRecordSyncDto {

        return HealthRawRecordSyncDto(
            hcRecordId = record.metadata.id,
            recordType = "HeartRateRecord",
            startTime = record.startTime.toString(),
            endTime = record.endTime.toString(),
            payload = mapHeartRatePayload(record)
        )
    }

    fun fromExercise(
        record: ExerciseSessionRecord
    ): HealthRawRecordSyncDto {

        return HealthRawRecordSyncDto(
            hcRecordId = record.metadata.id,
            recordType = "ExerciseSessionRecord",
            startTime = record.startTime.toString(),
            endTime = record.endTime.toString(),
            payload = mapExercisePayload(record)
        )
    }

    fun fromDailySteps(
        dailySteps: DailySteps
    ): DailyStepsSyncDto {

        return DailyStepsSyncDto(
            date = dailySteps.date.toString(),
            totalSteps = dailySteps.totalSteps
        )
    }

    private fun mapHeartRatePayload(
        record: HeartRateRecord
    ): JsonObject {

        return buildJsonObject {

            put(
                "sampleCount",
                record.samples.size
            )

            put(
                "samples",
                buildJsonObject {

                    record.samples.forEachIndexed { index, sample ->

                        put(
                            index.toString(),
                            buildJsonObject {

                                instant(
                                    "time",
                                    sample.time
                                )

                                put(
                                    "beatsPerMinute",
                                    sample.beatsPerMinute
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    private fun mapSleepPayload(
        record: SleepSessionRecord
    ): JsonObject {

        return buildJsonObject {

            record.title?.let {
                put("title", it)
            }

            record.notes?.let {
                put("notes", it)
            }

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

    private fun mapExercisePayload(
        record: ExerciseSessionRecord
    ): JsonObject {

        return buildJsonObject {

            put(
                "exerciseType",
                record.exerciseType
            )

            record.title?.let {
                put("title", it)
            }

            record.notes?.let {
                put("notes", it)
            }

            put(
                "segmentCount",
                record.segments.size
            )

            put(
                "lapCount",
                record.laps.size
            )

            record.plannedExerciseSessionId?.let {
                put(
                    "plannedExerciseSessionId",
                    it
                )
            }
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