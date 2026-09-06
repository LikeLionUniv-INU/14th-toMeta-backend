package com.likelion.tometa.domain.report.scheduler;

import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.dto.response.DailyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.service.DailyReportNotificationService;
import com.likelion.tometa.domain.report.service.WeeklyReportGenerationService;
import com.likelion.tometa.domain.report.service.WeeklyReportNotificationService;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGenerationSchedulerTest {

    private static final Instant MONDAY_00_01_KST = Instant.parse("2026-08-23T15:01:00Z");

    private static final LocalDateTime MONDAY_00_01 =
            LocalDateTime.of(2026, 8, 24, 0, 1);

    @Mock
    private DailyRecordRepository dailyRecordRepository;

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private WeeklyReportRepository weeklyReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DailyReportGenerationService dailyReportGenerationService;

    @Mock
    private DailyReportNotificationService dailyReportNotificationService;

    @Mock
    private WeeklyReportGenerationService weeklyReportGenerationService;

    @Mock
    private WeeklyReportNotificationService weeklyReportNotificationService;

    private ReportGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReportGenerationScheduler(
                dailyRecordRepository,
                dailyReportRepository,
                weeklyReportRepository,
                userRepository,
                dailyReportGenerationService,
                dailyReportNotificationService,
                weeklyReportGenerationService,
                weeklyReportNotificationService,
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

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(dailyReportGenerationService.generate(user, reportDate))
                .thenReturn(ReportGenerationResult.generated(dailyReportResponse(10L, reportDate)));

        when(dailyReportNotificationService.send(10L, MONDAY_00_01))
                .thenReturn(new DailyReportNotificationService.NotificationResult(true, 1));

        scheduler.generateDailyReports();

        verify(dailyReportGenerationService)
                .generate(user, reportDate);

        verify(dailyReportNotificationService)
                .send(10L, MONDAY_00_01);
    }

    @Test
    void dailyScheduler_doesNotNotifyAlreadyCompletedReport() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        User user = User.builder().build();

        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L));

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(dailyReportGenerationService.generate(user, reportDate))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        scheduler.generateDailyReports();

        verify(dailyReportGenerationService).generate(user, reportDate);
        verify(dailyReportNotificationService, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dailyScheduler_continuesWithNextUserAfterGenerationFailure() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);

        User failedUser = User.builder().build();
        User nextUser = User.builder().build();

        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L, 2L));

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(failedUser));

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(nextUser));

        when(dailyReportGenerationService.generate(failedUser, reportDate))
                .thenThrow(new RuntimeException("generation failed"));

        when(dailyReportGenerationService.generate(nextUser, reportDate))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        assertDoesNotThrow(scheduler::generateDailyReports);

        verify(dailyReportGenerationService).generate(failedUser, reportDate);
        verify(dailyReportGenerationService).generate(nextUser, reportDate);
        verify(dailyReportNotificationService, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dailyScheduler_continuesWithNextUserAfterNotificationFailure() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);

        User firstUser = User.builder().build();
        User nextUser = User.builder().build();

        when(dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate))
                .thenReturn(List.of(1L, 2L));

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(firstUser));

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(nextUser));

        when(dailyReportGenerationService.generate(firstUser, reportDate))
                .thenReturn(ReportGenerationResult.generated(dailyReportResponse(10L, reportDate)));

        when(dailyReportGenerationService.generate(nextUser, reportDate))
                .thenReturn(ReportGenerationResult.generated(dailyReportResponse(20L, reportDate)));

        when(dailyReportNotificationService.send(10L, MONDAY_00_01))
                .thenThrow(new RuntimeException("notification failed"));

        when(dailyReportNotificationService.send(20L, MONDAY_00_01))
                .thenReturn(new DailyReportNotificationService.NotificationResult(true, 1));

        assertDoesNotThrow(scheduler::generateDailyReports);

        verify(dailyReportGenerationService).generate(firstUser, reportDate);
        verify(dailyReportNotificationService).send(10L, MONDAY_00_01);
        verify(dailyReportGenerationService).generate(nextUser, reportDate);
        verify(dailyReportNotificationService).send(20L, MONDAY_00_01);
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

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

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

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(failedUser));

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(nextUser));

        when(weeklyReportGenerationService.generate(failedUser, weekStartDate))
                .thenThrow(new RuntimeException("generation failed"));

        when(weeklyReportGenerationService.generate(nextUser, weekStartDate))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        assertDoesNotThrow(scheduler::generateWeeklyReports);

        verify(weeklyReportGenerationService).generate(failedUser, weekStartDate);
        verify(weeklyReportGenerationService).generate(nextUser, weekStartDate);
    }

    @Test
    void weeklyNotificationScheduler_deliversDueMondayNotifications() {
        LocalDate weekStartDate = LocalDate.of(2026, 8, 17);

        when(weeklyReportRepository
                .findWeeklyNotificationTargetIds(
                        eq(weekStartDate),
                        eq(LocalTime.of(0, 1)),
                        eq(MONDAY_00_01.minusMinutes(5))
                ))
                .thenReturn(List.of(10L));

        when(weeklyReportNotificationService.send(10L, MONDAY_00_01))
                .thenReturn(new WeeklyReportNotificationService
                        .NotificationResult(true, 1));

        scheduler.sendWeeklyReportNotifications();

        verify(weeklyReportNotificationService)
                .send(10L, MONDAY_00_01);
    }

    @Test
    void dailyNotificationRecovery_marksStaleSendingAsUnknown() {
        when(dailyReportRepository
                .markStaleDailyNotificationDeliveriesUnknown(MONDAY_00_01.minusMinutes(5)))
                .thenReturn(1);

        scheduler.recoverStaleDailyNotificationDeliveries();

        verify(dailyReportRepository)
                .markStaleDailyNotificationDeliveriesUnknown(MONDAY_00_01.minusMinutes(5));
    }

    @Test
    void weeklyNotificationRecovery_marksStaleSendingAsUnknown() {
        when(weeklyReportRepository
                .markStaleWeeklyNotificationDeliveriesUnknown(MONDAY_00_01.minusMinutes(5)))
                .thenReturn(1);

        scheduler.recoverStaleWeeklyNotificationDeliveries();

        verify(weeklyReportRepository)
                .markStaleWeeklyNotificationDeliveriesUnknown(MONDAY_00_01.minusMinutes(5));
    }

    private DailyReportGenerationResponseDto dailyReportResponse(
            Long reportId,
            LocalDate reportDate
    ) {
        return new DailyReportGenerationResponseDto(
                reportId,
                reportDate,
                "completed",
                null,
                null,
                null
        );
    }
}
