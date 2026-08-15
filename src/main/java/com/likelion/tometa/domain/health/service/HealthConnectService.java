package com.likelion.tometa.domain.health.service;

import com.likelion.tometa.domain.health.dto.request.HealthConnectionRequestDto;
import com.likelion.tometa.domain.health.dto.response.HealthConnectStatusResponseDto;
import com.likelion.tometa.domain.health.dto.response.HealthConnectionResponseDto;
import com.likelion.tometa.domain.health.entity.HealthConnection;
import com.likelion.tometa.domain.health.repository.HealthConnectionRepository;
import com.likelion.tometa.domain.health.support.HealthDeviceTokenProvider;
import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.entity.AnonymousSession;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.AnonymousSessionRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionTokenProvider;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HealthConnectService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final HealthConnectionRepository healthConnectionRepository;
    private final UserRepository userRepository;
    private final AnonymousSessionTokenProvider anonymousSessionTokenProvider;
    private final HealthDeviceTokenProvider healthDeviceTokenProvider;

    @Transactional
    public HealthConnectionResponseDto connect(HealthConnectionRequestDto request, String sessionToken) {
        AnonymousSession session = getValidSession(sessionToken);
        User user = userRepository.findWithLockById(session.getUser().getId())
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));

        String deviceToken = healthDeviceTokenProvider.generateToken();
        String deviceTokenHash = healthDeviceTokenProvider.hash(deviceToken);

        Optional<HealthConnection> existingConnection =
                healthConnectionRepository.findByUser_IdAndDeviceId(user.getId(), request.deviceId());

        if (existingConnection.isPresent()) {
            existingConnection.get().reconnect(deviceTokenHash);
        } else {
            HealthConnection connection = HealthConnection.builder()
                    .user(user)
                    .deviceId(request.deviceId())
                    .deviceTokenHash(deviceTokenHash)
                    .build();

            healthConnectionRepository.save(connection);
        }

        session.touch();

        return new HealthConnectionResponseDto(deviceToken);
    }

    @Transactional
    public HealthConnectStatusResponseDto getStatus(String sessionToken) {
        AnonymousSession session = getValidSession(sessionToken);
        User user = session.getUser();

        Optional<HealthConnection> connection =
                healthConnectionRepository.findTopByUser_IdAndRevokedAtIsNullOrderByLastSyncedAtDesc(user.getId());

        boolean connected = connection.isPresent();
        LocalDateTime lastSyncedAt = connection
                .map(HealthConnection::getLastSyncedAt)
                .orElse(null);

        session.touch();

        return new HealthConnectStatusResponseDto(connected, lastSyncedAt);
    }

    private AnonymousSession getValidSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION);
        }

        String tokenHash = anonymousSessionTokenProvider.hash(sessionToken);

        return anonymousSessionRepository.findByTokenHash(tokenHash)
                .filter(session -> !session.isExpired(LocalDateTime.now()))
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));
    }
}