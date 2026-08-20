package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.entity.RecordImageObject;
import com.likelion.tometa.domain.record.enums.RecordImageObjectStatus;
import com.likelion.tometa.domain.record.repository.RecordImageObjectRepository;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.OBJECT_KEY_ROOT_PREFIX;

@Service
@RequiredArgsConstructor
public class RecordImageOwnershipService {

    private static final Duration CLEANUP_CLAIM_LEASE = Duration.ofMinutes(15);

    private final RecordImageObjectRepository recordImageObjectRepository;
    private final Clock clock;

    @Transactional
    public void registerPending(Long ownerUserId, List<String> objectKeys) {
        List<RecordImageObject> objects = objectKeys.stream()
                .map(objectKey -> RecordImageObject.pending(ownerUserId, objectKey))
                .toList();
        recordImageObjectRepository.saveAllAndFlush(objects);
    }

    @Transactional
    public void claimForAttachment(Long ownerUserId, List<String> objectKeys) {
        objectKeys.stream()
                .sorted()
                .forEach(objectKey -> claimForAttachment(ownerUserId, objectKey));
    }

    @Transactional
    public void replaceAttachments(
            Long ownerUserId,
            List<String> removedObjectKeys,
            List<String> addedObjectKeys
    ) {
        Set<String> added = Set.copyOf(addedObjectKeys);
        java.util.stream.Stream.concat(
                        removedObjectKeys.stream(),
                        addedObjectKeys.stream()
                )
                .distinct()
                .sorted()
                .forEach(objectKey -> {
                    if (added.contains(objectKey)) {
                        claimForAttachment(ownerUserId, objectKey);
                    } else {
                        releaseAttachment(ownerUserId, objectKey);
                    }
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> claimForCleanup(String objectKey) {
        Optional<RecordImageObject> optionalObject =
                recordImageObjectRepository.findByObjectKeyForUpdate(objectKey);
        if (optionalObject.isEmpty()) {
            return claimLegacyObject(objectKey);
        }

        RecordImageObject object = optionalObject.get();
        Instant now = clock.instant();
        if (object.getStatus() == RecordImageObjectStatus.PENDING
                || object.isCleanupClaimExpired(now.minus(CLEANUP_CLAIM_LEASE))) {
            String claimToken = UUID.randomUUID().toString();
            object.claimCleanup(claimToken, now);
            return Optional.of(claimToken);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<String> findRecoverableCleanupKeys(int limit) {
        return recordImageObjectRepository.findCleanupClaimKeysClaimedBefore(
                RecordImageObjectStatus.CLEANUP_CLAIMED,
                clock.instant().minus(CLEANUP_CLAIM_LEASE),
                PageRequest.of(0, limit)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeleted(String objectKey, String claimToken) {
        recordImageObjectRepository.findByObjectKeyForUpdate(objectKey)
                .filter(object -> object.hasCleanupClaim(claimToken))
                .ifPresent(RecordImageObject::markDeleted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseCleanupClaim(String objectKey, String claimToken) {
        recordImageObjectRepository.findByObjectKeyForUpdate(objectKey)
                .filter(object -> object.hasCleanupClaim(claimToken))
                .ifPresent(RecordImageObject::releaseCleanupClaim);
    }

    private void claimForAttachment(Long ownerUserId, String objectKey) {
        Optional<RecordImageObject> optionalObject =
                recordImageObjectRepository.findByObjectKeyForUpdate(objectKey);
        if (optionalObject.isEmpty()) {
            registerLegacyAttachment(ownerUserId, objectKey);
            return;
        }

        RecordImageObject object = optionalObject.get();
        if (!ownerUserId.equals(object.getOwnerUserId())) {
            throw new GeneralException(RecordImageErrorCode.INVALID_IMAGE_KEY);
        }
        if (object.getStatus() == RecordImageObjectStatus.ATTACHED) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_ALREADY_USED);
        }
        if (object.getStatus() != RecordImageObjectStatus.PENDING) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_NOT_FOUND);
        }
        object.markAttached();
    }

    private void releaseAttachment(Long ownerUserId, String objectKey) {
        RecordImageObject object = recordImageObjectRepository
                .findByObjectKeyForUpdate(objectKey)
                .orElseThrow(() -> new GeneralException(
                        RecordImageErrorCode.INVALID_IMAGE_KEY));
        if (!ownerUserId.equals(object.getOwnerUserId())
                || object.getStatus() != RecordImageObjectStatus.ATTACHED) {
            throw new GeneralException(RecordImageErrorCode.INVALID_IMAGE_KEY);
        }
        object.markPending();
    }

    private void registerLegacyAttachment(Long ownerUserId, String objectKey) {
        try {
            recordImageObjectRepository.saveAndFlush(RecordImageObject.attached(ownerUserId, objectKey));
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_NOT_FOUND);
        }
    }

    private Optional<String> claimLegacyObject(String objectKey) {
        Long ownerUserId = parseOwnerUserId(objectKey);
        if (ownerUserId == null) {
            return Optional.empty();
        }
        String claimToken = UUID.randomUUID().toString();
        recordImageObjectRepository.saveAndFlush(
                RecordImageObject.cleanupClaimed(
                        ownerUserId,
                        objectKey,
                        claimToken,
                        clock.instant()
                )
        );
        return Optional.of(claimToken);
    }

    private Long parseOwnerUserId(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(OBJECT_KEY_ROOT_PREFIX)) {
            return null;
        }
        int ownerEnd = objectKey.indexOf('/', OBJECT_KEY_ROOT_PREFIX.length());
        if (ownerEnd < 0) {
            return null;
        }
        try {
            long ownerUserId = Long.parseLong(objectKey.substring(
                    OBJECT_KEY_ROOT_PREFIX.length(),
                    ownerEnd
            ));
            return ownerUserId > 0 ? ownerUserId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
