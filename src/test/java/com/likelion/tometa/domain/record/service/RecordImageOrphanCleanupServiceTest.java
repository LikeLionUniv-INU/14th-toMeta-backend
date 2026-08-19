package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.global.config.s3.S3OrphanCleanupProperties;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordImageOrphanCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");

    @Mock
    private S3Client s3Client;
    @Mock
    private RecordImageOwnershipService recordImageOwnershipService;

    private RecordImageOrphanCleanupService service;

    @BeforeEach
    void setUp() {
        S3StorageProperties storageProperties = new S3StorageProperties(
                "test-bucket",
                "ap-northeast-2",
                10,
                60,
                10_485_760
        );
        S3OrphanCleanupProperties cleanupProperties = new S3OrphanCleanupProperties(
                Duration.ofHours(24),
                2,
                "0 0 * * * *",
                "Asia/Seoul"
        );
        service = new RecordImageOrphanCleanupService(
                s3Client,
                storageProperties,
                cleanupProperties,
                recordImageOwnershipService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void cleanupOrphanImages_deletesOnlyExpiredClaimedImages() {
        S3Object orphan = object("skin-images/1/orphan.jpg", NOW.minus(Duration.ofHours(25)));
        S3Object attached = object("skin-images/1/attached.jpg", NOW.minus(Duration.ofHours(25)));
        S3Object recent = object("skin-images/1/recent.jpg", NOW.minus(Duration.ofHours(23)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, orphan, attached, recent));
        when(recordImageOwnershipService.claimForCleanup(orphan.key()))
                .thenReturn(Optional.of("orphan-claim"));
        when(recordImageOwnershipService.claimForCleanup(attached.key()))
                .thenReturn(Optional.empty());

        service.cleanupOrphanImages();

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        DeleteObjectRequest request = captor.getValue();
        assertEquals(orphan.key(), request.key());
        assertEquals(orphan.eTag(), request.ifMatch());
        assertNull(request.ifMatchLastModifiedTime());
        assertNull(request.ifMatchSize());
        verify(recordImageOwnershipService).markDeleted(orphan.key(), "orphan-claim");
        verify(recordImageOwnershipService, never()).claimForCleanup(recent.key());
    }

    @Test
    void cleanupOrphanImages_includesImageAtExactRetentionBoundary() {
        S3Object boundary = object("skin-images/1/boundary.jpg", NOW.minus(Duration.ofHours(24)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, boundary));
        when(recordImageOwnershipService.claimForCleanup(boundary.key()))
                .thenReturn(Optional.of("boundary-claim"));

        service.cleanupOrphanImages();

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(recordImageOwnershipService).markDeleted(boundary.key(), "boundary-claim");
    }

    @Test
    void cleanupOrphanImages_skipsOwnershipClaimWhenNoImagePassedRetention() {
        S3Object recent = object("skin-images/1/recent.jpg", NOW.minus(Duration.ofHours(1)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, recent));

        service.cleanupOrphanImages();

        verify(recordImageOwnershipService, never()).claimForCleanup(any());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupOrphanImages_skipsImageWithoutEtag() {
        S3Object imageWithoutEtag = S3Object.builder()
                .key("skin-images/1/no-etag.jpg")
                .lastModified(NOW.minus(Duration.ofDays(2)))
                .size(100L)
                .build();
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, imageWithoutEtag));

        service.cleanupOrphanImages();

        verify(recordImageOwnershipService, never()).claimForCleanup(any());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupOrphanImages_treatsConcurrentOwnershipInsertAsProtected() {
        S3Object candidate = object("skin-images/1/concurrent.jpg", NOW.minus(Duration.ofDays(2)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, candidate));
        when(recordImageOwnershipService.claimForCleanup(candidate.key()))
                .thenThrow(new DataIntegrityViolationException("concurrent ownership insert"));

        service.cleanupOrphanImages();

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(recordImageOwnershipService, never()).markDeleted(any(), any());
    }

    @Test
    void cleanupOrphanImages_recoversFinalizationAfterObjectWasDeleted() {
        String objectKey = "skin-images/1/deleted-before-finalization.jpg";
        S3Object candidate = object(objectKey, NOW.minus(Duration.ofDays(2)));
        when(recordImageOwnershipService.findRecoverableCleanupKeys(2))
                .thenReturn(List.of())
                .thenReturn(List.of(objectKey));
        when(recordImageOwnershipService.claimForCleanup(objectKey))
                .thenReturn(Optional.of("initial-claim"))
                .thenReturn(Optional.of("recovery-claim"));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(
                        page(false, null, candidate),
                        page(false, null)
                );
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        doThrow(new IllegalStateException("temporary database failure"))
                .doNothing()
                .when(recordImageOwnershipService)
                .markDeleted(anyString(), anyString());

        service.cleanupOrphanImages();
        service.cleanupOrphanImages();

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        InOrder finalizationOrder = inOrder(recordImageOwnershipService);
        finalizationOrder.verify(recordImageOwnershipService)
                .markDeleted(objectKey, "initial-claim");
        finalizationOrder.verify(recordImageOwnershipService)
                .markDeleted(objectKey, "recovery-claim");
    }

    @Test
    void cleanupOrphanImages_processesEveryS3Page() {
        S3Object first = object("skin-images/1/first.jpg", NOW.minus(Duration.ofDays(2)));
        S3Object second = object("skin-images/1/second.jpg", NOW.minus(Duration.ofDays(2)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(
                        page(true, "next-page", first),
                        page(false, null, second)
                );
        when(recordImageOwnershipService.claimForCleanup(any()))
                .thenReturn(Optional.of("page-claim"));

        service.cleanupOrphanImages();

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(captor.capture());
        ListObjectsV2Request firstRequest = captor.getAllValues().get(0);
        assertEquals("test-bucket", firstRequest.bucket());
        assertEquals("skin-images/", firstRequest.prefix());
        assertEquals(2, firstRequest.maxKeys());
        assertNull(firstRequest.continuationToken());
        assertEquals("next-page", captor.getAllValues().get(1).continuationToken());
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupOrphanImages_releasesClaimAndContinuesWhenDeletionFails() {
        S3Object first = object("skin-images/1/first.jpg", NOW.minus(Duration.ofDays(2)));
        S3Object second = object("skin-images/1/second.jpg", NOW.minus(Duration.ofDays(2)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, first, second));
        when(recordImageOwnershipService.claimForCleanup(first.key()))
                .thenReturn(Optional.of("first-claim"));
        when(recordImageOwnershipService.claimForCleanup(second.key()))
                .thenReturn(Optional.of("second-claim"));
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).build())
                .thenReturn(null);

        service.cleanupOrphanImages();

        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        verify(recordImageOwnershipService).releaseCleanupClaim(first.key(), "first-claim");
        verify(recordImageOwnershipService).markDeleted(second.key(), "second-claim");
    }

    private S3Object object(String key, Instant lastModified) {
        return S3Object.builder()
                .key(key)
                .lastModified(lastModified)
                .eTag("etag-" + key)
                .size(100L)
                .build();
    }

    private ListObjectsV2Response page(
            boolean truncated,
            String nextContinuationToken,
            S3Object... objects
    ) {
        return ListObjectsV2Response.builder()
                .contents(objects)
                .isTruncated(truncated)
                .nextContinuationToken(nextContinuationToken)
                .build();
    }
}
