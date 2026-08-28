package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.repository.RecordReminderDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.likelion.tometa.domain.user.constant.RecordReminderPolicy.DELIVERY_TIMEOUT;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordReminderNotificationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final RecordReminderDeliveryRepository deliveryRepository;
    private final RecordReminderDeliveryInitializer deliveryInitializer;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    public NotificationResult send(
            Long userId,
            LocalDate reminderDate,
            LocalDateTime requestedAt
    ) {
        initializeDelivery(userId, reminderDate);

        LocalDateTime startedAt = requestedAt.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime staleBefore = startedAt.minus(DELIVERY_TIMEOUT);
        String attemptId = UUID.randomUUID().toString();

        int claimed = deliveryRepository.claim(
                userId,
                reminderDate,
                attemptId,
                startedAt,
                staleBefore
        );
        if (claimed == 0) {
            return NotificationResult.skipped();
        }

        AtomicBoolean deliveryStarted = new AtomicBoolean(false);
        try {
            int successCount = pushNotificationService.sendRecordReminder(
                    userId,
                    reminderDate,
                    () -> beginDelivery(
                            userId,
                            reminderDate,
                            attemptId,
                            deliveryStarted
                    )
            );
            LocalDateTime sentAt = LocalDateTime
                    .now(clock.withZone(KOREA_ZONE))
                    .truncatedTo(ChronoUnit.MICROS);

            int markedSent = deliveryRepository.markSent(
                    userId,
                    reminderDate,
                    attemptId,
                    sentAt
            );
            if (markedSent == 0) {
                throw new IllegalStateException(
                        "Record reminder completion was not persisted"
                );
            }
            return NotificationResult.sent(successCount);
        } catch (RuntimeException e) {
            if (!deliveryStarted.get()) {
                deliveryRepository.resetClaim(
                        userId,
                        reminderDate,
                        attemptId
                );
            } else {
                persistUnknownOutcome(userId, reminderDate, attemptId, e);
                log.atError()
                        .setCause(e)
                        .addArgument(userId)
                        .addArgument(reminderDate)
                        .addArgument(attemptId)
                        .log(
                                "Record reminder delivery outcome is unknown. " +
                                        "userId={}, reminderDate={}, attemptId={}"
                        );
            }
            throw e;
        }
    }

    private void beginDelivery(
            Long userId,
            LocalDate reminderDate,
            String attemptId,
            AtomicBoolean deliveryStarted
    ) {
        LocalDateTime deliveryStartedAt = LocalDateTime
                .now(clock.withZone(KOREA_ZONE))
                .truncatedTo(ChronoUnit.MICROS);
        int started = deliveryRepository.beginDelivery(
                userId,
                reminderDate,
                attemptId,
                deliveryStartedAt
        );
        if (started == 0) {
            throw new IllegalStateException(
                    "Record reminder delivery start was not persisted"
            );
        }
        deliveryStarted.set(true);
    }

    private void initializeDelivery(Long userId, LocalDate reminderDate) {
        try {
            deliveryInitializer.initialize(userId, reminderDate);
        } catch (DataIntegrityViolationException e) {
            if (!deliveryRepository.existsByUser_IdAndReminderDate(
                    userId,
                    reminderDate
            )) {
                throw e;
            }
            log.debug(
                    "Record reminder delivery was initialized concurrently. " +
                            "userId={}, reminderDate={}",
                    userId,
                    reminderDate
            );
        }
    }

    private void persistUnknownOutcome(
            Long userId,
            LocalDate reminderDate,
            String attemptId,
            RuntimeException deliveryFailure
    ) {
        try {
            int markedUnknown = deliveryRepository.markUnknown(
                    userId,
                    reminderDate,
                    attemptId
            );
            if (markedUnknown == 0) {
                log.warn(
                        "Record reminder was not transitioned to unknown. " +
                                "userId={}, reminderDate={}, attemptId={}",
                        userId,
                        reminderDate,
                        attemptId
                );
            }
        } catch (RuntimeException persistenceFailure) {
            deliveryFailure.addSuppressed(persistenceFailure);
        }
    }

    public record NotificationResult(boolean processed, int successCount) {

        private static NotificationResult sent(int successCount) {
            return new NotificationResult(true, successCount);
        }

        private static NotificationResult skipped() {
            return new NotificationResult(false, 0);
        }
    }
}
