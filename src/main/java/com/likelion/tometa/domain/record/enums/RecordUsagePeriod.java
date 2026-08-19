package com.likelion.tometa.domain.record.enums;

import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;

public enum RecordUsagePeriod {

    MORNING("morning"),
    NIGHT("night");

    private final String value;

    RecordUsagePeriod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean supports(CosmeticSetUsageTime usageTime) {
        return usageTime == CosmeticSetUsageTime.BOTH
                || (this == MORNING && usageTime == CosmeticSetUsageTime.MORNING)
                || (this == NIGHT && usageTime == CosmeticSetUsageTime.NIGHT);
    }
}
