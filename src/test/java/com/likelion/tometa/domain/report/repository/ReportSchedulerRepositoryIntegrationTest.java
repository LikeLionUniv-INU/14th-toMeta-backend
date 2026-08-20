package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import com.likelion.tometa.domain.user.repository.UserNotificationSettingRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class ReportSchedulerRepositoryIntegrationTest {

    private static final LocalDate WEEK_START_DATE =
            LocalDate.of(2026, 8, 17);
    private static final LocalDate WEEK_END_DATE =
            LocalDate.of(2026, 8, 23);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserNotificationSettingRepository settingRepository;
    @Autowired
    private DailyRecordRepository dailyRecordRepository;
    @Autowired
    private DailyReportRepository dailyReportRepository;
    @Autowired
    private WeeklyReportRepository weeklyReportRepository;

    @Test
    void generationTargets_excludeAlreadyCompletedReports() {
        User pendingUser = userRepository.save(User.builder().build());
        User completedUser = userRepository.save(User.builder().build());
        saveDailyReport(
                pendingUser,
                WEEK_END_DATE,
                false
        );
        saveDailyReport(completedUser, WEEK_END_DATE, true);

        assertEquals(
                List.of(pendingUser.getId()),
                dailyRecordRepository
                        .findDailyReportGenerationTargetUserIds(WEEK_END_DATE)
        );

        WeeklyReport completedWeeklyReport = WeeklyReport.builder()
                .user(completedUser)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_END_DATE)
                .build();
        completedWeeklyReport.complete("summary", "solution");
        weeklyReportRepository.saveAndFlush(completedWeeklyReport);

        assertEquals(
                List.of(pendingUser.getId()),
                weeklyReportRepository
                        .findWeeklyReportGenerationTargetUserIds(
                                WEEK_START_DATE,
                                WEEK_END_DATE
                        )
        );
    }

    @Test
    void notificationTarget_isClaimedAndSentOnlyOnce() {
        User user = userRepository.save(User.builder().build());
        settingRepository.save(UserNotificationSetting.builder()
                .user(user)
                .weeklyReportEnabled(true)
                .weeklyReportTime(LocalTime.of(10, 0))
                .build());
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_END_DATE)
                .build();
        report.complete("summary", "solution");
        weeklyReportRepository.saveAndFlush(report);
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 10, 0);

        assertEquals(
                List.of(),
                weeklyReportRepository.findWeeklyNotificationTargetIds(
                        WEEK_START_DATE,
                        LocalTime.of(9, 59),
                        now.minusMinutes(5)
                )
        );
        assertEquals(
                List.of(report.getId()),
                weeklyReportRepository.findWeeklyNotificationTargetIds(
                        WEEK_START_DATE,
                        LocalTime.of(10, 0),
                        now.minusMinutes(5)
                )
        );
        assertEquals(1, weeklyReportRepository.claimWeeklyNotification(
                report.getId(),
                "attempt-1",
                now,
                now.minusMinutes(5)
        ));
        assertEquals(0, weeklyReportRepository.claimWeeklyNotification(
                report.getId(),
                "attempt-2",
                now.plusMinutes(1),
                now.minusMinutes(4)
        ));
        assertEquals(1, weeklyReportRepository.beginWeeklyNotificationDelivery(
                report.getId(),
                "attempt-1",
                now
        ));
        assertEquals(0, weeklyReportRepository.claimWeeklyNotification(
                report.getId(),
                "attempt-3",
                now.plusMinutes(10),
                now.plusMinutes(5)
        ));
        assertEquals(1, weeklyReportRepository.markWeeklyNotificationSent(
                report.getId(),
                "attempt-1",
                now
        ));
        assertEquals(
                List.of(),
                weeklyReportRepository.findWeeklyNotificationTargetIds(
                        WEEK_START_DATE,
                        LocalTime.of(10, 1),
                        now.minusMinutes(4)
                )
        );
    }

    @Test
    void staleSendingNotification_isMarkedUnknownAndCannotBeReclaimed() {
        User user = userRepository.save(User.builder().build());
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStartDate(WEEK_START_DATE)
                .weekEndDate(WEEK_END_DATE)
                .build();
        report.complete("summary", "solution");
        weeklyReportRepository.saveAndFlush(report);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 8, 24, 10, 0);
        LocalDateTime deliveryStartedAt = claimedAt.plusMinutes(6);

        assertEquals(1, weeklyReportRepository.claimWeeklyNotification(
                report.getId(),
                "attempt-unknown",
                claimedAt,
                claimedAt.minusMinutes(5)
        ));
        assertEquals(1, weeklyReportRepository.beginWeeklyNotificationDelivery(
                report.getId(),
                "attempt-unknown",
                deliveryStartedAt
        ));
        assertEquals(
                0,
                weeklyReportRepository
                        .markStaleWeeklyNotificationDeliveriesUnknown(
                                deliveryStartedAt.minusNanos(1)
                        )
        );
        WeeklyReport sendingReport = weeklyReportRepository
                .findById(report.getId())
                .orElseThrow();
        assertEquals("sending", sendingReport.getNotificationStatus());
        assertEquals(
                deliveryStartedAt,
                sendingReport.getNotificationStartedAt()
        );
        assertEquals(
                1,
                weeklyReportRepository
                        .markStaleWeeklyNotificationDeliveriesUnknown(
                                deliveryStartedAt.plusMinutes(5)
                        )
        );

        WeeklyReport unknownReport = weeklyReportRepository
                .findById(report.getId())
                .orElseThrow();
        assertEquals("unknown", unknownReport.getNotificationStatus());
        assertEquals(
                "attempt-unknown",
                unknownReport.getNotificationAttemptId()
        );
        assertEquals(0, weeklyReportRepository.claimWeeklyNotification(
                report.getId(),
                "attempt-retry",
                deliveryStartedAt.plusMinutes(10),
                deliveryStartedAt.plusMinutes(5)
        ));
    }

    private DailyReport saveDailyReport(
            User user,
            LocalDate date,
            boolean completed
    ) {
        DailyRecord dailyRecord = dailyRecordRepository.save(
                DailyRecord.builder()
                        .user(user)
                        .recordDate(date)
                        .skinStatus("normal")
                        .build()
        );
        DailyReport dailyReport = DailyReport.builder()
                .dailyRecord(dailyRecord)
                .build();
        if (completed) {
            dailyReport.complete(null, "summary", "analysis", "solution");
        }
        return dailyReportRepository.saveAndFlush(dailyReport);
    }
}
