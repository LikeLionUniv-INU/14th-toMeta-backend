package com.likelion.tometa.domain.user.constant;

import java.time.Duration;

public final class RecordReminderPolicy {

    public static final Duration DELIVERY_TIMEOUT = Duration.ofMinutes(5);

    private RecordReminderPolicy() {
    }
}
