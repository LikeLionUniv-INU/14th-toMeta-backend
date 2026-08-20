package com.likelion.tometa.domain.health.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record HealthSyncRequestDto(

        @NotNull(message = "동기화 데이터 목록은 필수입니다.")
        List<@NotNull(message = "동기화 레코드는 null일 수 없습니다.") @Valid HealthRawRecordRequestDto> records,

        @NotNull(message = "일별 헬스 요약 목록은 필수입니다.")
        List<@NotNull(message = "일별 헬스 요약은 null일 수 없습니다.") @Valid DailyHealthSummaryRequestDto> dailyHealthSummaries
) {
}
