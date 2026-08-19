package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByDailyRecord(DailyRecord dailyRecord);

    Optional<DailyReport> findFirstByDailyRecord_UserAndReportStatusOrderByDailyRecord_RecordDateDesc(
            User user,
            String reportStatus
    );

    Optional<DailyReport> findByDailyRecord_UserAndDailyRecord_RecordDateAndReportStatus(
            User user,
            LocalDate recordDate,
            String reportStatus
    );

    List<DailyReport> findAllByDailyRecord_UserAndDailyRecord_RecordDateBetweenAndReportStatusOrderByDailyRecord_RecordDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String reportStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from DailyReport report where report.dailyRecord = :dailyRecord")
    Optional<DailyReport> findByDailyRecordForUpdate(
            @Param("dailyRecord") DailyRecord dailyRecord
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.reportStatus = 'generating'
             where report.id = :reportId
               and report.generationVersion = :generationVersion
               and report.reportStatus = 'collecting'
            """)
    int markGeneratingIfCurrent(
            @Param("reportId") Long reportId,
            @Param("generationVersion") long generationVersion
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.dailyHealthSummary = :dailyHealthSummary,
                   report.aiSummary = :aiSummary,
                   report.aiAnalysis = :aiAnalysis,
                   report.personalizedSolution = :personalizedSolution,
                   report.reportStatus = 'completed',
                   report.generatedAt = case
                       when report.generatedAt is null then :completedAt
                       else report.generatedAt
                   end,
                   report.regeneratedAt = case
                       when report.generatedAt is null then null
                       else :completedAt
                   end
             where report.id = :reportId
               and report.generationVersion = :generationVersion
               and report.reportStatus = 'generating'
            """)
    int completeGenerationIfCurrent(
            @Param("reportId") Long reportId,
            @Param("generationVersion") long generationVersion,
            @Param("dailyHealthSummary") DailyHealthSummary dailyHealthSummary,
            @Param("aiSummary") String aiSummary,
            @Param("aiAnalysis") String aiAnalysis,
            @Param("personalizedSolution") String personalizedSolution,
            @Param("completedAt") LocalDateTime completedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.reportStatus = 'collecting'
             where report.id = :reportId
               and report.generationVersion = :generationVersion
               and report.reportStatus = 'generating'
            """)
    int resetGenerationIfCurrent(
            @Param("reportId") Long reportId,
            @Param("generationVersion") long generationVersion
    );
}
