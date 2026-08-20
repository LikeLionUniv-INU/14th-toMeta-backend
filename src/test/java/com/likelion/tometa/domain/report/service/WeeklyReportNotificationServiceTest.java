package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReportNotificationServiceTest {

    private static final LocalDateTime REQUESTED_AT =
            LocalDateTime.of(2026, 8, 24, 10, 0);
    private static final LocalDate WEEK_START_DATE =
            LocalDate.of(2026, 8, 17);

    @Mock
    private WeeklyReportRepository weeklyReportRepository;
    @Mock
    private PushNotificationService pushNotificationService;
    private WeeklyReportNotificationService service;

    @BeforeEach
    void setUp() {
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");
        service = new WeeklyReportNotificationService(
                weeklyReportRepository,
                pushNotificationService,
                Clock.fixed(
                        REQUESTED_AT.atZone(koreaZone).toInstant(),
                        koreaZone
                )
        );
    }

    @Test
    void send_claimsAndMarksNotificationSent() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_START_DATE.plusDays(6))
                .build();
        when(weeklyReportRepository.claimWeeklyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);
        when(weeklyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(report));
        when(weeklyReportRepository.beginWeeklyNotificationDelivery(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);
        when(pushNotificationService.sendWeeklyReportNotification(
                1L,
                WEEK_START_DATE
        )).thenReturn(2);
        when(weeklyReportRepository.markWeeklyNotificationSent(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);

        WeeklyReportNotificationService.NotificationResult result =
                service.send(1L, REQUESTED_AT);

        assertTrue(result.processed());
        assertEquals(2, result.successCount());
        verify(weeklyReportRepository).markWeeklyNotificationSent(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        );
    }

    @Test
    void send_skipsWhenAnotherSchedulerAlreadyClaimed() {
        when(weeklyReportRepository.claimWeeklyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(0);

        WeeklyReportNotificationService.NotificationResult result =
                service.send(1L, REQUESTED_AT);

        assertFalse(result.processed());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void send_marksOutcomeUnknownWhenDeliveryFailsAfterStarting() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_START_DATE.plusDays(6))
                .build();
        when(weeklyReportRepository.claimWeeklyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);
        when(weeklyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(report));
        when(weeklyReportRepository.beginWeeklyNotificationDelivery(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);
        when(pushNotificationService.sendWeeklyReportNotification(
                1L,
                WEEK_START_DATE
        )).thenThrow(new IllegalStateException("delivery failed"));
        when(weeklyReportRepository.markWeeklyNotificationUnknown(
                eq(1L),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );

        verify(weeklyReportRepository, never())
                .resetWeeklyNotificationClaim(eq(1L), anyString());
        verify(weeklyReportRepository)
                .markWeeklyNotificationUnknown(eq(1L), anyString());
    }

    @Test
    void send_doesNotDeliverAgainWhenCompletionPersistenceFails() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_START_DATE.plusDays(6))
                .build();
        when(weeklyReportRepository.claimWeeklyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1, 0);
        when(weeklyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(report));
        when(weeklyReportRepository.beginWeeklyNotificationDelivery(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);
        when(pushNotificationService.sendWeeklyReportNotification(
                1L,
                WEEK_START_DATE
        )).thenReturn(1);
        when(weeklyReportRepository.markWeeklyNotificationSent(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenThrow(new IllegalStateException("database unavailable"));
        when(weeklyReportRepository.markWeeklyNotificationUnknown(
                eq(1L),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );
        WeeklyReportNotificationService.NotificationResult retry =
                service.send(1L, REQUESTED_AT);

        assertFalse(retry.processed());
        verify(pushNotificationService, times(1))
                .sendWeeklyReportNotification(1L, WEEK_START_DATE);
        verify(weeklyReportRepository, never())
                .resetWeeklyNotificationClaim(eq(1L), anyString());
        verify(weeklyReportRepository)
                .markWeeklyNotificationUnknown(eq(1L), anyString());
    }

    @Test
    void send_resetsClaimWhenFailureOccursBeforeDeliveryStarts() {
        when(weeklyReportRepository.claimWeeklyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);
        when(weeklyReportRepository.findByIdWithUser(1L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );

        verify(weeklyReportRepository)
                .resetWeeklyNotificationClaim(eq(1L), anyString());
        verifyNoInteractions(pushNotificationService);
    }
}
