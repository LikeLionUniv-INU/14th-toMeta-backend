package com.likelion.tometa.domain.cosmetic.enums;

import java.util.Arrays;
import java.util.Optional;

public enum CosmeticSetUsageTime {

    MORNING("morning"),
    NIGHT("night"),
    BOTH("both");

    private final String value;

    CosmeticSetUsageTime(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Optional<CosmeticSetUsageTime> from(String value) {
        return Arrays.stream(values())
                .filter(usageTime -> usageTime.value.equals(value))
                .findFirst();
    }
}
