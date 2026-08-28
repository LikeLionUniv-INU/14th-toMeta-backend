package com.likelion.tometa.domain.record.event;

import java.time.LocalDate;

public record DailyRecordCreatedEvent(
        Long userId,
        LocalDate recordDate
) {
}
