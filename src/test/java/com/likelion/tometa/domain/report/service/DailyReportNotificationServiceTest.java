package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyReportNotificationServiceTest {

    private static final LocalDateTime REQUESTED_AT =
            LocalDateTime.of(2026, 8, 24, 10, 0);

    private static final LocalDate REPORT_DATE =
            LocalDate.of(2026, 8, 23);

    @Mock
    private DailyReportRepository dailyReportRepository;

    @Mock
    private PushNotificationService pushNotificationService;

    @Mock
    private DailyReport dailyReport;

    @Mock
    private DailyRecord dailyRecord;

    @Mock
    private User user;

    private DailyReportNotificationService service;

    @BeforeEach
    void setUp() {
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");

        service = new DailyReportNotificationService(
                dailyReportRepository,
                pushNotificationService,
                Clock.fixed(
                        REQUESTED_AT
                                .atZone(koreaZone)
                                .toInstant(),
                        koreaZone
                )
        );
    }

    @Test
    void send_claimsAndMarksNotificationSent() {
        prepareReport();

        when(dailyReportRepository.claimDailyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);

        when(dailyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(dailyReport));

        when(dailyReportRepository.beginDailyNotificationDelivery(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);

        when(pushNotificationService.sendDailyReportNotification(
                1L,
                REPORT_DATE
        )).thenReturn(2);

        when(dailyReportRepository.markDailyNotificationSent(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);

        DailyReportNotificationService.NotificationResult result =
                service.send(1L, REQUESTED_AT);

        assertTrue(result.processed());
        assertEquals(2, result.successCount());

        verify(dailyReportRepository)
                .markDailyNotificationSent(
                        eq(1L),
                        anyString(),
                        eq(REQUESTED_AT)
                );
    }

    @Test
    void send_skipsWhenNotificationAlreadyClaimed() {
        when(dailyReportRepository.claimDailyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(0);

        DailyReportNotificationService.NotificationResult result =
                service.send(1L, REQUESTED_AT);

        assertFalse(result.processed());

        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void send_marksOutcomeUnknownWhenDeliveryFailsAfterStarting() {
        prepareReport();

        when(dailyReportRepository.claimDailyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);

        when(dailyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(dailyReport));

        when(dailyReportRepository.beginDailyNotificationDelivery(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);

        when(pushNotificationService.sendDailyReportNotification(
                1L,
                REPORT_DATE
        )).thenThrow(
                new IllegalStateException("delivery failed")
        );

        when(dailyReportRepository.markDailyNotificationUnknown(
                eq(1L),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );

        verify(dailyReportRepository, never())
                .resetDailyNotificationClaim(
                        eq(1L),
                        anyString()
                );

        verify(dailyReportRepository)
                .markDailyNotificationUnknown(
                        eq(1L),
                        anyString()
                );
    }

    @Test
    void send_doesNotDeliverAgainAfterUnknownOutcome() {
        prepareReport();

        when(dailyReportRepository.claimDailyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1, 0);

        when(dailyReportRepository.findByIdWithUser(1L))
                .thenReturn(Optional.of(dailyReport));

        when(dailyReportRepository.beginDailyNotificationDelivery(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);

        when(pushNotificationService.sendDailyReportNotification(
                1L,
                REPORT_DATE
        )).thenReturn(1);

        when(dailyReportRepository.markDailyNotificationSent(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT)
        )).thenThrow(
                new IllegalStateException("database unavailable")
        );

        when(dailyReportRepository.markDailyNotificationUnknown(
                eq(1L),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );

        DailyReportNotificationService.NotificationResult retry =
                service.send(1L, REQUESTED_AT);

        assertFalse(retry.processed());

        verify(
                pushNotificationService,
                times(1)
        ).sendDailyReportNotification(
                1L,
                REPORT_DATE
        );
    }

    @Test
    void send_resetsClaimWhenFailureOccursBeforeDeliveryStarts() {
        when(dailyReportRepository.claimDailyNotification(
                eq(1L),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);

        when(dailyReportRepository.findByIdWithUser(1L))
                .thenThrow(
                        new IllegalStateException("database unavailable")
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REQUESTED_AT)
        );

        verify(dailyReportRepository)
                .resetDailyNotificationClaim(
                        eq(1L),
                        anyString()
                );

        verifyNoInteractions(pushNotificationService);
    }

    private void prepareReport() {
        when(dailyReport.getDailyRecord())
                .thenReturn(dailyRecord);

        when(dailyRecord.getUser())
                .thenReturn(user);

        when(dailyRecord.getRecordDate())
                .thenReturn(REPORT_DATE);

        when(user.getId())
                .thenReturn(1L);
    }
}
