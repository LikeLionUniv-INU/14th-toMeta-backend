package com.likelion.tometa.domain.user.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.likelion.tometa.domain.user.entity.PushToken;
import com.likelion.tometa.domain.user.repository.PushTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    @Mock
    private PushTokenRepository pushTokenRepository;
    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Test
    void sendToUser_doesNotStartDeliveryWhenTokenLookupFails() {
        FcmPushService service = new FcmPushService(
                firebaseMessagingProvider,
                pushTokenRepository
        );
        Runnable deliveryStarting = mock(Runnable.class);
        when(firebaseMessagingProvider.getIfAvailable())
                .thenReturn(firebaseMessaging);
        when(pushTokenRepository.findAllByUser_Id(1L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service.sendToUser(
                        1L,
                        "title",
                        "body",
                        Map.of(),
                        deliveryStarting
                )
        );

        verifyNoInteractions(deliveryStarting, firebaseMessaging);
    }

    @Test
    void sendToUser_startsDeliveryImmediatelyBeforeFcmCall() throws Exception {
        FcmPushService service = new FcmPushService(
                firebaseMessagingProvider,
                pushTokenRepository
        );
        Runnable deliveryStarting = mock(Runnable.class);
        PushToken pushToken = PushToken.builder()
                .deviceId("device-1")
                .firebaseInstallationId("installation-1")
                .build();
        when(firebaseMessagingProvider.getIfAvailable())
                .thenReturn(firebaseMessaging);
        when(pushTokenRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(pushToken));
        when(firebaseMessaging.send(any(Message.class)))
                .thenReturn("message-1");

        int successCount = service.sendToUser(
                1L,
                "title",
                "body",
                Map.of(),
                deliveryStarting
        );

        assertEquals(1, successCount);
        InOrder order = inOrder(deliveryStarting, firebaseMessaging);
        order.verify(deliveryStarting).run();
        order.verify(firebaseMessaging).send(any(Message.class));
    }
}
