package com.likelion.tometa.domain.home.dto.response;

import java.time.LocalDate;
import java.util.List;

public record HomeResponseDto(
        String nickname,
        Week week,
        YesterdayReport yesterdayReport,
        LatestDailyReport latestDailyReport,
        String skinCareTip
) {

    public record Week(
            LocalDate startDate,
            LocalDate endDate,
            List<Day> days
    ) {
    }

    public record Day(
            LocalDate date,
            String skinStatus
    ) {
    }

    public record YesterdayReport(
            boolean recordExists,
            boolean reportAvailable,
            Long dailyReportId,
            String summary,
            String actionGuide
    ) {
    }

    public record LatestDailyReport(
            Long dailyReportId,
            LocalDate date
    ) {
    }
}