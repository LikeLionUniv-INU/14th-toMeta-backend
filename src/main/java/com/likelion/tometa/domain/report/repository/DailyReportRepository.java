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

    @Query("""
            select report
            from DailyReport report
            join fetch report.dailyRecord dailyRecord
            join fetch dailyRecord.user
            where report.id = :reportId
            """)
    Optional<DailyReport> findByIdWithUser(
            @Param("reportId") Long reportId
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.notificationStatus = 'claimed',
                   report.notificationStartedAt = :startedAt,
                   report.notificationAttemptId = :attemptId
             where report.id = :reportId
               and report.reportStatus = 'completed'
               and (
                    report.notificationStatus = 'pending'
                    or (
                        report.notificationStatus = 'claimed'
                        and report.notificationStartedAt <= :staleBefore
                    )
               )
            """)
    int claimDailyNotification(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.notificationStatus = 'sending',
                   report.notificationStartedAt = :deliveryStartedAt
             where report.id = :reportId
               and report.notificationStatus = 'claimed'
               and report.notificationAttemptId = :attemptId
            """)
    int beginDailyNotificationDelivery(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId,
            @Param("deliveryStartedAt") LocalDateTime deliveryStartedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.notificationStatus = 'sent',
                   report.notificationStartedAt = null,
                   report.notificationSentAt = :sentAt
             where report.id = :reportId
               and report.notificationStatus = 'sending'
               and report.notificationAttemptId = :attemptId
            """)
    int markDailyNotificationSent(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.notificationStatus = 'unknown'
             where report.id = :reportId
               and report.notificationStatus = 'sending'
               and report.notificationAttemptId = :attemptId
            """)
    int markDailyNotificationUnknown(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.notificationStatus = 'pending',
                   report.notificationStartedAt = null,
                   report.notificationAttemptId = null
             where report.id = :reportId
               and report.notificationStatus = 'claimed'
               and report.notificationAttemptId = :attemptId
            """)
    int resetDailyNotificationClaim(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update DailyReport report
               set report.notificationStatus = 'unknown'
             where report.notificationStatus = 'sending'
               and report.notificationStartedAt <= :staleBefore
            """)
    int markStaleDailyNotificationDeliveriesUnknown(
            @Param("staleBefore") LocalDateTime staleBefore
    );
}
