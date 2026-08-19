package com.likelion.tometa.domain.report.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportGenerationContext(
        LocalDate date,
        String gender,
        String skinStatus,
        List<Cosmetic> morningCosmetics,
        List<Cosmetic> nightCosmetics,
        String foodMemo,
        String memo,
        HealthSummary healthSummary
) {

    public DailyReportGenerationContext {
        morningCosmetics = morningCosmetics == null ? List.of() : List.copyOf(morningCosmetics);
        nightCosmetics = nightCosmetics == null ? List.of() : List.copyOf(nightCosmetics);
    }

    public record Cosmetic(
            String productName,
            String productType,
            List<String> ingredients
    ) {
        public Cosmetic {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        }
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
