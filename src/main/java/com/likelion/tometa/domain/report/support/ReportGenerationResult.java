package com.likelion.tometa.domain.report.support;

public record ReportGenerationResult<T>(
        T response,
        boolean generated
) {

    public static <T> ReportGenerationResult<T> generated(T response) {
        return new ReportGenerationResult<>(response, true);
    }

    public static <T> ReportGenerationResult<T> alreadyCompleted(T response) {
        return new ReportGenerationResult<>(response, false);
    }
}
