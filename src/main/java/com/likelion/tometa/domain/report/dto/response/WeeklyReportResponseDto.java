package com.likelion.tometa.domain.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklyReportResponseDto(
        Long reportId,
        int weekNumber,
        LocalDate startDate,
        LocalDate endDate,
        HealthSummary healthSummary,
        List<String> aiAnalysis,
        String personalizedSolution,
        String note
) {

    public record HealthSummary(
            List<SleepSession> sleepSession,
            List<DecimalValue> skinTemperature,
            List<IntegerValue> exerciseDuration,
            List<IntegerValue> totalCaloriesBurned,
            List<MenstrualCycle> menstrualCycle,
            List<DecimalValue> avgSpo2
    ) {
    }

    public record SleepSession(
            LocalDate date,
            Integer totalSleep,
            Integer awake,
            Integer lightSleep,
            Integer deepSleep,
            Integer remSleep
    ) {
    }

    public record DecimalValue(
            LocalDate date,
            BigDecimal value
    ) {
    }

    public record IntegerValue(
            LocalDate date,
            Integer value
    ) {
    }

    public record MenstrualCycle(
            LocalDate date,
            Integer menstrualCycleDay,
            Integer cycleLength
    ) {
    }
}