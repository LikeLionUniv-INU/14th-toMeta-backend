package com.likelion.tometa.domain.record.dto.response;

import java.time.LocalDate;

public record DailyRecordUpdateResponseDto(
        Long recordId,
        LocalDate date
) {
}
