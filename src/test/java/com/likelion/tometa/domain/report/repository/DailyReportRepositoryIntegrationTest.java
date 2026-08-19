package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class DailyReportRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DailyRecordRepository dailyRecordRepository;
    @Autowired
    private DailyReportRepository dailyReportRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void generationResult_isAppliedOnlyWhenVersionIsCurrent() {
        User user = userRepository.save(User.builder().build());
        DailyRecord record = dailyRecordRepository.save(DailyRecord.builder()
                .user(user)
                .recordDate(LocalDate.of(2026, 8, 12))
                .skinStatus("normal")
                .build());
        DailyReport report = dailyReportRepository.saveAndFlush(DailyReport.builder()
                .dailyRecord(record)
                .build());

        assertEquals(1, dailyReportRepository.markGeneratingIfCurrent(
                report.getId(),
                0L
        ));
        assertEquals(0, dailyReportRepository.markGeneratingIfCurrent(
                report.getId(),
                0L
        ));
        assertEquals(0, dailyReportRepository.markGeneratingIfCurrent(
                report.getId(),
                1L
        ));
        LocalDateTime initiallyCompletedAt = LocalDateTime.of(2026, 8, 13, 7, 0);
        assertEquals(1, dailyReportRepository.completeGenerationIfCurrent(
                report.getId(),
                0L,
                null,
                "initial summary",
                "initial analysis",
                "initial solution",
                initiallyCompletedAt
        ));

        DailyReport latest = dailyReportRepository.findById(report.getId()).orElseThrow();
        latest.invalidateForRegeneration();
        dailyReportRepository.flush();
        assertEquals(initiallyCompletedAt, latest.getGeneratedAt());
        assertEquals(1, dailyReportRepository.markGeneratingIfCurrent(
                report.getId(),
                1L
        ));
        assertEquals(0, dailyReportRepository.resetGenerationIfCurrent(
                report.getId(),
                0L
        ));
        assertEquals(1, dailyReportRepository.resetGenerationIfCurrent(
                report.getId(),
                1L
        ));
        assertEquals(1, dailyReportRepository.markGeneratingIfCurrent(
                report.getId(),
                1L
        ));

        latest = dailyReportRepository.findById(report.getId()).orElseThrow();
        latest.invalidateForRegeneration();
        dailyReportRepository.flush();
        assertEquals(0, dailyReportRepository.completeGenerationIfCurrent(
                report.getId(),
                1L,
                null,
                "stale summary",
                "stale analysis",
                "stale solution",
                LocalDateTime.now()
        ));

        assertEquals(1, dailyReportRepository.markGeneratingIfCurrent(
                report.getId(),
                2L
        ));
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 20, 1, 30);
        assertEquals(1, dailyReportRepository.completeGenerationIfCurrent(
                report.getId(),
                2L,
                null,
                "latest summary",
                "latest analysis",
                "latest solution",
                completedAt
        ));
        entityManager.clear();

        DailyReport completed = dailyReportRepository
                .findById(report.getId())
                .orElseThrow();
        assertEquals(2L, completed.getGenerationVersion());
        assertEquals("completed", completed.getReportStatus());
        assertEquals("latest summary", completed.getAiSummary());
        assertEquals("latest analysis", completed.getAiAnalysis());
        assertEquals("latest solution", completed.getPersonalizedSolution());
        assertEquals(initiallyCompletedAt, completed.getGeneratedAt());
        assertEquals(completedAt, completed.getRegeneratedAt());
    }
}
