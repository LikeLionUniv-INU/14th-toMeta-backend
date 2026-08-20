package com.likelion.tometa.domain.mypage.service;

import com.likelion.tometa.domain.health.repository.HealthConnectionRepository;
import com.likelion.tometa.domain.mypage.dto.response.MypageResponseDto;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.PushTokenRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MypageService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final HealthConnectionRepository healthConnectionRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;

    @Transactional
    public MypageResponseDto getMypage(String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);
        Long userId = user.getId();

        boolean healthConnectLinked = healthConnectionRepository
                .existsByUser_IdAndRevokedAtIsNull(userId);
        boolean pushConnected = pushTokenRepository.existsByUser_Id(userId);
        MypageResponseDto.NotificationSettings notificationSettings =
                userNotificationSettingRepository.findByUser_Id(userId)
                        .map(this::toNotificationSettings)
                        .orElseGet(MypageResponseDto.NotificationSettings::defaults);

        return new MypageResponseDto(
                user.getNickname(),
                healthConnectLinked,
                pushConnected,
                notificationSettings
        );
    }

    private MypageResponseDto.NotificationSettings toNotificationSettings(
            UserNotificationSetting setting
    ) {
        return new MypageResponseDto.NotificationSettings(
                setting.isDailyReportEnabled(),
                setting.isRecordReminderEnabled(),
                formatTime(setting.getRecordReminderTime()),
                setting.isWeeklyReportEnabled(),
                formatTime(setting.getWeeklyReportTime())
        );
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }
}
