package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.user.entity.RecordReminderDelivery;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class RecordReminderDeliveryRepositoryIntegrationTest {

    private static final LocalDate REMINDER_DATE = LocalDate.of(2026, 8, 24);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 24, 15, 45);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserNotificationSettingRepository settingRepository;
    @Autowired
    private DailyRecordRepository dailyRecordRepository;
    @Autowired
    private RecordReminderDeliveryRepository deliveryRepository;

    @Test
    void targetQuery_recoversReminderMissedAtConfiguredMinute() {
        User user = saveUserWithReminder(LocalTime.of(15, 40));

        assertEquals(
                List.of(user.getId()),
                findTargets(NOW)
        );
    }

    @Test
    void targetQuery_excludesUserWhoAlreadyRecorded() {
        User user = saveUserWithReminder(LocalTime.of(15, 40));
        dailyRecordRepository.saveAndFlush(
                DailyRecord.builder()
                        .user(user)
                        .recordDate(REMINDER_DATE)
                        .skinStatus("normal")
                        .build()
        );

        assertEquals(List.of(), findTargets(NOW));
    }

    @Test
    void staleClaim_isRetryableButFreshClaimIsNot() {
        User user = saveUserWithReminder(LocalTime.of(15, 40));
        savePendingDelivery(user);
        LocalDateTime claimedAt = NOW.minusMinutes(4);

        assertEquals(1, deliveryRepository.claim(
                user.getId(),
                REMINDER_DATE,
                "attempt-1",
                claimedAt,
                claimedAt.minusMinutes(5)
        ));
        assertEquals(List.of(), findTargets(NOW));
        assertEquals(
                List.of(user.getId()),
                findTargets(NOW.plusMinutes(2))
        );
        assertEquals(1, deliveryRepository.claim(
                user.getId(),
                REMINDER_DATE,
                "attempt-2",
                NOW.plusMinutes(2),
                NOW.minusMinutes(3)
        ));
    }

    @Test
    void staleSending_isMarkedUnknownAndCannotBeRetried() {
        User user = saveUserWithReminder(LocalTime.of(15, 40));
        RecordReminderDelivery delivery = savePendingDelivery(user);
        LocalDateTime deliveryStartedAt = NOW.minusMinutes(6);

        assertEquals(1, deliveryRepository.claim(
                user.getId(),
                REMINDER_DATE,
                "attempt-unknown",
                deliveryStartedAt,
                deliveryStartedAt.minusMinutes(5)
        ));
        assertEquals(1, deliveryRepository.beginDelivery(
                user.getId(),
                REMINDER_DATE,
                "attempt-unknown",
                deliveryStartedAt
        ));
        assertEquals(1, deliveryRepository.markStaleDeliveriesUnknown(
                NOW.minusMinutes(5)
        ));

        RecordReminderDelivery recovered = deliveryRepository
                .findById(delivery.getId())
                .orElseThrow();
        assertEquals("unknown", recovered.getNotificationStatus());
        assertEquals("attempt-unknown", recovered.getNotificationAttemptId());
        assertEquals(List.of(), findTargets(NOW));
        assertEquals(0, deliveryRepository.claim(
                user.getId(),
                REMINDER_DATE,
                "attempt-retry",
                NOW,
                NOW.minusMinutes(5)
        ));
    }

    @Test
    void markSent_storesCompletionTime() {
        User user = saveUserWithReminder(LocalTime.of(15, 40));
        RecordReminderDelivery delivery = savePendingDelivery(user);
        LocalDateTime startedAt = NOW.minusSeconds(3);
        LocalDateTime sentAt = NOW.plusNanos(123_456_000);

        assertEquals(1, deliveryRepository.claim(
                user.getId(),
                REMINDER_DATE,
                "attempt-sent",
                startedAt,
                startedAt.minusMinutes(5)
        ));
        assertEquals(1, deliveryRepository.beginDelivery(
                user.getId(),
                REMINDER_DATE,
                "attempt-sent",
                startedAt
        ));
        assertEquals(1, deliveryRepository.markSent(
                user.getId(),
                REMINDER_DATE,
                "attempt-sent",
                sentAt
        ));

        RecordReminderDelivery sentDelivery = deliveryRepository
                .findById(delivery.getId())
                .orElseThrow();
        assertEquals("sent", sentDelivery.getNotificationStatus());
        assertEquals(sentAt, sentDelivery.getNotificationSentAt());
        assertNull(sentDelivery.getNotificationStartedAt());
    }

    private User saveUserWithReminder(LocalTime reminderTime) {
        User user = userRepository.save(User.builder().build());
        settingRepository.saveAndFlush(
                UserNotificationSetting.builder()
                        .user(user)
                        .recordReminderEnabled(true)
                        .recordReminderTime(reminderTime)
                        .build()
        );
        return user;
    }

    private RecordReminderDelivery savePendingDelivery(User user) {
        return deliveryRepository.saveAndFlush(
                RecordReminderDelivery.builder()
                        .user(user)
                        .reminderDate(REMINDER_DATE)
                        .build()
        );
    }

    private List<Long> findTargets(LocalDateTime now) {
        return settingRepository.findRecordReminderTargetUserIds(
                REMINDER_DATE,
                now.toLocalTime(),
                now.minusMinutes(5)
        );
    }
}
