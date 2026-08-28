package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.repository.RecordReminderDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordReminderNotificationServiceTest {

    private static final LocalDate REMINDER_DATE = LocalDate.of(2026, 8, 24);
    private static final LocalDateTime REQUESTED_AT =
            LocalDateTime.of(2026, 8, 24, 15, 40);

    @Mock
    private RecordReminderDeliveryRepository deliveryRepository;
    @Mock
    private RecordReminderDeliveryInitializer deliveryInitializer;
    @Mock
    private PushNotificationService pushNotificationService;

    private RecordReminderNotificationService service;

    @BeforeEach
    void setUp() {
        ZoneId koreaZone = ZoneId.of("Asia/Seoul");
        service = new RecordReminderNotificationService(
                deliveryRepository,
                deliveryInitializer,
                pushNotificationService,
                Clock.fixed(
                        REQUESTED_AT.atZone(koreaZone).toInstant(),
                        koreaZone
                )
        );
    }

    @Test
    void send_claimsAndMarksReminderSent() {
        whenClaimSucceeds();
        when(deliveryRepository.beginDelivery(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);
        whenPushDeliverySucceeds(2);
        when(deliveryRepository.markSent(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);

        RecordReminderNotificationService.NotificationResult result =
                service.send(1L, REMINDER_DATE, REQUESTED_AT);

        assertTrue(result.processed());
        assertEquals(2, result.successCount());
        verify(deliveryInitializer).initialize(1L, REMINDER_DATE);
        verify(deliveryRepository).markSent(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT)
        );
    }

    @Test
    void send_skipsWhenReminderIsAlreadyClaimedOrCompleted() {
        when(deliveryRepository.claim(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(0);

        RecordReminderNotificationService.NotificationResult result =
                service.send(1L, REMINDER_DATE, REQUESTED_AT);

        assertFalse(result.processed());
        verifyNoInteractions(pushNotificationService);
    }

    @Test
    void send_marksOutcomeUnknownAndDoesNotResendAfterDeliveryStarted() {
        when(deliveryRepository.claim(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1, 0);
        when(deliveryRepository.beginDelivery(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT)
        )).thenReturn(1);
        whenPushDeliverySucceeds(1);
        when(deliveryRepository.markSent(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT)
        )).thenThrow(new IllegalStateException("database unavailable"));
        when(deliveryRepository.markUnknown(
                eq(1L),
                eq(REMINDER_DATE),
                anyString()
        )).thenReturn(1);

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REMINDER_DATE, REQUESTED_AT)
        );
        RecordReminderNotificationService.NotificationResult retry =
                service.send(1L, REMINDER_DATE, REQUESTED_AT);

        assertFalse(retry.processed());
        verify(pushNotificationService, times(1))
                .sendRecordReminder(
                        eq(1L),
                        eq(REMINDER_DATE),
                        any(Runnable.class)
                );
        verify(deliveryRepository, never()).resetClaim(
                eq(1L),
                eq(REMINDER_DATE),
                anyString()
        );
        verify(deliveryRepository).markUnknown(
                eq(1L),
                eq(REMINDER_DATE),
                anyString()
        );
    }

    @Test
    void send_resetsClaimWhenFailureOccursBeforeDeliveryStarts() {
        whenClaimSucceeds();
        when(pushNotificationService.sendRecordReminder(
                eq(1L),
                eq(REMINDER_DATE),
                any(Runnable.class)
        )).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service.send(1L, REMINDER_DATE, REQUESTED_AT)
        );

        verify(deliveryRepository).resetClaim(
                eq(1L),
                eq(REMINDER_DATE),
                anyString()
        );
        verify(deliveryRepository, never()).beginDelivery(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT)
        );
    }

    private void whenClaimSucceeds() {
        when(deliveryRepository.claim(
                eq(1L),
                eq(REMINDER_DATE),
                anyString(),
                eq(REQUESTED_AT),
                eq(REQUESTED_AT.minusMinutes(5))
        )).thenReturn(1);
    }

    private void whenPushDeliverySucceeds(int successCount) {
        when(pushNotificationService.sendRecordReminder(
                eq(1L),
                eq(REMINDER_DATE),
                any(Runnable.class)
        )).thenAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return successCount;
        });
    }
}
