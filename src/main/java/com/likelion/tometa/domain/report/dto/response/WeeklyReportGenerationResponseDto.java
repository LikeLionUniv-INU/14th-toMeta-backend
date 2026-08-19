package com.likelion.tometa.domain.report.dto.response;

import java.time.LocalDate;
import java.util.List;

public record WeeklyReportGenerationResponseDto(
        Long weeklyReportId,
        LocalDate startDate,
        LocalDate endDate,
        String reportStatus,
        String weeklySummary,
        List<String> analyses,
        String personalizedSolution
) {
}
