package com.likelion.tometa.domain.cosmetic.enums;

import java.util.Arrays;

public enum UsageTime {

    MORNING("morning"),
    EVENING("evening"),
    BOTH("both");

    private final String value;

    UsageTime(String value) {
        this.value = value;
    }

    public static boolean supports(String value) {
        return Arrays.stream(values())
                .anyMatch(usageTime -> usageTime.value.equals(value));
    }
}
