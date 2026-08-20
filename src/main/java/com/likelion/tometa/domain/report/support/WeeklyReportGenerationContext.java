package com.likelion.tometa.domain.report.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklyReportGenerationContext(
        LocalDate startDate,
        LocalDate endDate,
        String gender,
        List<Day> days
) {

    public WeeklyReportGenerationContext {
        days = List.copyOf(days);
    }

    public record Day(
            LocalDate date,
            boolean hasDailyReport,
            String skinCondition,
            String foodMemo,
            String memo,
            String aiSummary,
            String aiAnalysis,
            HealthSummary healthSummary
    ) {
    }

    public record HealthSummary(
            Integer sleepMinutes,
            BigDecimal skinTemperature,
            Integer exerciseDuration,
            Integer totalCaloriesBurned,
            Integer menstrualCycleDay,
            Integer menstrualCycleLength,
            BigDecimal avgSpo2
    ) {

        private static final int CYCLE_LENGTH = 28;

        public HealthSummary {
            menstrualCycleLength = menstrualCycleDay == null
                    ? null
                    : CYCLE_LENGTH;
        }

        public HealthSummary(
                Integer sleepMinutes,
                BigDecimal skinTemperature,
                Integer exerciseDuration,
                Integer totalCaloriesBurned,
                Integer menstrualCycleDay,
                BigDecimal avgSpo2
        ) {
            this(
                    sleepMinutes,
                    skinTemperature,
                    exerciseDuration,
                    totalCaloriesBurned,
                    menstrualCycleDay,
                    menstrualCycleDay == null
                            ? null
                            : CYCLE_LENGTH,
                    avgSpo2
            );
        }
    }
}
