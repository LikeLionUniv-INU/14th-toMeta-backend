package com.likelion.tometa.domain.report.support;

public record DailyReportAiResult(
        String aiSummary,
        String aiAnalysis,
        String personalizedSolution
) {
}
