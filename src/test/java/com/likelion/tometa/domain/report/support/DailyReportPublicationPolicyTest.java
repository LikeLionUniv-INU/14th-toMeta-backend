package com.likelion.tometa.domain.report.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyReportPublicationPolicyTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 24);
    private static final String PUBLICATION_CRON = "0 55 22 * * *";

    @Test
    void beforePublicationTime_reportIsNotDue() {
        DailyReportPublicationPolicy policy = policyAt("2026-08-24T13:54:59Z");

        assertFalse(policy.isPublicationTimeReached(REPORT_DATE));
    }

    @Test
    void atPublicationTime_reportIsDue() {
        DailyReportPublicationPolicy policy = policyAt("2026-08-24T13:55:00Z");

        assertTrue(policy.isPublicationTimeReached(REPORT_DATE));
    }

    @Test
    void afterPublicationTime_reportIsDue() {
        DailyReportPublicationPolicy policy = policyAt("2026-08-24T14:00:00Z");

        assertTrue(policy.isPublicationTimeReached(REPORT_DATE));
    }

    private DailyReportPublicationPolicy policyAt(String instant) {
        return new DailyReportPublicationPolicy(
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                PUBLICATION_CRON
        );
    }
}
