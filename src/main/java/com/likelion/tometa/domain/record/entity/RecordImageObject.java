package com.likelion.tometa.domain.record.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.record.enums.RecordImageObjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "record_image_objects",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_record_image_objects_object_key",
                columnNames = "object_key"
        ),
        indexes = @Index(
                name = "idx_record_image_objects_status",
                columnList = "status"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordImageObject extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_image_object_id")
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RecordImageObjectStatus status;

    @Column(name = "cleanup_claim_token", length = 36)
    private String cleanupClaimToken;

    @Column(name = "cleanup_claimed_at")
    private Instant cleanupClaimedAt;

    private RecordImageObject(
            Long ownerUserId,
            String objectKey,
            RecordImageObjectStatus status
    ) {
        this.ownerUserId = ownerUserId;
        this.objectKey = objectKey;
        this.status = status;
    }

    public static RecordImageObject pending(Long ownerUserId, String objectKey) {
        return new RecordImageObject(ownerUserId, objectKey, RecordImageObjectStatus.PENDING);
    }

    public static RecordImageObject attached(Long ownerUserId, String objectKey) {
        return new RecordImageObject(ownerUserId, objectKey, RecordImageObjectStatus.ATTACHED);
    }

    public static RecordImageObject cleanupClaimed(
            Long ownerUserId,
            String objectKey,
            String claimToken,
            Instant claimedAt
    ) {
        RecordImageObject object = new RecordImageObject(
                ownerUserId,
                objectKey,
                RecordImageObjectStatus.CLEANUP_CLAIMED
        );
        object.cleanupClaimToken = claimToken;
        object.cleanupClaimedAt = claimedAt;
        return object;
    }

    public void markAttached() {
        this.status = RecordImageObjectStatus.ATTACHED;
    }

    public void markPending() {
        transitionToPending();
    }

    public void claimCleanup(String claimToken, Instant claimedAt) {
        this.status = RecordImageObjectStatus.CLEANUP_CLAIMED;
        this.cleanupClaimToken = claimToken;
        this.cleanupClaimedAt = claimedAt;
    }

    public void releaseCleanupClaim() {
        transitionToPending();
    }

    public void markDeleted() {
        this.status = RecordImageObjectStatus.DELETED;
        clearCleanupClaim();
    }

    public boolean hasCleanupClaim(String claimToken) {
        return this.status == RecordImageObjectStatus.CLEANUP_CLAIMED
                && claimToken != null
                && claimToken.equals(this.cleanupClaimToken);
    }

    public boolean isCleanupClaimExpired(Instant expirationThreshold) {
        return this.status == RecordImageObjectStatus.CLEANUP_CLAIMED
                && (cleanupClaimedAt == null || !cleanupClaimedAt.isAfter(expirationThreshold));
    }

    private void clearCleanupClaim() {
        this.cleanupClaimToken = null;
        this.cleanupClaimedAt = null;
    }

    private void transitionToPending() {
        this.status = RecordImageObjectStatus.PENDING;
        clearCleanupClaim();
    }
}
