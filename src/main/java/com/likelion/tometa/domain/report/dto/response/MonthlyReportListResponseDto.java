package com.likelion.tometa.domain.report.dto.response;

import java.time.LocalDate;
import java.util.List;

public record MonthlyReportListResponseDto(
        int year,
        int month,
        List<DailyReportItem> dailyReports,
        List<WeeklyReportItem> weeklyReports
) {

    public record DailyReportItem(
            LocalDate date,
            boolean hasDailyReport,
            String skinCondition
    ) {
    }

    public record WeeklyReportItem(
            Long reportId,
            int weekNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }
}
