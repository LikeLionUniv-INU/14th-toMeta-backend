package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final FcmPushService fcmPushService;

    public int sendDailyReportNotification(
            Long userId,
            LocalDate reportDate
    ) {
        return userNotificationSettingRepository
                .findByUser_Id(userId)
                .filter(
                        UserNotificationSetting::isDailyReportEnabled
                )
                .map(setting ->
                        fcmPushService.sendToUser(
                                userId,
                                "오늘의 피부 리포트가 도착했어요",
                                "어제의 기록과 건강 데이터를 바탕으로 만든 분석을 확인해보세요.",
                                Map.of(
                                        "type",
                                        "DAILY_REPORT",
                                        "date",
                                        reportDate.toString()
                                )
                        )
                )
                .orElse(0);
    }

    public int sendRecordReminder(
            Long userId,
            LocalDate date
    ) {
        return sendRecordReminder(userId, date, () -> {
        });
    }

    int sendRecordReminder(
            Long userId,
            LocalDate date,
            Runnable deliveryStarting
    ) {
        Optional<UserNotificationSetting> setting = userNotificationSettingRepository
                .findByUser_Id(userId)
                .filter(UserNotificationSetting::isRecordReminderEnabled);
        if (setting.isEmpty()) {
            deliveryStarting.run();
            return 0;
        }

        return fcmPushService.sendToUser(
                userId,
                "오늘의 피부 기록을 남겨주세요",
                "오늘의 피부 상태와 생활 기록을 남겨보세요.",
                Map.of(
                        "type",
                        "RECORD_REMINDER",
                        "date",
                        date.toString()
                ),
                deliveryStarting
        );
    }

    public int sendWeeklyReportNotification(
            Long userId,
            LocalDate weekStartDate
    ) {
        return userNotificationSettingRepository
                .findByUser_Id(userId)
                .filter(
                        UserNotificationSetting::isWeeklyReportEnabled
                )
                .map(setting ->
                        fcmPushService.sendToUser(
                                userId,
                                "이번 주 피부 리포트가 도착했어요",
                                "지난 한 주의 피부와 생활 패턴을 확인해보세요.",
                                Map.of(
                                        "type",
                                        "WEEKLY_REPORT",
                                        "startDate",
                                        weekStartDate.toString()
                                )
                        )
                )
                .orElse(0);
    }
}
