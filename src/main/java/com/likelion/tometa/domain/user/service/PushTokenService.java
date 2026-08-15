package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.code.PushErrorCode;
import com.likelion.tometa.domain.user.dto.request.PushTokenRegisterRequestDto;
import com.likelion.tometa.domain.user.dto.response.PushTokenRegisterResponseDto;
import com.likelion.tometa.domain.user.entity.PushToken;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.PushTokenRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final PushTokenRepository pushTokenRepository;

    @Transactional
    public PushTokenRegisterResponseDto register(PushTokenRegisterRequestDto request, String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);

        Optional<PushToken> currentUserToken = pushTokenRepository
                        .findByUserAndDeviceId(user, request.deviceId());

        if (currentUserToken.isPresent()) {
            PushToken pushToken = currentUserToken.get();

            pushToken.updateFirebaseInstallationId(request.firebaseInstallationId());

            return new PushTokenRegisterResponseDto(pushToken.getId());
        }

        Optional<PushToken> existingInstallation =
                pushTokenRepository
                        .findByDeviceIdAndFirebaseInstallationId(
                                request.deviceId(),
                                request.firebaseInstallationId()
                        );

        if (existingInstallation.isPresent()) {
            PushToken pushToken = existingInstallation.get();

            pushToken.updateOwner(user);

            return new PushTokenRegisterResponseDto(pushToken.getId());
        }

        PushToken pushToken =
                pushTokenRepository.save(
                        PushToken.builder()
                                .user(user)
                                .deviceId(request.deviceId())
                                .firebaseInstallationId(
                                        request.firebaseInstallationId()
                                )
                                .build()
                );

        return new PushTokenRegisterResponseDto(pushToken.getId());
    }

    @Transactional
    public void delete(Long pushTokenId, String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);

        PushToken pushToken = pushTokenRepository
                        .findByIdAndUser(pushTokenId, user)
                        .orElseThrow(() -> new GeneralException(PushErrorCode.PUSH_TOKEN_NOT_FOUND));

        pushTokenRepository.delete(pushToken);
    }
}