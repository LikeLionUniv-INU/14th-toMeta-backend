package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
    @InjectMocks
    private WeeklyReportNotificationService service;

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
                1L,
                REQUESTED_AT,
                REQUESTED_AT.minusMinutes(5)
        )).thenReturn(1);
        when(weeklyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(report));
        when(pushNotificationService.sendWeeklyReportNotification(
                1L,
                WEEK_START_DATE
        )).thenReturn(2);

        WeeklyReportNotificationService.NotificationResult result =
                service.send(1L, REQUESTED_AT);

        assertTrue(result.processed());
        assertEquals(2, result.successCount());
        verify(weeklyReportRepository).markWeeklyNotificationSent(
                1L,
                REQUESTED_AT,
                REQUESTED_AT
        );
    }

    @Test
    void send_skipsWhenAnotherSchedulerAlreadyClaimed() {
        when(weeklyReportRepository.claimWeeklyNotification(
                1L,
                REQUESTED_AT,
                REQUESTED_AT.minusMinutes(5)
        )).thenReturn(0);

        WeeklyReportNotificationService.NotificationResult result =
                service.send(1L, REQUESTED_AT);

        assertFalse(result.processed());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void send_resetsClaimWhenDeliveryFails() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_START_DATE.plusDays(6))
                .build();
        when(weeklyReportRepository.claimWeeklyNotification(
                1L,
                REQUESTED_AT,
                REQUESTED_AT.minusMinutes(5)
        )).thenReturn(1);
        when(weeklyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(report));
        when(pushNotificationService.sendWeeklyReportNotification(
                1L,
                WEEK_START_DATE
        )).thenThrow(new IllegalStateException("delivery failed"));

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );

        verify(weeklyReportRepository).resetWeeklyNotification(
                1L,
                REQUESTED_AT
        );
    }
}
