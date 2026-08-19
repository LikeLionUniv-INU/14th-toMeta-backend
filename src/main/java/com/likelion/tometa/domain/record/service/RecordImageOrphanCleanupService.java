package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.global.config.s3.S3OrphanCleanupProperties;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.OBJECT_KEY_ROOT_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordImageOrphanCleanupService {

    private final S3Client s3Client;
    private final S3StorageProperties storageProperties;
    private final S3OrphanCleanupProperties cleanupProperties;
    private final RecordImageOwnershipService recordImageOwnershipService;
    private final Clock clock;

    @Scheduled(
            cron = "${app.storage.s3.orphan-cleanup.cron}",
            zone = "${app.storage.s3.orphan-cleanup.zone}"
    )
    public void cleanupOrphanImages() {
        try {
            Instant cutoff = clock.instant().minus(cleanupProperties.retention());
            int recovered = recoverIncompleteDeletions(cutoff);
            CleanupResult result = cleanup(cutoff);
            log.info(
                    "Orphan image cleanup completed. scanned: {}, eligible: {}, protected: {}, deleted: {}, recovered: {}, failed: {}",
                    result.scanned(),
                    result.eligible(),
                    result.protectedCount(),
                    result.deleted(),
                    recovered,
                    result.failed()
            );
        } catch (RuntimeException e) {
            log.error("Orphan image cleanup failed before completion.", e);
        }
    }

    private CleanupResult cleanup(Instant cutoff) {
        int scanned = 0;
        int eligible = 0;
        int protectedCount = 0;
        int deleted = 0;
        int failed = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Response response = listObjects(continuationToken);
            List<S3Object> objects = response.contents();
            scanned += objects.size();

            List<S3Object> candidates = objects.stream()
                    .filter(object -> isCleanupCandidate(object, cutoff))
                    .toList();
            eligible += candidates.size();

            for (S3Object candidate : candidates) {
                Optional<String> claimToken;
                try {
                    claimToken = recordImageOwnershipService.claimForCleanup(candidate.key());
                } catch (DataIntegrityViolationException e) {
                    protectedCount++;
                    continue;
                } catch (RuntimeException e) {
                    failed++;
                    log.atWarn()
                            .setCause(e)
                            .addArgument(candidate.key())
                            .log("Failed to claim orphan image: {}");
                    continue;
                }
                if (claimToken.isEmpty()) {
                    protectedCount++;
                    continue;
                }

                try {
                    deleteObject(candidate);
                    if (finalizeDeletion(candidate.key(), claimToken.get())) {
                        deleted++;
                    } else {
                        failed++;
                    }
                } catch (SdkException e) {
                    failed++;
                    releaseClaim(candidate.key(), claimToken.get());
                    log.atWarn()
                            .setCause(e)
                            .addArgument(candidate.key())
                            .log("Failed to delete orphan image: {}");
                }
            }

            continuationToken = nextContinuationToken(response);
        } while (continuationToken != null);

        return new CleanupResult(scanned, eligible, protectedCount, deleted, failed);
    }

    private int recoverIncompleteDeletions(Instant cutoff) {
        int recovered = 0;
        List<String> objectKeys = recordImageOwnershipService.findRecoverableCleanupKeys(
                cleanupProperties.batchSize()
        );
        for (String objectKey : objectKeys) {
            Optional<String> claimToken;
            try {
                claimToken = recordImageOwnershipService.claimForCleanup(objectKey);
            } catch (RuntimeException e) {
                logRecoveryFailure("Failed to reclaim orphan image", objectKey, e);
                continue;
            }
            if (claimToken.isEmpty()) {
                continue;
            }

            RecoveryObjectLookup lookup = findObjectForRecovery(
                    objectKey,
                    claimToken.get(),
                    cutoff
            );
            if (!lookup.resolvable()) {
                continue;
            }
            if (lookup.object() != null) {
                try {
                    deleteObject(lookup.object());
                } catch (SdkException e) {
                    releaseClaim(objectKey, claimToken.get());
                    logRecoveryFailure("Failed to retry orphan image deletion", objectKey, e);
                    continue;
                }
            }
            if (finalizeDeletion(objectKey, claimToken.get())) {
                recovered++;
            }
        }
        return recovered;
    }

    private RecoveryObjectLookup findObjectForRecovery(
            String objectKey,
            String claimToken,
            Instant cutoff
    ) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.bucket())
                    .key(objectKey)
                    .build());
            S3Object object = S3Object.builder()
                    .key(objectKey)
                    .eTag(response.eTag())
                    .lastModified(response.lastModified())
                    .size(response.contentLength())
                    .build();
            if (!isCleanupCandidate(object, cutoff)) {
                releaseClaim(objectKey, claimToken);
                return RecoveryObjectLookup.unresolvable();
            }
            return RecoveryObjectLookup.found(object);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return RecoveryObjectLookup.missing();
            }
            releaseClaim(objectKey, claimToken);
            logRecoveryFailure("Failed to inspect orphan image recovery state", objectKey, e);
            return RecoveryObjectLookup.unresolvable();
        } catch (SdkException e) {
            releaseClaim(objectKey, claimToken);
            logRecoveryFailure("Failed to inspect orphan image recovery state", objectKey, e);
            return RecoveryObjectLookup.unresolvable();
        }
    }

    private ListObjectsV2Response listObjects(String continuationToken) {
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(storageProperties.bucket())
                .prefix(OBJECT_KEY_ROOT_PREFIX)
                .maxKeys(cleanupProperties.batchSize());
        if (continuationToken != null) {
            requestBuilder.continuationToken(continuationToken);
        }
        return s3Client.listObjectsV2(requestBuilder.build());
    }

    private boolean isCleanupCandidate(S3Object object, Instant cutoff) {
        return object.key() != null
                && !object.key().isBlank()
                && object.lastModified() != null
                && object.eTag() != null
                && !object.eTag().isBlank()
                && !object.lastModified().isAfter(cutoff);
    }

    private void deleteObject(S3Object object) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(object.key())
                .ifMatch(object.eTag())
                .build());
    }

    private void releaseClaim(String objectKey, String claimToken) {
        try {
            recordImageOwnershipService.releaseCleanupClaim(objectKey, claimToken);
        } catch (RuntimeException e) {
            log.atError()
                    .setCause(e)
                    .addArgument(objectKey)
                    .log("Failed to release orphan image cleanup claim: {}");
        }
    }

    private boolean finalizeDeletion(String objectKey, String claimToken) {
        try {
            recordImageOwnershipService.markDeleted(objectKey, claimToken);
            return true;
        } catch (RuntimeException e) {
            logRecoveryFailure("Failed to finalize orphan image deletion", objectKey, e);
            return false;
        }
    }

    private void logRecoveryFailure(String message, String objectKey, Throwable throwable) {
        log.atError()
                .setCause(throwable)
                .addArgument(objectKey)
                .log(message + ": {}");
    }

    private String nextContinuationToken(ListObjectsV2Response response) {
        if (!Boolean.TRUE.equals(response.isTruncated())) {
            return null;
        }
        String nextToken = response.nextContinuationToken();
        return nextToken == null || nextToken.isBlank() ? null : nextToken;
    }

    private record CleanupResult(
            int scanned,
            int eligible,
            int protectedCount,
            int deleted,
            int failed
    ) {
    }

    private record RecoveryObjectLookup(boolean resolvable, S3Object object) {

        private static RecoveryObjectLookup found(S3Object object) {
            return new RecoveryObjectLookup(true, object);
        }

        private static RecoveryObjectLookup missing() {
            return new RecoveryObjectLookup(true, null);
        }

        private static RecoveryObjectLookup unresolvable() {
            return new RecoveryObjectLookup(false, null);
        }
    }
}
