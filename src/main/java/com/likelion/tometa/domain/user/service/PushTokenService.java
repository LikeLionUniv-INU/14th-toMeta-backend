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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final PushTokenRepository pushTokenRepository;
    private final PlatformTransactionManager transactionManager;

    public PushTokenRegisterResponseDto register(
            PushTokenRegisterRequestDto request,
            String sessionToken
    ) {
        try {
            return executeRegister(request, sessionToken);
        } catch (DataIntegrityViolationException e) {
            return executeRegister(request, sessionToken);
        }
    }

    private PushTokenRegisterResponseDto executeRegister(
            PushTokenRegisterRequestDto request,
            String sessionToken
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        return transactionTemplate.execute(
                status -> registerOnce(request, sessionToken)
        );
    }

    private PushTokenRegisterResponseDto registerOnce(
            PushTokenRegisterRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        Optional<PushToken> targetToken =
                pushTokenRepository
                        .findByDeviceIdAndFirebaseInstallationId(
                                request.deviceId(),
                                request.firebaseInstallationId()
                        );

        Optional<PushToken> currentUserToken =
                pushTokenRepository.findByUserAndDeviceId(
                        user,
                        request.deviceId()
                );

        if (targetToken.isPresent()) {
            PushToken target = targetToken.get();

            if (currentUserToken.isPresent()
                    && !currentUserToken.get().getId().equals(target.getId())) {

                pushTokenRepository.delete(currentUserToken.get());

                // (user_id, device_id) UNIQUE 충돌 방지를 위해
                // 소유권 이전 전에 DELETE를 DB에 먼저 반영
                pushTokenRepository.flush();
            }

            target.updateOwner(user);

            return new PushTokenRegisterResponseDto(
                    target.getId()
            );
        }

        if (currentUserToken.isPresent()) {
            PushToken current = currentUserToken.get();

            current.updateFirebaseInstallationId(
                    request.firebaseInstallationId()
            );

            return new PushTokenRegisterResponseDto(
                    current.getId()
            );
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

        return new PushTokenRegisterResponseDto(
                pushToken.getId()
        );
    }

    @Transactional
    public void delete(
            Long pushTokenId,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        PushToken pushToken =
                pushTokenRepository
                        .findByIdAndUser(
                                pushTokenId,
                                user
                        )
                        .orElseThrow(
                                () -> new GeneralException(
                                        PushErrorCode.PUSH_TOKEN_NOT_FOUND
                                )
                        );

        pushTokenRepository.delete(pushToken);
    }
}