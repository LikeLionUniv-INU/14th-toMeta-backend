package com.likelion.tometa.domain.health.service;

import com.likelion.tometa.domain.health.code.HealthErrorCode;
import com.likelion.tometa.domain.health.dto.request.DailyHealthSummaryRequestDto;
import com.likelion.tometa.domain.health.dto.request.HealthConnectionRequestDto;
import com.likelion.tometa.domain.health.dto.request.HealthRawRecordRequestDto;
import com.likelion.tometa.domain.health.dto.request.HealthSyncRequestDto;
import com.likelion.tometa.domain.health.dto.response.HealthConnectStatusResponseDto;
import com.likelion.tometa.domain.health.dto.response.HealthConnectionResponseDto;
import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.health.entity.HealthConnection;
import com.likelion.tometa.domain.health.entity.HealthRawRecord;
import com.likelion.tometa.domain.health.repository.DailyHealthSummaryRepository;
import com.likelion.tometa.domain.health.repository.HealthConnectionRepository;
import com.likelion.tometa.domain.health.repository.HealthRawRecordRepository;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HealthConnectService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final HealthConnectionRepository healthConnectionRepository;
    private final HealthRawRecordRepository healthRawRecordRepository;
    private final DailyHealthSummaryRepository dailyHealthSummaryRepository;
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

    @Transactional
    public void sync(HealthSyncRequestDto request, String authorizationHeader) {
        HealthConnection connection = getValidHealthConnection(authorizationHeader);
        User user = userRepository.findWithLockById(connection.getUser().getId())
                .orElseThrow(() -> new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));

        for (HealthRawRecordRequestDto record : request.records()) {
            Optional<HealthRawRecord> existingRecord =
                    healthRawRecordRepository.findByHealthConnection_IdAndHcRecordId(
                            connection.getId(),
                            record.hcRecordId()
                    );

            if (existingRecord.isPresent()) {
                existingRecord.get().updatePayload(
                        toUtcLocalDateTime(record.startTime()),
                        toUtcLocalDateTime(record.endTime()),
                        record.payload().toString()
                );
                continue;
            }

            HealthRawRecord healthRawRecord = HealthRawRecord.builder()
                    .healthConnection(connection)
                    .hcRecordId(record.hcRecordId())
                    .recordType(record.recordType())
                    .startTime(toUtcLocalDateTime(record.startTime()))
                    .endTime(toUtcLocalDateTime(record.endTime()))
                    .payload(record.payload().toString())
                    .build();

            healthRawRecordRepository.save(healthRawRecord);
        }

        for (DailyHealthSummaryRequestDto healthSummary : request.dailyHealthSummaries()) {
            saveDailyHealthSummary(user, healthSummary);
        }

        connection.markSynced();
    }

    private void saveDailyHealthSummary(
            User user,
            DailyHealthSummaryRequestDto request
    ) {
        DailyHealthSummary summary = dailyHealthSummaryRepository
                .findByUser_IdAndSummaryDate(user.getId(), request.date())
                .orElseGet(() -> dailyHealthSummaryRepository.save(
                        DailyHealthSummary.builder()
                                .user(user)
                                .summaryDate(request.date())
                                .build()
                ));

        summary.updateReportMetrics(
                request.sleepMinutes(),
                request.skinTemperatureCelsius(),
                request.exerciseMinutes(),
                request.totalCaloriesBurned(),
                request.menstrualCycleDay(),
                request.avgSpo2()
        );
    }

    private LocalDateTime toUtcLocalDateTime(Instant instant) {
        return instant == null
                ? null
                : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private HealthConnection getValidHealthConnection(String authorizationHeader) {
        String deviceToken = extractBearerToken(authorizationHeader);
        String tokenHash = healthDeviceTokenProvider.hash(deviceToken);

        return healthConnectionRepository.findByDeviceTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(() -> new GeneralException(HealthErrorCode.INVALID_HEALTH_DEVICE_TOKEN));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new GeneralException(HealthErrorCode.INVALID_HEALTH_DEVICE_TOKEN);
        }

        String token = authorizationHeader.substring(7).trim();

        if (token.isBlank()) {
            throw new GeneralException(HealthErrorCode.INVALID_HEALTH_DEVICE_TOKEN);
        }

        return token;
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