package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class WeeklyReportNotificationService {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofMinutes(5);

    private final WeeklyReportRepository weeklyReportRepository;
    private final PushNotificationService pushNotificationService;

    public NotificationResult send(Long reportId, LocalDateTime requestedAt) {
        LocalDateTime startedAt = requestedAt.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime staleBefore = startedAt.minus(DELIVERY_TIMEOUT);

        int claimed = weeklyReportRepository.claimWeeklyNotification(
                reportId,
                startedAt,
                staleBefore
        );
        if (claimed == 0) {
            return NotificationResult.skipped();
        }

        try {
            WeeklyReport weeklyReport = weeklyReportRepository
                    .findByIdWithUser(reportId)
                    .orElseThrow();

            int successCount = pushNotificationService
                    .sendWeeklyReportNotification(
                            weeklyReport.getUser().getId(),
                            weeklyReport.getWeekStartDate()
                    );

            weeklyReportRepository.markWeeklyNotificationSent(
                    reportId,
                    startedAt,
                    requestedAt
            );
            return NotificationResult.sent(successCount);
        } catch (RuntimeException e) {
            weeklyReportRepository.resetWeeklyNotification(
                    reportId,
                    startedAt
            );
            throw e;
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
