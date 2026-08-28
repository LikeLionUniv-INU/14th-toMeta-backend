package com.likelion.tometa.domain.user.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "record_reminder_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_record_reminder_deliveries_user_date",
                columnNames = {"user_id", "reminder_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordReminderDelivery extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_reminder_delivery_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "notification_status", nullable = false, length = 20)
    private String notificationStatus;

    @Column(name = "notification_started_at")
    private LocalDateTime notificationStartedAt;

    @Column(name = "notification_attempt_id", length = 36)
    private String notificationAttemptId;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    @Builder
    private RecordReminderDelivery(User user, LocalDate reminderDate) {
        this.user = user;
        this.reminderDate = reminderDate;
        this.notificationStatus = "pending";
    }
}
