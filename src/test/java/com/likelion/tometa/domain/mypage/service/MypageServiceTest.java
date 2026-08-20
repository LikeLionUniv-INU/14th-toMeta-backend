package com.likelion.tometa.domain.mypage.service;

import com.likelion.tometa.domain.health.repository.HealthConnectionRepository;
import com.likelion.tometa.domain.mypage.dto.response.MypageResponseDto;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.PushTokenRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MypageServiceTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;

    @Mock
    private HealthConnectionRepository healthConnectionRepository;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @InjectMocks
    private MypageService mypageService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .nickname("김도영")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
    }

    @Test
    void getMypage_returnsProfileConnectionStatusesAndNotificationSettings() {
        UserNotificationSetting setting = UserNotificationSetting.builder()
                .user(user)
                .dailyReportEnabled(true)
                .recordReminderEnabled(true)
                .recordReminderTime(LocalTime.of(22, 0))
                .weeklyReportEnabled(true)
                .weeklyReportTime(LocalTime.of(7, 5))
                .build();
        when(healthConnectionRepository.existsByUser_IdAndRevokedAtIsNull(1L))
                .thenReturn(true);
        when(pushTokenRepository.existsByUser_Id(1L)).thenReturn(true);
        when(userNotificationSettingRepository.findByUser_Id(1L))
                .thenReturn(Optional.of(setting));

        MypageResponseDto result = mypageService.getMypage(SESSION_TOKEN);

        assertEquals("김도영", result.nickname());
        assertTrue(result.healthConnectLinked());
        assertTrue(result.pushConnected());
        assertTrue(result.notificationSettings().dailyReportEnabled());
        assertTrue(result.notificationSettings().recordReminderEnabled());
        assertEquals("22:00", result.notificationSettings().recordReminderTime());
        assertTrue(result.notificationSettings().weeklyReportEnabled());
        assertEquals("07:05", result.notificationSettings().weeklyReportTime());
        verify(sessionUserResolver).resolve(SESSION_TOKEN);
    }

    @Test
    void getMypage_returnsDefaultObjectWhenNotificationSettingsDoNotExist() {
        when(healthConnectionRepository.existsByUser_IdAndRevokedAtIsNull(1L))
                .thenReturn(false);
        when(pushTokenRepository.existsByUser_Id(1L)).thenReturn(false);
        when(userNotificationSettingRepository.findByUser_Id(1L))
                .thenReturn(Optional.empty());

        MypageResponseDto result = mypageService.getMypage(SESSION_TOKEN);

        assertFalse(result.healthConnectLinked());
        assertFalse(result.pushConnected());
        assertFalse(result.notificationSettings().dailyReportEnabled());
        assertFalse(result.notificationSettings().recordReminderEnabled());
        assertNull(result.notificationSettings().recordReminderTime());
        assertFalse(result.notificationSettings().weeklyReportEnabled());
        assertNull(result.notificationSettings().weeklyReportTime());
    }
}
