package com.likelion.tometa.domain.report.dto.response;

import java.time.LocalDate;

public record DailyReportGenerationResponseDto(
        Long dailyReportId,
        LocalDate date,
        String reportStatus,
        String aiSummary,
        String aiAnalysis,
        String personalizedSolution
) {
}
