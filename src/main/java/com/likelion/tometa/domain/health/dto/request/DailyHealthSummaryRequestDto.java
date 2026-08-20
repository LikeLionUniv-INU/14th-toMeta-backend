package com.likelion.tometa.domain.health.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyHealthSummaryRequestDto(

        @NotNull(message = "일별 헬스 요약 날짜는 필수입니다.")
        LocalDate date,

        @PositiveOrZero(message = "수면 시간은 0 이상이어야 합니다.")
        Integer sleepMinutes,

        @DecimalMin(value = "0.0", message = "피부온도는 0 이상이어야 합니다.")
        @DecimalMax(value = "100.0", message = "피부온도는 100 이하여야 합니다.")
        BigDecimal skinTemperatureCelsius,

        @PositiveOrZero(message = "운동 시간은 0 이상이어야 합니다.")
        Integer exerciseMinutes,

        @PositiveOrZero(message = "총 소모 칼로리는 0 이상이어야 합니다.")
        Integer totalCaloriesBurned,

        @Min(value = 1, message = "생리주기 일수는 1 이상이어야 합니다.")
        @Max(value = 28, message = "생리주기 일수는 28 이하여야 합니다.")
        Integer menstrualCycleDay,

        @DecimalMin(value = "0.0", message = "산소포화도는 0 이상이어야 합니다.")
        @DecimalMax(value = "100.0", message = "산소포화도는 100 이하여야 합니다.")
        BigDecimal avgSpo2
) {
}