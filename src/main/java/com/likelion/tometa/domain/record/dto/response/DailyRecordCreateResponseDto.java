package com.likelion.tometa.domain.record.dto.response;

import java.time.LocalDate;

public record DailyRecordCreateResponseDto(
        Long recordId,
        LocalDate date
) {
}
