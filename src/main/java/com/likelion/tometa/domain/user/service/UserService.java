package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.dto.request.UserNotificationSettingRequestDto;
import com.likelion.tometa.domain.user.dto.request.UserProfileRequestDto;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionTokenProvider;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final AnonymousSessionTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Transactional
    public void saveProfile(UserProfileRequestDto request, String sessionToken) {
        AnonymousSession session = getValidSession(sessionToken);
        User user = session.getUser();

        String nickname = request.hasNickname()
                ? request.nickname()
                : user.getNickname();
        String gender = request.hasGender()
                ? request.gender()
                : user.getGender();
        String ageGroup = request.hasAgeGroup()
                ? request.ageGroup()
                : user.getAgeGroup();
        String skinType = request.hasSkinType()
                ? request.skinType()
                : user.getSkinType();

        if (user.getProfileCompletedAt() == null
                && isCompleteProfile(nickname, gender, ageGroup, skinType)) {
            user.completeProfile(
                    nickname,
                    gender,
                    ageGroup,
                    skinType
            );
        } else {
            user.updateProfile(
                    nickname,
                    gender,
                    ageGroup,
                    skinType
            );
        }

        session.touch();
    }

    private boolean isCompleteProfile(
            String nickname,
            String gender,
            String ageGroup,
            String skinType
    ) {
        return nickname != null
                && gender != null
                && ageGroup != null
                && skinType != null;
    }

    @Transactional
    public void saveNotificationSettings(UserNotificationSettingRequestDto request, String sessionToken) {
        AnonymousSession session = getValidSession(sessionToken);
        User user = userRepository.findWithLockById(session.getUser().getId())
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));

        if (userNotificationSettingRepository.existsByUser_Id(user.getId())) {
            session.touch();
            return;
        }

        UserNotificationSetting notificationSetting = UserNotificationSetting.builder()
                .user(user)
                .dailyReportEnabled(request.dailyReportEnabled())
                .recordReminderEnabled(request.recordReminderEnabled())
                .recordReminderTime(parseTime(request.recordReminderTime()))
                .weeklyReportEnabled(request.weeklyReportEnabled())
                .weeklyReportTime(parseTime(request.weeklyReportTime()))
                .build();

        userNotificationSettingRepository.save(notificationSetting);
        session.touch();
    }

    private AnonymousSession getValidSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION);
        }

        String tokenHash = tokenProvider.hash(sessionToken);

        return anonymousSessionRepository.findByTokenHash(tokenHash)
                .filter(session -> !session.isExpired(LocalDateTime.now()))
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));
    }

    private LocalTime parseTime(String time) {
        return time == null ? null : LocalTime.parse(time);
    }
}
