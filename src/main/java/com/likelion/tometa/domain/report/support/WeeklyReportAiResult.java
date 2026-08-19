package com.likelion.tometa.domain.report.support;

import java.util.List;

public record WeeklyReportAiResult(
        String weeklySummary,
        List<String> analyses,
        String personalizedSolution
) {
}
