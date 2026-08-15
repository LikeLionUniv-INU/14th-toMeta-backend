package com.likelion.tometa.domain.health.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HealthSyncRequestDto(

        @NotNull(message = "동기화 데이터 목록은 필수입니다.")
        List<@Valid HealthRawRecordRequestDto> records
) {
}
