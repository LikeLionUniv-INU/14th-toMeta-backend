package com.likelion.tometa.domain.record.event;

import java.time.LocalDate;

public record DailyRecordUpdatedEvent(
        Long userId,
        LocalDate recordDate
) {
}
