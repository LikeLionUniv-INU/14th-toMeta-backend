package com.likelion.tometa.healthconnect.sync

import com.likelion.tometa.healthconnect.network.HealthConnectRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthSyncCoordinator(
    private val requestFactory: HealthSyncRequestFactory,
    private val healthConnectRepository: HealthConnectRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun syncRecent(
        days: Long = DEFAULT_SYNC_DAYS
    ) {
        val range =
            createHealthSyncTimeRange(
                clock = clock,
                days = days
            )

        val request =
            requestFactory.create(
                startTime = range.startTime,
                endTime = range.endTime,
                startDate = range.startDate,
                endDateExclusive = range.endDateExclusive,
                zoneId = range.zoneId
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

internal data class HealthSyncTimeRange(
    val startTime: Instant,
    val endTime: Instant,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val zoneId: ZoneId
)

internal fun createHealthSyncTimeRange(
    clock: Clock,
    days: Long
): HealthSyncTimeRange {
    require(days > 0) {
        "동기화 기간은 1일 이상이어야 합니다."
    }

    val endTime =
        clock.instant()

    val zoneId =
        clock.zone

    val today =
        endTime
            .atZone(zoneId)
            .toLocalDate()

    val startDate =
        today.minusDays(
            days - 1
        )

    val endDateExclusive =
        today.plusDays(1)

    val startTime =
        startDate
            .atStartOfDay(zoneId)
            .toInstant()

    return HealthSyncTimeRange(
        startTime = startTime,
        endTime = endTime,
        startDate = startDate,
        endDateExclusive = endDateExclusive,
        zoneId = zoneId
    )
}
