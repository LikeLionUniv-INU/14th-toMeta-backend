package com.likelion.tometa.domain.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyReportResponseDto(
        LocalDate date,
        boolean hasDailyReport,
        String skinCondition,
        HealthSummary healthSummary,
        String aiAnalysis,
        String personalizedSolution,
        String note,
        String skinCareTip
) {

    public static DailyReportResponseDto notGenerated(LocalDate date) {
        return new DailyReportResponseDto(
                date,
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public record HealthSummary(
            Integer sleepMinutes,
            BigDecimal skinTemperature,
            Integer exerciseDuration,
            Integer totalCaloriesBurned,
            MenstrualCycle menstrualCycle,
            BigDecimal avgSpo2
    ) {
    }

    public record MenstrualCycle(
            Integer menstrualCycleDay,
            Integer cycleLength
    ) {
    }
}
