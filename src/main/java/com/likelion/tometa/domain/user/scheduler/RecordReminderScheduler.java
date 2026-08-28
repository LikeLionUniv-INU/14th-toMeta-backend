package com.likelion.tometa.domain.user.scheduler;

import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.user.repository.RecordReminderDeliveryRepository;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.service.RecordReminderNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.likelion.tometa.domain.user.constant.RecordReminderPolicy.DELIVERY_TIMEOUT;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecordReminderScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final DailyRecordRepository dailyRecordRepository;
    private final RecordReminderDeliveryRepository deliveryRepository;
    private final RecordReminderNotificationService notificationService;
    private final Clock clock;

    @Scheduled(
            cron = "${app.notification.scheduler.record-reminder-cron:0 * * * * *}",
            zone = "Asia/Seoul"
    )
    public void sendRecordReminders() {
        LocalDateTime now = currentDateTime().truncatedTo(ChronoUnit.MINUTES);
        LocalDate recordDate = now.toLocalDate();
        LocalTime reminderTime = now.toLocalTime();

        List<Long> targetUserIds = userNotificationSettingRepository
                .findRecordReminderTargetUserIds(
                        recordDate,
                        reminderTime,
                        now.minus(DELIVERY_TIMEOUT)
                );

        int skipped = 0;
        int processed = 0;
        int notified = 0;
        int failed = 0;

        for (Long userId : targetUserIds) {
            try {
                if (dailyRecordRepository.existsByUser_IdAndRecordDate(
                        userId,
                        recordDate
                )) {
                    skipped++;
                    continue;
                }

                RecordReminderNotificationService.NotificationResult result =
                        notificationService.send(userId, recordDate, now);
                if (result.processed()) {
                    processed++;
                    notified += result.successCount();
                }
            } catch (RuntimeException e) {
                failed++;

                log.atWarn()
                        .setCause(e)
                        .addArgument(userId)
                        .addArgument(recordDate)
                        .addArgument(reminderTime)
                        .log(
                                "Record reminder notification failed. " +
                                        "userId={}, recordDate={}, reminderTime={}"
                        );
            }
        }

        log.info(
                "Record reminder scheduler completed. " +
                        "recordDate={}, reminderTime={}, targets={}, " +
                        "skipped={}, processed={}, notifications={}, failed={}",
                recordDate,
                reminderTime,
                targetUserIds.size(),
                skipped,
                processed,
                notified,
                failed
        );
    }

    @Scheduled(
            cron = "${app.notification.scheduler.record-reminder-recovery-cron:0 * * * * *}",
            zone = "Asia/Seoul"
    )
    public void recoverStaleRecordReminderDeliveries() {
        LocalDateTime staleBefore = currentDateTime()
                .truncatedTo(ChronoUnit.MINUTES)
                .minus(DELIVERY_TIMEOUT);
        try {
            int recovered = deliveryRepository
                    .markStaleDeliveriesUnknown(staleBefore);
            if (recovered > 0) {
                log.warn(
                        "Stale record reminder deliveries marked unknown. " +
                                "staleBefore={}, recovered={}",
                        staleBefore,
                        recovered
                );
            }
        } catch (RuntimeException e) {
            log.error(
                    "Failed to mark stale record reminder deliveries as unknown. " +
                            "staleBefore={}",
                    staleBefore,
                    e
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.now(clock.withZone(KOREA_ZONE));
    }
}
