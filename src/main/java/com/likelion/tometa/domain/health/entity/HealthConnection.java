package com.likelion.tometa.domain.health.entity;

import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "health_connections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_health_connections_user_device",
                        columnNames = {"user_id", "device_id"}
                ),
                @UniqueConstraint(
                        name = "uk_health_connections_device_token_hash",
                        columnNames = "device_token_hash"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "device_token_hash", nullable = false, length = 64)
    private String deviceTokenHash;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Builder
    private HealthConnection(User user, String deviceId, String deviceTokenHash) {
        this.user = user;
        this.deviceId = deviceId;
        this.deviceTokenHash = deviceTokenHash;
        this.connectedAt = LocalDateTime.now();
    }

    public void markSynced() {
        this.lastSyncedAt = LocalDateTime.now();
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void reconnect(String deviceTokenHash) {
        this.deviceTokenHash = deviceTokenHash;
        this.connectedAt = LocalDateTime.now();
        this.revokedAt = null;
    }
}
