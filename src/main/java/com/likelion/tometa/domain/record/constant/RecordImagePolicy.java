package com.likelion.tometa.domain.record.constant;

import java.util.Set;

public final class RecordImagePolicy {

    public static final int MAX_IMAGE_COUNT = 5;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public static String objectKeyPrefix(Long userId) {
        return "skin-images/%d/".formatted(userId);
    }

    private RecordImagePolicy() {
    }
}
