package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.dto.request.UserProfileRequestDto;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String SESSION_TOKEN = "session-token";
    private static final String TOKEN_HASH = "token-hash";

    @Mock
    private AnonymousSessionRepository anonymousSessionRepository;

    @Mock
    private UserNotificationSettingRepository userNotificationSettingRepository;

    @Mock
    private AnonymousSessionTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(tokenProvider.hash(SESSION_TOKEN)).thenReturn(TOKEN_HASH);
    }

    @Test
    void saveProfile_updatesOnlyProvidedFields() {
        User user = User.builder().build();
        user.completeProfile("김도영", "male", "20s", "dry");
        AnonymousSession session = validSession(user);
        mockSession(session);
        UserProfileRequestDto request = new UserProfileRequestDto();
        request.setNickname("새닉네임");
        request.setSkinType("combination_dry");

        userService.saveProfile(request, SESSION_TOKEN);

        assertEquals("새닉네임", user.getNickname());
        assertEquals("male", user.getGender());
        assertEquals("20s", user.getAgeGroup());
        assertEquals("combination_dry", user.getSkinType());
        assertNotNull(session.getLastAccessedAt());
    }

    @Test
    void saveProfile_keepsIncompleteProfileIncompleteUntilAllFieldsExist() {
        User user = User.builder()
                .nickname("김도영")
                .build();
        AnonymousSession session = validSession(user);
        mockSession(session);
        UserProfileRequestDto request = new UserProfileRequestDto();
        request.setGender("male");

        userService.saveProfile(request, SESSION_TOKEN);

        assertEquals("김도영", user.getNickname());
        assertEquals("male", user.getGender());
        assertNull(user.getAgeGroup());
        assertNull(user.getSkinType());
        assertNull(user.getProfileCompletedAt());
    }

    @Test
    void saveProfile_completesProfileWhenMergedValuesAreComplete() {
        User user = User.builder()
                .nickname("김도영")
                .gender("male")
                .ageGroup("20s")
                .build();
        AnonymousSession session = validSession(user);
        mockSession(session);
        UserProfileRequestDto request = new UserProfileRequestDto();
        request.setSkinType("sensitive");

        userService.saveProfile(request, SESSION_TOKEN);

        assertEquals("김도영", user.getNickname());
        assertEquals("male", user.getGender());
        assertEquals("20s", user.getAgeGroup());
        assertEquals("sensitive", user.getSkinType());
        assertNotNull(user.getProfileCompletedAt());
    }

    private AnonymousSession validSession(User user) {
        return AnonymousSession.builder()
                .user(user)
                .tokenHash(TOKEN_HASH)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    private void mockSession(AnonymousSession session) {
        when(anonymousSessionRepository.findByTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(session));
    }
}
