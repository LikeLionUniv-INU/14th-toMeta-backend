package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.dto.request.UserProfileRequestDto;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionTokenProvider;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final AnonymousSessionTokenProvider tokenProvider;

    @Transactional
    public void saveProfile(UserProfileRequestDto request, String sessionToken) {
        AnonymousSession session = getValidSession(sessionToken);
        User user = session.getUser();

        if (user.getProfileCompletedAt() == null) {
            user.completeProfile(
                    request.nickname(),
                    request.gender(),
                    request.ageGroup(),
                    request.skinType()
            );
        } else {
            user.updateProfile(
                    request.nickname(),
                    request.gender(),
                    request.ageGroup(),
                    request.skinType()
            );
        }

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
}