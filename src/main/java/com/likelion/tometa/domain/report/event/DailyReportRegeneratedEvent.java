package com.likelion.tometa.domain.report.event;

import java.time.LocalDate;

public record DailyReportRegeneratedEvent(
        Long userId,
        LocalDate reportDate
) {
}
