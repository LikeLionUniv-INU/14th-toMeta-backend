package com.likelion.tometa.domain.report.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WeeklyReportNoteUpdateRequestDto(
        @NotNull(message = "Note는 필수입니다.")
        @Size(max = 300, message = "Note는 300자 이하여야 합니다.")
        String note
) {
}
