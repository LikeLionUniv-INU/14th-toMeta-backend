package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
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
public class WeeklyReportNotificationService {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofMinutes(5);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final WeeklyReportRepository weeklyReportRepository;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    public NotificationResult send(Long reportId, LocalDateTime requestedAt) {
        LocalDateTime startedAt = requestedAt.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime staleBefore = startedAt.minus(DELIVERY_TIMEOUT);
        String attemptId = UUID.randomUUID().toString();

        int claimed = weeklyReportRepository.claimWeeklyNotification(
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
            WeeklyReport weeklyReport = weeklyReportRepository
                    .findByIdWithUser(reportId)
                    .orElseThrow();

            LocalDateTime deliveryStartedAt = LocalDateTime
                    .now(clock.withZone(KOREA_ZONE))
                    .truncatedTo(ChronoUnit.MICROS);
            int started = weeklyReportRepository
                    .beginWeeklyNotificationDelivery(
                            reportId,
                            attemptId,
                            deliveryStartedAt
                    );
            if (started == 0) {
                return NotificationResult.skipped();
            }
            deliveryStarted = true;

            int successCount = pushNotificationService
                    .sendWeeklyReportNotification(
                            weeklyReport.getUser().getId(),
                            weeklyReport.getWeekStartDate()
                    );

            int markedSent = weeklyReportRepository.markWeeklyNotificationSent(
                    reportId,
                    attemptId,
                    requestedAt
            );
            if (markedSent == 0) {
                throw new IllegalStateException(
                        "Weekly notification completion was not persisted"
                );
            }
            return NotificationResult.sent(successCount);
        } catch (RuntimeException e) {
            if (!deliveryStarted) {
                weeklyReportRepository.resetWeeklyNotificationClaim(
                        reportId,
                        attemptId
                );
            } else {
                persistUnknownOutcome(reportId, attemptId, e);
                log.atError()
                        .setCause(e)
                        .addArgument(reportId)
                        .addArgument(attemptId)
                        .log("Weekly notification delivery outcome is unknown. reportId={}, attemptId={}");
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
            int markedUnknown = weeklyReportRepository
                    .markWeeklyNotificationUnknown(reportId, attemptId);
            if (markedUnknown == 0) {
                log.warn(
                        "Weekly notification was not transitioned to unknown. reportId={}, attemptId={}",
                        reportId,
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
