package com.likelion.tometa.domain.user.support;

import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AnonymousSessionUserResolver {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final AnonymousSessionTokenProvider tokenProvider;

    public User resolve(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION);
        }

        String tokenHash = tokenProvider.hash(sessionToken);

        AnonymousSession session = anonymousSessionRepository.findByTokenHash(tokenHash)
                .filter(foundSession -> !foundSession.isExpired(LocalDateTime.now()))
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));

        session.touch();
        return session.getUser();
    }
}
