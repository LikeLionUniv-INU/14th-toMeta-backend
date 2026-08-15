package com.likelion.tometa.domain.health.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record DailyStepsRequestDto(

        @NotNull(message = "걸음 수 날짜는 필수입니다.")
        LocalDate date,

        @NotNull(message = "걸음 수는 필수입니다.")
        @PositiveOrZero(message = "걸음 수는 0 이상이어야 합니다.")
        @Max(value = Integer.MAX_VALUE, message = "걸음 수가 허용 범위를 초과했습니다.")
        Long totalSteps
) {
}
