package com.likelion.tometa.domain.report.scheduler;

import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.service.WeeklyReportGenerationService;
import com.likelion.tometa.domain.report.service.WeeklyReportNotificationService;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationSchedulerTest {

    private static final Instant MONDAY_00_01_KST =
            Instant.parse("2026-08-23T15:01:00Z");

    @Mock
    private DailyRecordRepository dailyRecordRepository;
    @Mock
    private WeeklyReportRepository weeklyReportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DailyReportGenerationService dailyReportGenerationService;
    @Mock
    private WeeklyReportGenerationService weeklyReportGenerationService;
    @Mock
    private WeeklyReportNotificationService weeklyReportNotificationService;
    @Mock
    private PushNotificationService pushNotificationService;

    private ReportGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReportGenerationScheduler(
                dailyRecordRepository,
                weeklyReportRepository,
                userRepository,
                dailyReportGenerationService,
                weeklyReportGenerationService,
                weeklyReportNotificationService,
                pushNotificationService,
                Clock.fixed(
                        MONDAY_00_01_KST,
                        ZoneId.of("Asia/Seoul")
                )
        );
    }

    @Test
    void dailyScheduler_generatesPreviousDayAndNotifiesOnlyNewReport() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        User user = User.builder().build();
        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(dailyReportGenerationService.generate(user, reportDate))
                .thenReturn(ReportGenerationResult.generated(null));
        when(pushNotificationService
                .sendDailyReportNotification(1L, reportDate))
                .thenReturn(1);

        scheduler.generateDailyReports();

        verify(dailyReportGenerationService).generate(user, reportDate);
        verify(pushNotificationService)
                .sendDailyReportNotification(1L, reportDate);
    }

    @Test
    void dailyScheduler_doesNotNotifyAlreadyCompletedReport() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        User user = User.builder().build();
        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(dailyReportGenerationService.generate(user, reportDate))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        scheduler.generateDailyReports();

        verify(dailyReportGenerationService).generate(user, reportDate);
        verify(pushNotificationService, never())
                .sendDailyReportNotification(any(), any());
    }

    @Test
    void dailyScheduler_continuesWithNextUserAfterGenerationFailure() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        User failedUser = User.builder().build();
        User nextUser = User.builder().build();
        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L, 2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(failedUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(nextUser));
        when(dailyReportGenerationService.generate(failedUser, reportDate))
                .thenThrow(new RuntimeException("generation failed"));
        when(dailyReportGenerationService.generate(nextUser, reportDate))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        assertDoesNotThrow(scheduler::generateDailyReports);

        verify(dailyReportGenerationService)
                .generate(failedUser, reportDate);
        verify(pushNotificationService, never())
                .sendDailyReportNotification(1L, reportDate);
        verify(dailyReportGenerationService).generate(nextUser, reportDate);
    }

    @Test
    void dailyScheduler_continuesWithNextUserAfterNotificationFailure() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        User firstUser = User.builder().build();
        User nextUser = User.builder().build();
        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L, 2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(firstUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(nextUser));
        when(dailyReportGenerationService.generate(firstUser, reportDate))
                .thenReturn(ReportGenerationResult.generated(null));
        when(dailyReportGenerationService.generate(nextUser, reportDate))
                .thenReturn(ReportGenerationResult.generated(null));
        when(pushNotificationService
                .sendDailyReportNotification(1L, reportDate))
                .thenThrow(new RuntimeException("notification failed"));
        when(pushNotificationService
                .sendDailyReportNotification(2L, reportDate))
                .thenReturn(1);

        assertDoesNotThrow(scheduler::generateDailyReports);

        verify(dailyReportGenerationService)
                .generate(firstUser, reportDate);
        verify(pushNotificationService)
                .sendDailyReportNotification(1L, reportDate);
        verify(dailyReportGenerationService).generate(nextUser, reportDate);
        verify(pushNotificationService)
                .sendDailyReportNotification(2L, reportDate);
    }

    @Test
    void weeklyScheduler_usesPreviousMondayThroughSunday() {
        LocalDate weekStartDate = LocalDate.of(2026, 8, 17);
        LocalDate weekEndDate = LocalDate.of(2026, 8, 23);
        User user = User.builder().build();
        when(weeklyReportRepository
                .findWeeklyReportGenerationTargetUserIds(
                        weekStartDate,
                        weekEndDate
                ))
                .thenReturn(List.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(weeklyReportGenerationService.generate(user, weekStartDate))
                .thenReturn(ReportGenerationResult.generated(null));

        scheduler.generateWeeklyReports();

        verify(weeklyReportGenerationService)
                .generate(user, weekStartDate);
    }

    @Test
    void weeklyScheduler_continuesWithNextUserAfterGenerationFailure() {
        LocalDate weekStartDate = LocalDate.of(2026, 8, 17);
        LocalDate weekEndDate = LocalDate.of(2026, 8, 23);
        User failedUser = User.builder().build();
        User nextUser = User.builder().build();
        when(weeklyReportRepository
                .findWeeklyReportGenerationTargetUserIds(
                        weekStartDate,
                        weekEndDate
                ))
                .thenReturn(List.of(1L, 2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(failedUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(nextUser));
        when(weeklyReportGenerationService
                .generate(failedUser, weekStartDate))
                .thenThrow(new RuntimeException("generation failed"));
        when(weeklyReportGenerationService.generate(nextUser, weekStartDate))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        assertDoesNotThrow(scheduler::generateWeeklyReports);

        verify(weeklyReportGenerationService)
                .generate(failedUser, weekStartDate);
        verify(weeklyReportGenerationService)
                .generate(nextUser, weekStartDate);
    }

    @Test
    void weeklyNotificationScheduler_deliversDueMondayNotifications() {
        LocalDate weekStartDate = LocalDate.of(2026, 8, 17);
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 0, 1);
        when(weeklyReportRepository.findWeeklyNotificationTargetIds(
                eq(weekStartDate),
                eq(LocalTime.of(0, 1)),
                eq(now.minusMinutes(5))
        )).thenReturn(List.of(10L));
        when(weeklyReportNotificationService.send(10L, now))
                .thenReturn(new WeeklyReportNotificationService.NotificationResult(
                        true,
                        1
                ));

        scheduler.sendWeeklyReportNotifications();

        verify(weeklyReportNotificationService).send(10L, now);
    }

    @Test
    void weeklyNotificationRecovery_marksStaleSendingAsUnknown() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 0, 1);
        when(weeklyReportRepository
                .markStaleWeeklyNotificationDeliveriesUnknown(
                        now.minusMinutes(5)
                ))
                .thenReturn(1);

        scheduler.recoverStaleWeeklyNotificationDeliveries();

        verify(weeklyReportRepository)
                .markStaleWeeklyNotificationDeliveriesUnknown(
                        now.minusMinutes(5)
                );
    }
}
