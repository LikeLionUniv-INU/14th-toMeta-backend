package com.likelion.tometa.domain.mypage.service;

import com.likelion.tometa.domain.health.repository.HealthConnectionRepository;
import com.likelion.tometa.domain.mypage.dto.request.NotificationSettingsUpdateRequestDto;
import com.likelion.tometa.domain.mypage.dto.response.MypageResponseDto;
import com.likelion.tometa.domain.mypage.dto.response.UserProfileResponseDto;
import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.PushTokenRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MypageService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final HealthConnectionRepository healthConnectionRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final UserRepository userRepository;

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

    @Transactional
    public UserProfileResponseDto getUserProfile(String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);

        return new UserProfileResponseDto(
                user.getNickname(),
                user.getGender(),
                user.getAgeGroup(),
                user.getSkinType()
        );
    }

    @Transactional
    public void updateNotificationSettings(
            NotificationSettingsUpdateRequestDto request,
            String sessionToken
    ) {
        User resolvedUser = sessionUserResolver.resolve(sessionToken);
        User user = userRepository.findWithLockById(resolvedUser.getId())
                .orElseThrow(() -> new GeneralException(
                        UserErrorCode.INVALID_ANONYMOUS_SESSION
                ));
        Optional<UserNotificationSetting> existingSetting =
                userNotificationSettingRepository.findByUser_Id(user.getId());

        boolean dailyReportEnabled = request.dailyReportEnabled() != null
                ? request.dailyReportEnabled()
                : existingSetting
                        .map(UserNotificationSetting::isDailyReportEnabled)
                        .orElse(false);
        boolean recordReminderEnabled = request.recordReminderEnabled() != null
                ? request.recordReminderEnabled()
                : existingSetting
                        .map(UserNotificationSetting::isRecordReminderEnabled)
                        .orElse(false);
        boolean weeklyReportEnabled = request.weeklyReportEnabled() != null
                ? request.weeklyReportEnabled()
                : existingSetting
                        .map(UserNotificationSetting::isWeeklyReportEnabled)
                        .orElse(false);
        LocalTime recordReminderTime = resolveTime(
                recordReminderEnabled,
                request.recordReminderTime(),
                existingSetting
                        .map(UserNotificationSetting::getRecordReminderTime)
                        .orElse(null)
        );
        LocalTime weeklyReportTime = resolveTime(
                weeklyReportEnabled,
                request.weeklyReportTime(),
                existingSetting
                        .map(UserNotificationSetting::getWeeklyReportTime)
                        .orElse(null)
        );

        if (existingSetting.isPresent()) {
            existingSetting.get().update(
                    dailyReportEnabled,
                    recordReminderEnabled,
                    recordReminderTime,
                    weeklyReportEnabled,
                    weeklyReportTime
            );
            return;
        }

        UserNotificationSetting setting = UserNotificationSetting.builder()
                .user(user)
                .dailyReportEnabled(dailyReportEnabled)
                .recordReminderEnabled(recordReminderEnabled)
                .recordReminderTime(recordReminderTime)
                .weeklyReportEnabled(weeklyReportEnabled)
                .weeklyReportTime(weeklyReportTime)
                .build();
        userNotificationSettingRepository.save(setting);
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

    private LocalTime resolveTime(
            boolean enabled,
            String requestedTime,
            LocalTime existingTime
    ) {
        if (!enabled) {
            return null;
        }
        if (requestedTime == null) {
            if (existingTime == null) {
                throw new GeneralException(UserErrorCode.INVALID_NOTIFICATION_TIME);
            }
            return existingTime;
        }
        if (!TIME_PATTERN.matcher(requestedTime).matches()) {
            throw new GeneralException(UserErrorCode.INVALID_NOTIFICATION_TIME);
        }

        try {
            return LocalTime.parse(requestedTime, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new GeneralException(UserErrorCode.INVALID_NOTIFICATION_TIME);
        }
    }
}
