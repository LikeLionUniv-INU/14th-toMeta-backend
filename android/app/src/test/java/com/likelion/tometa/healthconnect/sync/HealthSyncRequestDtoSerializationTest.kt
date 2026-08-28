package com.likelion.tometa.healthconnect.sync

import com.likelion.tometa.healthconnect.sync.dto.DailyHealthSummarySyncDto
import com.likelion.tometa.healthconnect.sync.dto.HealthRawRecordSyncDto
import com.likelion.tometa.healthconnect.sync.dto.HealthSyncRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncRequestDtoSerializationTest {

    private val json = Json

    @Test
    fun `HealthSyncRequestDto가 서버 계약에 맞게 직렬화된다`() {

        val request =
            HealthSyncRequestDto(
                records = listOf(
                    HealthRawRecordSyncDto(
                        hcRecordId = "record-1",
                        recordType = "SleepSessionRecord",
                        startTime = "2026-08-16T00:00:00Z",
                        endTime = "2026-08-16T01:00:00Z",
                        payload = buildJsonObject {
                            put("sampleCount", 1)
                        }
                    )
                ),
                dailyHealthSummaries = listOf(
                    DailyHealthSummarySyncDto(
                        date = "2026-08-16",
                        sleepMinutes = 420,
                        skinTemperatureCelsius = 36.5,
                        exerciseMinutes = 30,
                        totalCaloriesBurned = 2100,
                        menstrualCycleDay = null,
                        avgSpo2 = 97.5
                    )
                )
            )

        val result =
            json.encodeToString(request)

        assertTrue(result.contains("\"records\""))
        assertTrue(result.contains("\"dailyHealthSummaries\""))

        assertTrue(result.contains("\"hcRecordId\""))
        assertTrue(result.contains("\"recordType\""))
        assertTrue(result.contains("\"startTime\""))
        assertTrue(result.contains("\"endTime\""))
        assertTrue(result.contains("\"payload\""))

        assertTrue(result.contains("\"date\""))
        assertTrue(result.contains("\"sleepMinutes\""))
        assertTrue(result.contains("\"skinTemperatureCelsius\""))
        assertTrue(result.contains("\"exerciseMinutes\""))
        assertTrue(result.contains("\"totalCaloriesBurned\""))
        assertTrue(result.contains("\"avgSpo2\""))
    }
}