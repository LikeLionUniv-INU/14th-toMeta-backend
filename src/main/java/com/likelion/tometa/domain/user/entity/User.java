package com.likelion.tometa.domain.user.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "nickname", length = 10)
    private String nickname;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "age_group", length = 10)
    private String ageGroup;

    @Column(name = "skin_type", length = 30)
    private String skinType;

    @Column(name = "allow_notification", nullable = false)
    private boolean allowNotification;

    @Column(name = "notification_time")
    private LocalTime notificationTime;

    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt;

    @Builder
    private User(
            String nickname,
            String gender,
            String ageGroup,
            String skinType,
            boolean allowNotification,
            LocalTime notificationTime
    ) {
        this.nickname = nickname;
        this.gender = gender;
        this.ageGroup = ageGroup;
        this.skinType = skinType;
        this.allowNotification = allowNotification;
        this.notificationTime =
                allowNotification ? notificationTime : null;
    }

    public void completeProfile(
            String nickname,
            String gender,
            String ageGroup,
            String skinType,
            boolean allowNotification,
            LocalTime notificationTime
    ) {
        this.nickname = nickname;
        this.gender = gender;
        this.ageGroup = ageGroup;
        this.skinType = skinType;
        this.allowNotification = allowNotification;
        this.notificationTime = allowNotification ? notificationTime : null;
        this.profileCompletedAt = LocalDateTime.now();
    }

    public void updateNotification(boolean allowNotification, LocalTime notificationTime) {
        this.allowNotification = allowNotification;
        this.notificationTime = allowNotification ? notificationTime : null;
    }
}
