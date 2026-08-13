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
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_subscriptions_endpoint_hash",
                columnNames = "endpoint_hash"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64)
    private String endpointHash;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    @Builder
    private PushSubscription(
            User user,
            String endpoint,
            String endpointHash,
            String p256dh,
            String auth
    ) {
        this.user = user;
        this.endpoint = endpoint;
        this.endpointHash = endpointHash;
        this.p256dh = p256dh;
        this.auth = auth;
    }
}
