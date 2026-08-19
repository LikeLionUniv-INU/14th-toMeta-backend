package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.global.config.S3OrphanCleanupProperties;
import com.likelion.tometa.global.config.S3StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordImageOrphanCleanupServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private DailyRecordImageRepository dailyRecordImageRepository;

    private RecordImageOrphanCleanupService service;

    @BeforeEach
    void setUp() {
        S3StorageProperties storageProperties = new S3StorageProperties(
                "test-bucket",
                "ap-northeast-2",
                10,
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
                dailyRecordImageRepository
        );
    }

    @Test
    void cleanupOrphanImages_deletesOnlyExpiredUnreferencedImages() {
        Instant now = Instant.now();
        S3Object orphan = object("skin-images/1/orphan.jpg", now.minus(Duration.ofHours(25)));
        S3Object referenced = object("skin-images/1/referenced.jpg", now.minus(Duration.ofHours(25)));
        S3Object recent = object("skin-images/1/recent.jpg", now.minus(Duration.ofHours(23)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, orphan, referenced, recent));
        when(dailyRecordImageRepository.findReferencedObjectKeys(List.of(orphan.key(), referenced.key())))
                .thenReturn(List.of(referenced.key()));

        service.cleanupOrphanImages();

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals(orphan.key(), captor.getValue().key());
    }

    @Test
    void cleanupOrphanImages_skipsRepositoryLookupWhenNoImagePassedRetention() {
        S3Object recent = object("skin-images/1/recent.jpg", Instant.now().minus(Duration.ofHours(1)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, recent));

        service.cleanupOrphanImages();

        verify(dailyRecordImageRepository, never()).findReferencedObjectKeys(any());
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupOrphanImages_processesEveryS3Page() {
        S3Object first = object("skin-images/1/first.jpg", Instant.now().minus(Duration.ofDays(2)));
        S3Object second = object("skin-images/1/second.jpg", Instant.now().minus(Duration.ofDays(2)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(
                        page(true, "next-page", first),
                        page(false, null, second)
                );
        when(dailyRecordImageRepository.findReferencedObjectKeys(any())).thenReturn(List.of());

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
    void cleanupOrphanImages_continuesWhenOneDeletionFails() {
        S3Object first = object("skin-images/1/first.jpg", Instant.now().minus(Duration.ofDays(2)));
        S3Object second = object("skin-images/1/second.jpg", Instant.now().minus(Duration.ofDays(2)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page(false, null, first, second));
        when(dailyRecordImageRepository.findReferencedObjectKeys(any())).thenReturn(List.of());
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).build())
                .thenReturn(null);

        service.cleanupOrphanImages();

        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    private S3Object object(String key, Instant lastModified) {
        return S3Object.builder()
                .key(key)
                .lastModified(lastModified)
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
