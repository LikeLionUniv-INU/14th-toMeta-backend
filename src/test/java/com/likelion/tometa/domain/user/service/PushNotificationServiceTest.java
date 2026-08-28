package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private UserNotificationSettingRepository settingRepository;
    @Mock
    private FcmPushService fcmPushService;
    @InjectMocks
    private PushNotificationService service;

    @Test
    void dailyNotification_isSentOnlyWhenEnabled() {
        LocalDate reportDate = LocalDate.of(2026, 8, 23);
        UserNotificationSetting setting = UserNotificationSetting.builder()
                .dailyReportEnabled(true)
                .build();
        when(settingRepository.findByUser_Id(1L))
                .thenReturn(Optional.of(setting));
        when(fcmPushService.sendToUser(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Map.of(
                        "type", "DAILY_REPORT",
                        "date", reportDate.toString()
                ))
        )).thenReturn(1);

        assertEquals(1, service.sendDailyReportNotification(1L, reportDate));
    }

    @Test
    void weeklyNotification_isSkippedWhenDisabled() {
        LocalDate weekStartDate = LocalDate.of(2026, 8, 17);
        UserNotificationSetting setting = UserNotificationSetting.builder()
                .weeklyReportEnabled(false)
                .build();
        when(settingRepository.findByUser_Id(1L))
                .thenReturn(Optional.of(setting));

        assertEquals(
                0,
                service.sendWeeklyReportNotification(1L, weekStartDate)
        );
        verify(fcmPushService, never()).sendToUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap()
        );
    }

    @Test
    void recordReminder_startsDeliveryAfterSettingLookup() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        Runnable deliveryStarting = mock(Runnable.class);
        UserNotificationSetting setting = UserNotificationSetting.builder()
                .recordReminderEnabled(true)
                .build();
        when(settingRepository.findByUser_Id(1L))
                .thenReturn(Optional.of(setting));
        when(fcmPushService.sendToUser(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Map.of(
                        "type", "RECORD_REMINDER",
                        "date", date.toString()
                )),
                org.mockito.ArgumentMatchers.eq(deliveryStarting)
        )).thenReturn(1);

        assertEquals(
                1,
                service.sendRecordReminder(1L, date, deliveryStarting)
        );
    }

    @Test
    void recordReminder_disabledSettingCompletesWithoutFcmDelivery() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        Runnable deliveryStarting = mock(Runnable.class);
        UserNotificationSetting setting = UserNotificationSetting.builder()
                .recordReminderEnabled(false)
                .build();
        when(settingRepository.findByUser_Id(1L))
                .thenReturn(Optional.of(setting));

        assertEquals(
                0,
                service.sendRecordReminder(1L, date, deliveryStarting)
        );

        verify(deliveryStarting).run();
        verify(fcmPushService, never()).sendToUser(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.any(Runnable.class)
        );
    }
}
