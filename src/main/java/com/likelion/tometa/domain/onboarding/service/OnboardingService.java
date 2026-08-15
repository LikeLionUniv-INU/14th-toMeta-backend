package com.likelion.tometa.domain.onboarding.service;

import com.likelion.tometa.domain.health.repository.HealthConnectionRepository;
import com.likelion.tometa.domain.onboarding.code.OnboardingErrorCode;
import com.likelion.tometa.domain.onboarding.constant.ConsentPolicy;
import com.likelion.tometa.domain.onboarding.dto.request.ConsentRequestDto;
import com.likelion.tometa.domain.onboarding.dto.response.OnboardingStatusResponseDto;
import com.likelion.tometa.domain.onboarding.service.result.ConsentResult;
import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserConsent;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.domain.user.repository.UserConsentRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionTokenProvider;
import com.likelion.tometa.global.config.AnonymousSessionProperties;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final AnonymousSessionRepository anonymousSessionRepository;
    private final HealthConnectionRepository healthConnectionRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final AnonymousSessionTokenProvider tokenProvider;
    private final AnonymousSessionProperties sessionProperties;

    @Transactional
    public ConsentResult agreeToConsents(ConsentRequestDto request, String sessionToken) {
        validateRequiredConsents(request);

        Optional<AnonymousSession> existingSession = findValidSession(sessionToken);

        if (existingSession.isPresent()) {
            AnonymousSession session = existingSession.get();

            saveMissingConsents(session.getUser());
            session.touch();

            return ConsentResult.existingSession();
        }

        return createNewAnonymousUser();
    }

    @Transactional
    public OnboardingStatusResponseDto getOnboardingStatus(String sessionToken) {
        AnonymousSession session = findValidSession(sessionToken)
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));

        User user = session.getUser();

        boolean profileCompleted = user.getProfileCompletedAt() != null;

        boolean healthConnectLinked = healthConnectionRepository
                .existsByUser_IdAndRevokedAtIsNull(user.getId());

        boolean notificationSettingsCompleted = userNotificationSettingRepository
                .existsByUser_Id(user.getId());

        session.touch();

        return new OnboardingStatusResponseDto(
                profileCompleted,
                healthConnectLinked,
                notificationSettingsCompleted
        );
    }

    private void validateRequiredConsents(ConsentRequestDto request) {
        if (!Boolean.TRUE.equals(request.termsAgreed()) ||
                !Boolean.TRUE.equals(request.privacyAgreed())) {
            throw new GeneralException(OnboardingErrorCode.REQUIRED_CONSENT_NOT_AGREED);
        }
    }

    private Optional<AnonymousSession> findValidSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = tokenProvider.hash(sessionToken);

        return anonymousSessionRepository.findByTokenHash(tokenHash)
                .filter(session -> !session.isExpired(LocalDateTime.now()));
    }

    private ConsentResult createNewAnonymousUser() {
        User savedUser = userRepository.save(User.builder().build());

        saveMissingConsents(savedUser);

        String sessionToken = tokenProvider.generateToken();
        String tokenHash = tokenProvider.hash(sessionToken);
        LocalDateTime expiresAt =
                LocalDateTime.now().plusDays(sessionProperties.expirationDays());

        AnonymousSession anonymousSession = AnonymousSession.builder()
                .user(savedUser)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        anonymousSessionRepository.save(anonymousSession);

        return ConsentResult.newSession(sessionToken);
    }

    private void saveMissingConsents(User user) {
        saveConsentIfAbsent(user, ConsentPolicy.TERMS_TYPE, ConsentPolicy.TERMS_VERSION);
        saveConsentIfAbsent(user, ConsentPolicy.PRIVACY_TYPE, ConsentPolicy.PRIVACY_VERSION);
    }

    private void saveConsentIfAbsent(User user, String consentType, String version) {
        boolean alreadyAgreed =
                userConsentRepository.existsByUserIdAndConsentTypeAndVersion(
                        user.getId(), consentType, version);

        if (alreadyAgreed) {
            return;
        }

        UserConsent consent = UserConsent.builder()
                .user(user)
                .consentType(consentType)
                .version(version)
                .agreed(true)
                .build();

        userConsentRepository.save(consent);
    }
}