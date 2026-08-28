package com.likelion.tometa.domain.user.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.likelion.tometa.domain.user.entity.PushToken;
import com.likelion.tometa.domain.user.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final PushTokenRepository pushTokenRepository;

    public int sendToUser(
            Long userId,
            String title,
            String body,
            Map<String, String> data
    ) {
        return sendToUser(userId, title, body, data, () -> {
        });
    }

    int sendToUser(
            Long userId,
            String title,
            String body,
            Map<String, String> data,
            Runnable deliveryStarting
    ) {
        FirebaseMessaging firebaseMessaging =
                firebaseMessagingProvider.getIfAvailable();

        if (firebaseMessaging == null) {
            deliveryStarting.run();
            log.warn(
                    "Firebase가 비활성화되어 푸시를 발송하지 않습니다. userId={}",
                    userId
            );
            return 0;
        }

        List<PushToken> pushTokens =
                pushTokenRepository.findAllByUser_Id(userId);

        if (pushTokens.isEmpty()) {
            deliveryStarting.run();
            return 0;
        }

        List<PreparedPush> preparedPushes = pushTokens.stream()
                .map(pushToken -> new PreparedPush(
                        pushToken,
                        buildMessage(pushToken, title, body, data)
                ))
                .toList();
        deliveryStarting.run();

        int successCount = 0;
        List<InvalidPushToken> invalidPushTokens = new ArrayList<>();

        for (PreparedPush preparedPush : preparedPushes) {
            PushToken pushToken = preparedPush.pushToken();
            try {
                String messageId =
                        firebaseMessaging.send(
                                preparedPush.message()
                        );

                successCount++;

                log.info(
                        "FCM 푸시 발송 성공. userId={}, pushTokenId={}, messageId={}",
                        userId,
                        pushToken.getId(),
                        messageId
                );
            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode()
                        == MessagingErrorCode.UNREGISTERED) {
                    invalidPushTokens.add(
                            new InvalidPushToken(
                                    pushToken.getId(),
                                    pushToken.getFirebaseInstallationId()
                            )
                    );
                }

                log.warn(
                        "FCM 푸시 발송 실패. userId={}, pushTokenId={}, errorCode={}",
                        userId,
                        pushToken.getId(),
                        e.getMessagingErrorCode(),
                        e
                );
            }
        }

        int deletedCount = invalidPushTokens.stream()
                .mapToInt(invalidPushToken ->
                        pushTokenRepository.deleteByIdAndFirebaseInstallationId(
                                invalidPushToken.id(),
                                invalidPushToken.firebaseInstallationId()
                        )
                )
                .sum();

        if (!invalidPushTokens.isEmpty()) {
            log.info(
                    "유효하지 않은 FCM 발송 대상 정리 완료. userId={}, targetCount={}, deletedCount={}",
                    userId,
                    invalidPushTokens.size(),
                    deletedCount
            );
        }

        return successCount;
    }

    private Message buildMessage(
            PushToken pushToken,
            String title,
            String body,
            Map<String, String> data
    ) {
        Message.Builder messageBuilder = Message.builder()
                .setFid(pushToken.getFirebaseInstallationId())
                .setNotification(
                        Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build()
                );

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }
        return messageBuilder.build();
    }

    private record PreparedPush(PushToken pushToken, Message message) {
    }

    private record InvalidPushToken(
            Long id,
            String firebaseInstallationId
    ) {
    }
}
