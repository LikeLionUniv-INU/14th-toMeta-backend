package com.likelion.tometa.domain.report.entity;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DailyReportTest {

    @Test
    void complete_recordsInitialGenerationOnceAndRegenerationPreservesIt() {
        DailyReport report = DailyReport.builder()
                .dailyRecord(DailyRecord.builder().build())
                .build();

        report.complete(null, "summary", "analysis", "solution");
        var initiallyGeneratedAt = report.getGeneratedAt();
        assertNotNull(initiallyGeneratedAt);
        assertNull(report.getRegeneratedAt());

        report.invalidateForRegeneration();
        assertEquals(initiallyGeneratedAt, report.getGeneratedAt());
        report.complete(null, "new summary", "new analysis", "new solution");

        assertEquals(initiallyGeneratedAt, report.getGeneratedAt());
        assertNotNull(report.getRegeneratedAt());
    }
}
