package com.likelion.tometa.domain.user.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "push_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_push_tokens_user_device",
                        columnNames = {"user_id", "device_id"}
                ),
                @UniqueConstraint(
                        name = "uk_push_tokens_token",
                        columnNames = "token"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "token", nullable = false, length = 512)
    private String firebaseInstallationId;

    @Builder
    private PushToken(
            User user,
            String deviceId,
            String firebaseInstallationId
    ) {
        this.user = user;
        this.deviceId = deviceId;
        this.firebaseInstallationId = firebaseInstallationId;
    }

    public void updateFirebaseInstallationId(String firebaseInstallationId) {
        this.firebaseInstallationId = firebaseInstallationId;
    }

    public void updateOwner(User user) {
        this.user = user;
    }
}