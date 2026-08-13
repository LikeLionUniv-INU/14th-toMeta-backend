package com.likelion.tometa.domain.user.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_consents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_consents_user_type_version",
                columnNames = {"user_id", "consent_type", "version"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "consent_type", nullable = false, length = 50)
    private String consentType;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Builder
    private UserConsent(User user, String consentType, String version, boolean agreed) {
        this.user = user;
        this.consentType = consentType;
        this.version = version;
        this.agreed = agreed;
        this.agreedAt = LocalDateTime.now();
    }
}
