package com.likelion.tometa.domain.health.dto.response;

import java.time.LocalDateTime;

public record HealthConnectStatusResponseDto(
        boolean connected,
        LocalDateTime lastSyncedAt
) {
}
