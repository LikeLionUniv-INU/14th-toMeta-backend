package com.likelion.tometa.domain.user.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@Entity
@Table(
        name = "user_notification_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_notification_settings_user",
                columnNames = "user_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_setting_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "daily_report_enabled", nullable = false)
    private boolean dailyReportEnabled;

    @Column(name = "record_reminder_enabled", nullable = false)
    private boolean recordReminderEnabled;

    @Column(name = "record_reminder_time")
    private LocalTime recordReminderTime;

    @Column(name = "weekly_report_enabled", nullable = false)
    private boolean weeklyReportEnabled;

    @Column(name = "weekly_report_time")
    private LocalTime weeklyReportTime;

    @Builder
    private UserNotificationSetting(
            User user,
            boolean dailyReportEnabled,
            boolean recordReminderEnabled,
            LocalTime recordReminderTime,
            boolean weeklyReportEnabled,
            LocalTime weeklyReportTime
    ) {
        this.user = user;
        this.dailyReportEnabled = dailyReportEnabled;
        this.recordReminderEnabled = recordReminderEnabled;
        this.recordReminderTime = recordReminderTime;
        this.weeklyReportEnabled = weeklyReportEnabled;
        this.weeklyReportTime = weeklyReportTime;
    }

    public void update(
            boolean dailyReportEnabled,
            boolean recordReminderEnabled,
            LocalTime recordReminderTime,
            boolean weeklyReportEnabled,
            LocalTime weeklyReportTime
    ) {
        this.dailyReportEnabled = dailyReportEnabled;
        this.recordReminderEnabled = recordReminderEnabled;
        this.recordReminderTime = recordReminderTime;
        this.weeklyReportEnabled = weeklyReportEnabled;
        this.weeklyReportTime = weeklyReportTime;
    }
}