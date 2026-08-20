package com.likelion.tometa.healthconnect.sync

import com.likelion.tometa.healthconnect.network.HealthConnectRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthSyncCoordinator(
    private val requestFactory: HealthSyncRequestFactory,
    private val healthConnectRepository: HealthConnectRepository
) {

    suspend fun syncRecent(
        days: Long = DEFAULT_SYNC_DAYS
    ) {
        require(days > 0) {
            "동기화 기간은 1일 이상이어야 합니다."
        }

        val zoneId =
            ZoneId.systemDefault()

        val today =
            LocalDate.now(zoneId)

        val startDate =
            today.minusDays(
                days - 1
            )

        val endDateExclusive =
            today.plusDays(1)

        val endTime =
            Instant.now()

        val startTime =
            startDate
                .atStartOfDay(zoneId)
                .toInstant()

        val request =
            requestFactory.create(
                startTime = startTime,
                endTime = endTime,
                startDate = startDate,
                endDateExclusive = endDateExclusive,
                zoneId = zoneId
            )

        healthConnectRepository.sync(
            request
        )
    }

    private companion object {
        const val DEFAULT_SYNC_DAYS =
            30L
    }
}
