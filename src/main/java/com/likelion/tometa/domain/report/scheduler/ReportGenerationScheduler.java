package com.likelion.tometa.domain.report.scheduler;

import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.service.WeeklyReportGenerationService;
import com.likelion.tometa.domain.report.service.WeeklyReportNotificationService;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationScheduler {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration NOTIFICATION_TIMEOUT = Duration.ofMinutes(5);

    private final DailyRecordRepository dailyRecordRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final UserRepository userRepository;
    private final DailyReportGenerationService dailyReportGenerationService;
    private final WeeklyReportGenerationService weeklyReportGenerationService;
    private final WeeklyReportNotificationService weeklyReportNotificationService;
    private final PushNotificationService pushNotificationService;
    private final Clock clock;

    @Scheduled(
            cron = "${app.report.scheduler.daily-generation-cron:0 0 7 * * *}",
            zone = "Asia/Seoul"
    )
    public void generateDailyReports() {
        LocalDate reportDate = currentDateTime().toLocalDate().minusDays(1);
        List<Long> targetUserIds = dailyRecordRepository
                .findDailyReportGenerationTargetUserIds(reportDate);

        int generated = 0;
        int notified = 0;
        int generationFailed = 0;
        int notificationFailed = 0;

        for (Long userId : targetUserIds) {
            try {
                User user = userRepository.findById(userId).orElseThrow();
                ReportGenerationResult<?> result = dailyReportGenerationService
                        .generate(user, reportDate);
                if (result.generated()) {
                    generated++;
                    try {
                        notified += pushNotificationService
                                .sendDailyReportNotification(
                                        userId,
                                        reportDate
                                );
                    } catch (RuntimeException e) {
                        notificationFailed++;
                        log.atWarn()
                                .setCause(e)
                                .addArgument(userId)
                                .addArgument(reportDate)
                                .log("Daily report notification failed. userId={}, reportDate={}");
                    }
                }
            } catch (RuntimeException e) {
                generationFailed++;
                log.atWarn()
                        .setCause(e)
                        .addArgument(userId)
                        .addArgument(reportDate)
                        .log("Daily report generation failed. userId={}, reportDate={}");
            }
        }

        log.info(
                "Daily report scheduler completed. reportDate={}, targets={}, generated={}, notifications={}, generationFailed={}, notificationFailed={}",
                reportDate,
                targetUserIds.size(),
                generated,
                notified,
                generationFailed,
                notificationFailed
        );
    }

    @Scheduled(
            cron = "${app.report.scheduler.weekly-generation-cron:0 0 0 * * MON}",
            zone = "Asia/Seoul"
    )
    public void generateWeeklyReports() {
        LocalDate currentMonday = currentDateTime().toLocalDate();
        LocalDate weekStartDate = currentMonday.minusWeeks(1);
        LocalDate weekEndDate = currentMonday.minusDays(1);
        List<Long> targetUserIds = weeklyReportRepository
                .findWeeklyReportGenerationTargetUserIds(
                        weekStartDate,
                        weekEndDate
                );

        int generated = 0;
        int failed = 0;

        for (Long userId : targetUserIds) {
            try {
                User user = userRepository.findById(userId).orElseThrow();
                if (weeklyReportGenerationService
                        .generate(user, weekStartDate)
                        .generated()) {
                    generated++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.atWarn()
                        .setCause(e)
                        .addArgument(userId)
                        .addArgument(weekStartDate)
                        .log("Weekly report generation failed. userId={}, weekStartDate={}");
            }
        }

        log.info(
                "Weekly report scheduler completed. weekStartDate={}, weekEndDate={}, targets={}, generated={}, failed={}",
                weekStartDate,
                weekEndDate,
                targetUserIds.size(),
                generated,
                failed
        );
    }

    @Scheduled(
            cron = "${app.report.scheduler.weekly-notification-cron:0 * * * * MON}",
            zone = "Asia/Seoul"
    )
    public void sendWeeklyReportNotifications() {
        LocalDateTime now = currentDateTime()
                .truncatedTo(ChronoUnit.MINUTES);
        LocalDate weekStartDate = now.toLocalDate().minusWeeks(1);
        LocalTime currentTime = now.toLocalTime();
        List<Long> reportIds = weeklyReportRepository
                .findWeeklyNotificationTargetIds(
                        weekStartDate,
                        currentTime,
                        now.minus(NOTIFICATION_TIMEOUT)
                );

        int processed = 0;
        int notified = 0;
        int failed = 0;

        for (Long reportId : reportIds) {
            try {
                WeeklyReportNotificationService.NotificationResult result =
                        weeklyReportNotificationService.send(reportId, now);
                if (result.processed()) {
                    processed++;
                    notified += result.successCount();
                }
            } catch (RuntimeException e) {
                failed++;
                log.atWarn()
                        .setCause(e)
                        .addArgument(reportId)
                        .log("Weekly report notification failed. reportId={}");
            }
        }

        log.info(
                "Weekly report notification scheduler completed. weekStartDate={}, targets={}, processed={}, notifications={}, failed={}",
                weekStartDate,
                reportIds.size(),
                processed,
                notified,
                failed
        );
    }

    @Scheduled(
            cron = "${app.report.scheduler.weekly-notification-recovery-cron:0 * * * * *}",
            zone = "Asia/Seoul"
    )
    public void recoverStaleWeeklyNotificationDeliveries() {
        LocalDateTime staleBefore = currentDateTime()
                .truncatedTo(ChronoUnit.MINUTES)
                .minus(NOTIFICATION_TIMEOUT);
        try {
            int recovered = weeklyReportRepository
                    .markStaleWeeklyNotificationDeliveriesUnknown(staleBefore);
            if (recovered > 0) {
                log.warn(
                        "Stale weekly notification deliveries marked unknown. staleBefore={}, recovered={}",
                        staleBefore,
                        recovered
                );
            }
        } catch (RuntimeException e) {
            log.error(
                    "Failed to mark stale weekly notification deliveries as unknown. staleBefore={}",
                    staleBefore,
                    e
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.now(clock.withZone(KOREA_ZONE));
    }
}
