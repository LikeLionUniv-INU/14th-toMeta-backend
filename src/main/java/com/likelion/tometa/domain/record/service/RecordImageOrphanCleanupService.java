package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.global.config.S3OrphanCleanupProperties;
import com.likelion.tometa.global.config.S3StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.OBJECT_KEY_ROOT_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordImageOrphanCleanupService {

    private final S3Client s3Client;
    private final S3StorageProperties storageProperties;
    private final S3OrphanCleanupProperties cleanupProperties;
    private final DailyRecordImageRepository dailyRecordImageRepository;

    @Scheduled(
            cron = "${app.storage.s3.orphan-cleanup.cron}",
            zone = "${app.storage.s3.orphan-cleanup.zone}"
    )
    public void cleanupOrphanImages() {
        try {
            CleanupResult result = cleanup(Instant.now().minus(cleanupProperties.retention()));
            log.info(
                    "Orphan image cleanup completed. scanned: {}, eligible: {}, referenced: {}, deleted: {}, failed: {}",
                    result.scanned(),
                    result.eligible(),
                    result.referenced(),
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
        int referenced = 0;
        int deleted = 0;
        int failed = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Response response = listObjects(continuationToken);
            List<S3Object> objects = response.contents();
            scanned += objects.size();

            List<String> candidateKeys = objects.stream()
                    .filter(object -> isCleanupCandidate(object, cutoff))
                    .map(S3Object::key)
                    .toList();
            eligible += candidateKeys.size();

            Set<String> referencedKeys = findReferencedKeys(candidateKeys);
            referenced += referencedKeys.size();

            for (String objectKey : candidateKeys) {
                if (referencedKeys.contains(objectKey)) {
                    continue;
                }

                try {
                    deleteObject(objectKey);
                    deleted++;
                } catch (SdkException e) {
                    failed++;
                    log.warn("Failed to delete orphan image. objectKey: {}", objectKey, e);
                }
            }

            continuationToken = nextContinuationToken(response);
        } while (continuationToken != null);

        return new CleanupResult(scanned, eligible, referenced, deleted, failed);
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
                && object.lastModified().isBefore(cutoff);
    }

    private Set<String> findReferencedKeys(List<String> candidateKeys) {
        if (candidateKeys.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(dailyRecordImageRepository.findReferencedObjectKeys(candidateKeys));
    }

    private void deleteObject(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(storageProperties.bucket())
                .key(objectKey)
                .build());
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
            int referenced,
            int deleted,
            int failed
    ) {
    }
}
