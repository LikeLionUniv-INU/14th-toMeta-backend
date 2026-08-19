package com.likelion.tometa.domain.record.enums;

import java.util.Arrays;
import java.util.Optional;

public enum SkinStatus {

    VERY_GOOD("very_good"),
    GOOD("good"),
    NORMAL("normal"),
    BAD("bad"),
    VERY_BAD("very_bad");

    private final String value;

    SkinStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean requiresMemo() {
        return this == BAD || this == VERY_BAD;
    }

    public static Optional<SkinStatus> from(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst();
    }
}
