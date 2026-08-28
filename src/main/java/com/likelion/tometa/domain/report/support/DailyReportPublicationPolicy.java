package com.likelion.tometa.domain.report.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class DailyReportPublicationPolicy {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;
    private final CronExpression publicationCron;

    public DailyReportPublicationPolicy(
            Clock clock,
            @Value("${app.report.scheduler.daily-generation-cron:0 0 7 * * *}")
            String publicationCron
    ) {
        this.clock = clock;
        this.publicationCron = CronExpression.parse(publicationCron);
    }

    public boolean isPublicationTimeReached(LocalDate reportDate) {
        ZonedDateTime publicationTime = publicationCron.next(
                reportDate.atStartOfDay(KOREA_ZONE).minusNanos(1)
        );
        if (publicationTime == null
                || !publicationTime.toLocalDate().equals(reportDate)) {
            return false;
        }

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(KOREA_ZONE));
        return !now.isBefore(publicationTime);
    }
}
