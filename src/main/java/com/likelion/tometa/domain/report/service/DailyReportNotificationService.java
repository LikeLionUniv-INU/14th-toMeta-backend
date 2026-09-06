package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportNotificationService {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofMinutes(5);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyReportRepository dailyReportRepository;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    public NotificationResult send(Long reportId) {
        LocalDateTime requestedAt = LocalDateTime
                .now(clock.withZone(KOREA_ZONE))
                .truncatedTo(ChronoUnit.MICROS);

        return send(reportId, requestedAt);
    }

    public NotificationResult send(
            Long reportId,
            LocalDateTime requestedAt
    ) {
        LocalDateTime startedAt = requestedAt
                .truncatedTo(ChronoUnit.MICROS);
        LocalDateTime staleBefore = startedAt.minus(DELIVERY_TIMEOUT);
        String attemptId = UUID.randomUUID().toString();

        int claimed = dailyReportRepository.claimDailyNotification(
                reportId,
                attemptId,
                startedAt,
                staleBefore
        );

        if (claimed == 0) {
            return NotificationResult.skipped();
        }

        boolean deliveryStarted = false;

        try {
            DailyReport dailyReport = dailyReportRepository
                    .findByIdWithUser(reportId)
                    .orElseThrow();

            LocalDateTime deliveryStartedAt = LocalDateTime
                    .now(clock.withZone(KOREA_ZONE))
                    .truncatedTo(ChronoUnit.MICROS);

            int started = dailyReportRepository.beginDailyNotificationDelivery(
                    reportId,
                    attemptId,
                    deliveryStartedAt
            );

            if (started == 0) {
                return NotificationResult.skipped();
            }

            deliveryStarted = true;

            int successCount = pushNotificationService
                    .sendDailyReportNotification(
                            dailyReport.getDailyRecord()
                                    .getUser()
                                    .getId(),
                            dailyReport.getDailyRecord()
                                    .getRecordDate()
                    );

            int markedSent = dailyReportRepository.markDailyNotificationSent(
                    reportId,
                    attemptId,
                    requestedAt
            );

            if (markedSent == 0) {
                throw new IllegalStateException(
                        "Daily notification completion was not persisted"
                );
            }

            return NotificationResult.sent(successCount);
        } catch (RuntimeException e) {
            if (!deliveryStarted) {
                dailyReportRepository.resetDailyNotificationClaim(
                        reportId,
                        attemptId
                );
            } else {
                persistUnknownOutcome(
                        reportId,
                        attemptId,
                        e
                );

                log.atError()
                        .setCause(e)
                        .addArgument(reportId)
                        .addArgument(attemptId)
                        .log("Daily notification delivery outcome is unknown. "
                                + "reportId={}, attemptId={}");
            }

            throw e;
        }
    }

    private void persistUnknownOutcome(
            Long reportId,
            String attemptId,
            RuntimeException deliveryFailure
    ) {
        try {
            int markedUnknown = dailyReportRepository
                    .markDailyNotificationUnknown(
                            reportId,
                            attemptId
                    );

            if (markedUnknown == 0) {
                log.warn(
                        "Daily notification was not transitioned to unknown. "
                                + "reportId={}, attemptId={}",
                        reportId,
                        attemptId
                );
            }
        } catch (RuntimeException persistenceFailure) {
            deliveryFailure.addSuppressed(persistenceFailure);
        }
    }

    public record NotificationResult(
            boolean processed,
            int successCount
    ) {

        private static NotificationResult sent(int successCount) {
            return new NotificationResult(
                    true,
                    successCount
            );
        }

        private static NotificationResult skipped() {
            return new NotificationResult(
                    false,
                    0
            );
        }
    }
}
