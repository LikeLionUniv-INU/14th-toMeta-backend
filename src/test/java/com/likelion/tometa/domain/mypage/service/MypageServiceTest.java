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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
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

    @Mock
    private UserRepository userRepository;

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

    @Test
    void getUserProfile_returnsCurrentProfile() {
        User profileUser = User.builder()
                .nickname("김도영")
                .gender("male")
                .ageGroup("20s")
                .skinType("dry")
                .build();
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(profileUser);

        UserProfileResponseDto result = mypageService.getUserProfile(SESSION_TOKEN);

        assertEquals("김도영", result.nickname());
        assertEquals("male", result.gender());
        assertEquals("20s", result.ageGroup());
        assertEquals("dry", result.skinType());
    }

    @Test
    void getUserProfile_allowsNullFieldsBeforeProfileRegistration() {
        User profileUser = User.builder().build();
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(profileUser);

        UserProfileResponseDto result = mypageService.getUserProfile(SESSION_TOKEN);

        assertNull(result.nickname());
        assertNull(result.gender());
        assertNull(result.ageGroup());
        assertNull(result.skinType());
    }

    @Test
    void updateNotificationSettings_updatesOnlyRequestedFields() {
        UserNotificationSetting setting = createEnabledSetting();
        mockUpdateContext(Optional.of(setting));
        NotificationSettingsUpdateRequestDto request =
                new NotificationSettingsUpdateRequestDto(
                        false,
                        null,
                        null,
                        null,
                        "07:05"
                );

        mypageService.updateNotificationSettings(request, SESSION_TOKEN);

        assertFalse(setting.isDailyReportEnabled());
        assertTrue(setting.isRecordReminderEnabled());
        assertEquals(LocalTime.of(22, 0), setting.getRecordReminderTime());
        assertTrue(setting.isWeeklyReportEnabled());
        assertEquals(LocalTime.of(7, 5), setting.getWeeklyReportTime());
    }

    @Test
    void updateNotificationSettings_clearsTimesWhenNotificationsAreDisabled() {
        UserNotificationSetting setting = createEnabledSetting();
        mockUpdateContext(Optional.of(setting));
        NotificationSettingsUpdateRequestDto request =
                new NotificationSettingsUpdateRequestDto(
                        null,
                        false,
                        "invalid",
                        false,
                        "24:00"
                );

        mypageService.updateNotificationSettings(request, SESSION_TOKEN);

        assertFalse(setting.isRecordReminderEnabled());
        assertNull(setting.getRecordReminderTime());
        assertFalse(setting.isWeeklyReportEnabled());
        assertNull(setting.getWeeklyReportTime());
    }

    @Test
    void updateNotificationSettings_createsSettingsWhenTheyDoNotExist() {
        mockUpdateContext(Optional.empty());
        NotificationSettingsUpdateRequestDto request =
                new NotificationSettingsUpdateRequestDto(
                        true,
                        true,
                        "09:30",
                        null,
                        null
                );

        mypageService.updateNotificationSettings(request, SESSION_TOKEN);

        ArgumentCaptor<UserNotificationSetting> captor =
                ArgumentCaptor.forClass(UserNotificationSetting.class);
        verify(userNotificationSettingRepository).save(captor.capture());
        UserNotificationSetting savedSetting = captor.getValue();
        assertSame(user, savedSetting.getUser());
        assertTrue(savedSetting.isDailyReportEnabled());
        assertTrue(savedSetting.isRecordReminderEnabled());
        assertEquals(LocalTime.of(9, 30), savedSetting.getRecordReminderTime());
        assertFalse(savedSetting.isWeeklyReportEnabled());
        assertNull(savedSetting.getWeeklyReportTime());
    }

    @Test
    void updateNotificationSettings_rejectsInvalidNotificationTime() {
        mockUpdateContext(Optional.empty());
        NotificationSettingsUpdateRequestDto request =
                new NotificationSettingsUpdateRequestDto(
                        null,
                        true,
                        "24:00",
                        null,
                        null
                );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> mypageService.updateNotificationSettings(request, SESSION_TOKEN)
        );

        assertSame(UserErrorCode.INVALID_NOTIFICATION_TIME, exception.getErrorCode());
        verify(userNotificationSettingRepository, never()).save(
                org.mockito.ArgumentMatchers.any(UserNotificationSetting.class)
        );
    }

    @Test
    void updateNotificationSettings_requiresTimeWhenEnablingWithoutExistingTime() {
        mockUpdateContext(Optional.empty());
        NotificationSettingsUpdateRequestDto request =
                new NotificationSettingsUpdateRequestDto(
                        null,
                        null,
                        null,
                        true,
                        null
                );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> mypageService.updateNotificationSettings(request, SESSION_TOKEN)
        );

        assertSame(UserErrorCode.INVALID_NOTIFICATION_TIME, exception.getErrorCode());
    }

    private UserNotificationSetting createEnabledSetting() {
        return UserNotificationSetting.builder()
                .user(user)
                .dailyReportEnabled(true)
                .recordReminderEnabled(true)
                .recordReminderTime(LocalTime.of(22, 0))
                .weeklyReportEnabled(true)
                .weeklyReportTime(LocalTime.of(21, 0))
                .build();
    }

    private void mockUpdateContext(
            Optional<UserNotificationSetting> notificationSetting
    ) {
        when(userRepository.findWithLockById(1L)).thenReturn(Optional.of(user));
        when(userNotificationSettingRepository.findByUser_Id(1L))
                .thenReturn(notificationSetting);
    }
}
