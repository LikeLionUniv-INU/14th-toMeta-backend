package com.likelion.tometa.domain.user.scheduler;

import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.user.repository.RecordReminderDeliveryRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.service.RecordReminderNotificationService;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordReminderSchedulerTest {

    private static final Instant MONDAY_15_40_KST =
            Instant.parse("2026-08-24T06:40:00Z");
    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 8, 24);
    private static final LocalTime REMINDER_TIME = LocalTime.of(15, 40);
    private static final LocalDateTime NOW =
            LocalDateTime.of(RECORD_DATE, REMINDER_TIME);

    @Mock
    private UserNotificationSettingRepository settingRepository;
    @Mock
    private DailyRecordRepository dailyRecordRepository;
    @Mock
    private RecordReminderDeliveryRepository deliveryRepository;
    @Mock
    private RecordReminderNotificationService notificationService;

    private RecordReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RecordReminderScheduler(
                settingRepository,
                dailyRecordRepository,
                deliveryRepository,
                notificationService,
                Clock.fixed(
                        MONDAY_15_40_KST,
                        ZoneId.of("Asia/Seoul")
                )
        );
    }

    @Test
    void scheduler_sendsDueReminder() {
        when(settingRepository.findRecordReminderTargetUserIds(
                RECORD_DATE,
                REMINDER_TIME,
                NOW.minusMinutes(5)
        )).thenReturn(List.of(1L));
        when(notificationService.send(1L, RECORD_DATE, NOW))
                .thenReturn(new RecordReminderNotificationService.NotificationResult(
                        true,
                        1
                ));

        scheduler.sendRecordReminders();

        verify(notificationService).send(1L, RECORD_DATE, NOW);
    }

    @Test
    void scheduler_skipsUserWhoWritesRecordAfterTargetQuery() {
        when(settingRepository.findRecordReminderTargetUserIds(
                RECORD_DATE,
                REMINDER_TIME,
                NOW.minusMinutes(5)
        )).thenReturn(List.of(1L));
        when(dailyRecordRepository.existsByUser_IdAndRecordDate(
                1L,
                RECORD_DATE
        )).thenReturn(true);

        scheduler.sendRecordReminders();

        verify(notificationService, never()).send(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void scheduler_continuesWithNextUserAfterNotificationFailure() {
        when(settingRepository.findRecordReminderTargetUserIds(
                RECORD_DATE,
                REMINDER_TIME,
                NOW.minusMinutes(5)
        )).thenReturn(List.of(1L, 2L));
        when(notificationService.send(1L, RECORD_DATE, NOW))
                .thenThrow(new RuntimeException("notification failed"));
        when(notificationService.send(2L, RECORD_DATE, NOW))
                .thenReturn(new RecordReminderNotificationService.NotificationResult(
                        true,
                        1
                ));

        assertDoesNotThrow(scheduler::sendRecordReminders);

        verify(notificationService).send(1L, RECORD_DATE, NOW);
        verify(notificationService).send(2L, RECORD_DATE, NOW);
    }

    @Test
    void recovery_marksStaleSendingDeliveriesUnknown() {
        when(deliveryRepository.markStaleDeliveriesUnknown(
                NOW.minusMinutes(5)
        )).thenReturn(1);

        scheduler.recoverStaleRecordReminderDeliveries();

        verify(deliveryRepository).markStaleDeliveriesUnknown(
                NOW.minusMinutes(5)
        );
    }

    @Test
    void recovery_doesNotThrowWhenRepositoryFails() {
        when(deliveryRepository.markStaleDeliveriesUnknown(
                NOW.minusMinutes(5)
        )).thenThrow(new RuntimeException("database unavailable"));

        assertDoesNotThrow(
                scheduler::recoverStaleRecordReminderDeliveries
        );
    }
}
