package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.entity.RecordImageObject;
import com.likelion.tometa.domain.record.enums.RecordImageObjectStatus;
import com.likelion.tometa.domain.record.repository.RecordImageObjectRepository;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordImageOwnershipServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");

    @Mock
    private RecordImageObjectRepository recordImageObjectRepository;

    private RecordImageOwnershipService service;

    @BeforeEach
    void setUp() {
        service = new RecordImageOwnershipService(
                recordImageObjectRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void registerPending_savesOwnershipForIssuedKeys() {
        List<String> keys = List.of("skin-images/1/first.jpg", "skin-images/1/second.jpg");

        service.registerPending(1L, keys);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecordImageObject>> captor = ArgumentCaptor.forClass(List.class);
        verify(recordImageObjectRepository).saveAllAndFlush(captor.capture());
        assertEquals(keys, captor.getValue().stream()
                .map(RecordImageObject::getObjectKey)
                .toList());
        assertTrue(captor.getValue().stream()
                .allMatch(object -> object.getStatus() == RecordImageObjectStatus.PENDING));
    }

    @Test
    void claimForAttachment_changesPendingObjectToAttached() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.pending(1L, key);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        service.claimForAttachment(1L, List.of(key));

        assertSame(RecordImageObjectStatus.ATTACHED, object.getStatus());
    }

    @Test
    void replaceAttachments_releasesRemovedAndClaimsAddedObjects() {
        String removedKey = "skin-images/1/removed.jpg";
        String addedKey = "skin-images/1/added.jpg";
        RecordImageObject removed = RecordImageObject.attached(1L, removedKey);
        RecordImageObject added = RecordImageObject.pending(1L, addedKey);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(addedKey))
                .thenReturn(Optional.of(added));
        when(recordImageObjectRepository.findByObjectKeyForUpdate(removedKey))
                .thenReturn(Optional.of(removed));

        service.replaceAttachments(
                1L,
                List.of(removedKey),
                List.of(addedKey)
        );

        assertSame(RecordImageObjectStatus.PENDING, removed.getStatus());
        assertSame(RecordImageObjectStatus.ATTACHED, added.getStatus());
    }

    @Test
    void claimForAttachment_rejectsCleanupClaimedObject() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.cleanupClaimed(
                1L,
                key,
                "active-claim",
                NOW
        );
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.claimForAttachment(1L, List.of(key))
        );

        assertSame(RecordImageErrorCode.IMAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void claimForCleanup_changesPendingObjectToCleanupClaimed() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.pending(1L, key);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        Optional<String> claimToken = service.claimForCleanup(key);

        assertTrue(claimToken.isPresent());
        assertSame(RecordImageObjectStatus.CLEANUP_CLAIMED, object.getStatus());
        assertEquals(claimToken.get(), object.getCleanupClaimToken());
    }

    @Test
    void claimForCleanup_doesNotClaimAttachedObject() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.attached(1L, key);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        Optional<String> claimToken = service.claimForCleanup(key);

        assertTrue(claimToken.isEmpty());
        assertSame(RecordImageObjectStatus.ATTACHED, object.getStatus());
    }

    @Test
    void releaseCleanupClaim_returnsObjectToPending() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.cleanupClaimed(1L, key, "claim-token", NOW);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        service.releaseCleanupClaim(key, "claim-token");

        assertSame(RecordImageObjectStatus.PENDING, object.getStatus());
        assertNull(object.getCleanupClaimToken());
    }

    @Test
    void releaseCleanupClaim_doesNotReleaseAnotherWorkerClaim() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.cleanupClaimed(1L, key, "active-claim", NOW);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        service.releaseCleanupClaim(key, "different-claim");

        assertSame(RecordImageObjectStatus.CLEANUP_CLAIMED, object.getStatus());
        assertEquals("active-claim", object.getCleanupClaimToken());
    }

    @Test
    void claimForCleanup_doesNotStealActiveClaim() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.cleanupClaimed(1L, key, "active-claim", NOW);
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        Optional<String> claimToken = service.claimForCleanup(key);

        assertTrue(claimToken.isEmpty());
        assertEquals("active-claim", object.getCleanupClaimToken());
    }

    @Test
    void claimForCleanup_reclaimsExpiredClaim() {
        String key = "skin-images/1/image.jpg";
        RecordImageObject object = RecordImageObject.cleanupClaimed(
                1L,
                key,
                "expired-claim",
                NOW.minus(Duration.ofMinutes(16))
        );
        when(recordImageObjectRepository.findByObjectKeyForUpdate(key))
                .thenReturn(Optional.of(object));

        Optional<String> claimToken = service.claimForCleanup(key);

        assertTrue(claimToken.isPresent());
        assertFalse("expired-claim".equals(claimToken.get()));
        assertEquals(claimToken.get(), object.getCleanupClaimToken());
    }

    @Test
    void findRecoverableCleanupKeys_returnsExpiredClaimsInRequestedBatch() {
        List<String> keys = List.of("skin-images/1/stale.jpg");
        when(recordImageObjectRepository.findCleanupClaimKeysClaimedBefore(
                eq(RecordImageObjectStatus.CLEANUP_CLAIMED),
                eq(NOW.minus(Duration.ofMinutes(15))),
                any(Pageable.class)
        )).thenReturn(keys);

        List<String> result = service.findRecoverableCleanupKeys(100);

        assertEquals(keys, result);
    }
}
