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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
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
            CleanupResult result = cleanup(clock.instant().minus(cleanupProperties.retention()));
            log.info(
                    "Orphan image cleanup completed. scanned: {}, eligible: {}, protected: {}, deleted: {}, failed: {}",
                    result.scanned(),
                    result.eligible(),
                    result.protectedCount(),
                    result.deleted(),
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
                    recordImageOwnershipService.markDeleted(candidate.key(), claimToken.get());
                    deleted++;
                } catch (SdkException e) {
                    failed++;
                    releaseClaim(candidate.key(), claimToken.get());
                    log.atWarn()
                            .setCause(e)
                            .addArgument(candidate.key())
                            .log("Failed to delete orphan image: {}");
                } catch (RuntimeException e) {
                    failed++;
                    log.atError()
                            .setCause(e)
                            .addArgument(candidate.key())
                            .log("Failed to finalize orphan image deletion: {}");
                }
            }

            continuationToken = nextContinuationToken(response);
        } while (continuationToken != null);

        return new CleanupResult(scanned, eligible, protectedCount, deleted, failed);
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
}
