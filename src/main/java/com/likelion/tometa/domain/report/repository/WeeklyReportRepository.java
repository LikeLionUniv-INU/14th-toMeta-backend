package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    Optional<WeeklyReport> findByUserAndWeekStartDate(
            User user,
            LocalDate weekStartDate
    );

    Optional<WeeklyReport> findByIdAndUserAndReportStatus(
            Long id,
            User user,
            String reportStatus
    );

    List<WeeklyReport>
    findAllByUserAndWeekStartDateBetweenAndReportStatusOrderByWeekStartDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String reportStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report
            from WeeklyReport report
            where report.user = :user
              and report.weekStartDate = :weekStartDate
            """)
    Optional<WeeklyReport> findByUserAndWeekStartDateForUpdate(
            @Param("user") User user,
            @Param("weekStartDate") LocalDate weekStartDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report
            from WeeklyReport report
            where report.id = :reportId
            """)
    Optional<WeeklyReport> findByIdForUpdate(
            @Param("reportId") Long reportId
    );

    @Query("""
            select distinct dailyRecord.user.id
            from DailyRecord dailyRecord
            where dailyRecord.recordDate between :weekStartDate and :weekEndDate
              and not exists (
                    select weeklyReport.id
                    from WeeklyReport weeklyReport
                    where weeklyReport.user = dailyRecord.user
                      and weeklyReport.weekStartDate = :weekStartDate
                      and weeklyReport.reportStatus = 'completed'
              )
            order by dailyRecord.user.id
            """)
    List<Long> findWeeklyReportGenerationTargetUserIds(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("weekEndDate") LocalDate weekEndDate
    );

    @Query("""
            select weeklyReport.id
            from WeeklyReport weeklyReport,
                 UserNotificationSetting setting
            where setting.user = weeklyReport.user
              and weeklyReport.weekStartDate = :weekStartDate
              and weeklyReport.reportStatus = 'completed'
              and setting.weeklyReportEnabled = true
              and setting.weeklyReportTime is not null
              and setting.weeklyReportTime <= :currentTime
              and (
                    weeklyReport.notificationStatus = 'pending'
                    or (
                        weeklyReport.notificationStatus = 'claimed'
                        and weeklyReport.notificationStartedAt <= :staleBefore
                    )
              )
            order by weeklyReport.id
            """)
    List<Long> findWeeklyNotificationTargetIds(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("currentTime") LocalTime currentTime,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Query("""
            select weeklyReport
            from WeeklyReport weeklyReport
            join fetch weeklyReport.user
            where weeklyReport.id = :reportId
            """)
    Optional<WeeklyReport> findByIdWithUser(
            @Param("reportId") Long reportId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update WeeklyReport weeklyReport
               set weeklyReport.notificationStatus = 'claimed',
                   weeklyReport.notificationStartedAt = :startedAt,
                   weeklyReport.notificationAttemptId = :attemptId
             where weeklyReport.id = :reportId
               and weeklyReport.reportStatus = 'completed'
               and (
                    weeklyReport.notificationStatus = 'pending'
                    or (
                        weeklyReport.notificationStatus = 'claimed'
                        and weeklyReport.notificationStartedAt <= :staleBefore
                    )
               )
            """)
    int claimWeeklyNotification(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update WeeklyReport weeklyReport
               set weeklyReport.notificationStatus = 'sending',
                   weeklyReport.notificationStartedAt = :deliveryStartedAt
             where weeklyReport.id = :reportId
               and weeklyReport.notificationStatus = 'claimed'
               and weeklyReport.notificationAttemptId = :attemptId
            """)
    int beginWeeklyNotificationDelivery(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId,
            @Param("deliveryStartedAt") LocalDateTime deliveryStartedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update WeeklyReport weeklyReport
               set weeklyReport.notificationStatus = 'sent',
                   weeklyReport.notificationStartedAt = null,
                   weeklyReport.notificationSentAt = :sentAt
             where weeklyReport.id = :reportId
               and weeklyReport.notificationStatus = 'sending'
               and weeklyReport.notificationAttemptId = :attemptId
            """)
    int markWeeklyNotificationSent(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update WeeklyReport weeklyReport
               set weeklyReport.notificationStatus = 'unknown'
             where weeklyReport.id = :reportId
               and weeklyReport.notificationStatus = 'sending'
               and weeklyReport.notificationAttemptId = :attemptId
            """)
    int markWeeklyNotificationUnknown(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update WeeklyReport weeklyReport
               set weeklyReport.notificationStatus = 'unknown'
             where weeklyReport.notificationStatus = 'sending'
               and weeklyReport.notificationStartedAt <= :staleBefore
            """)
    int markStaleWeeklyNotificationDeliveriesUnknown(
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update WeeklyReport weeklyReport
               set weeklyReport.notificationStatus = 'pending',
                   weeklyReport.notificationStartedAt = null,
                   weeklyReport.notificationAttemptId = null
             where weeklyReport.id = :reportId
               and weeklyReport.notificationStatus = 'claimed'
               and weeklyReport.notificationAttemptId = :attemptId
            """)
    int resetWeeklyNotificationClaim(
            @Param("reportId") Long reportId,
            @Param("attemptId") String attemptId
    );
}
