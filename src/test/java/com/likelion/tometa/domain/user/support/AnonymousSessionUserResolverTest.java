package com.likelion.tometa.domain.user.support;

import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymousSessionUserResolverTest {

    @Mock
    private AnonymousSessionRepository anonymousSessionRepository;

    @Mock
    private AnonymousSessionTokenProvider tokenProvider;

    @InjectMocks
    private AnonymousSessionUserResolver sessionUserResolver;

    @Test
    void resolve_rejectsMissingToken() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> sessionUserResolver.resolve(null)
        );

        assertSame(UserErrorCode.INVALID_ANONYMOUS_SESSION, exception.getErrorCode());
        verifyNoInteractions(anonymousSessionRepository, tokenProvider);
    }

    @Test
    void resolve_rejectsUnknownToken() {
        when(tokenProvider.hash("invalid-token")).thenReturn("token-hash");
        when(anonymousSessionRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> sessionUserResolver.resolve("invalid-token")
        );

        assertSame(UserErrorCode.INVALID_ANONYMOUS_SESSION, exception.getErrorCode());
    }

    @Test
    void resolve_rejectsExpiredSession() {
        AnonymousSession expiredSession = AnonymousSession.builder()
                .user(User.builder().build())
                .tokenHash("token-hash")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(tokenProvider.hash("expired-token")).thenReturn("token-hash");
        when(anonymousSessionRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(expiredSession));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> sessionUserResolver.resolve("expired-token")
        );

        assertSame(UserErrorCode.INVALID_ANONYMOUS_SESSION, exception.getErrorCode());
    }

    @Test
    void resolve_returnsUserAndTouchesValidSession() {
        User user = User.builder().build();
        AnonymousSession session = AnonymousSession.builder()
                .user(user)
                .tokenHash("token-hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        LocalDateTime lastAccessedAtBeforeResolve = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(
                session,
                "lastAccessedAt",
                lastAccessedAtBeforeResolve
        );
        when(tokenProvider.hash("valid-token")).thenReturn("token-hash");
        when(anonymousSessionRepository.findByTokenHash("token-hash"))
                .thenReturn(Optional.of(session));

        User resolvedUser = sessionUserResolver.resolve("valid-token");

        assertSame(user, resolvedUser);
        assertTrue(session.getLastAccessedAt().isAfter(lastAccessedAtBeforeResolve));
    }
}
