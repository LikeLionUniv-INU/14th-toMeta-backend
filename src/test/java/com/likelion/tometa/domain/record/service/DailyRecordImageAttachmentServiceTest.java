package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRecordImageAttachmentServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private DailyRecordImageRepository dailyRecordImageRepository;
    @Mock
    private RecordImageOwnershipService recordImageOwnershipService;

    private DailyRecordImageAttachmentService service;
    private User user;
    private DailyRecord dailyRecord;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                "test-bucket",
                "ap-northeast-2",
                10,
                60,
                10_485_760
        );
        service = new DailyRecordImageAttachmentService(
                s3Client,
                properties,
                dailyRecordImageRepository,
                recordImageOwnershipService
        );
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        dailyRecord = DailyRecord.builder().user(user).build();
    }

    @Test
    void attach_savesImagesInRequestOrder() {
        String firstKey = "skin-images/1/first.jpg";
        String secondKey = "skin-images/1/second.jpg";
        List<String> keys = List.of(firstKey, secondKey);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenAnswer(invocation -> {
                    HeadObjectRequest request = invocation.getArgument(0);
                    long size = request.key().contains("first") ? 100L : 200L;
                    return HeadObjectResponse.builder()
                            .contentType("image/jpeg")
                            .contentLength(size)
                            .build();
                });

        service.attach(dailyRecord, user, keys);

        ArgumentCaptor<Iterable<DailyRecordImage>> captor = iterableCaptor();
        verify(dailyRecordImageRepository).saveAllAndFlush(captor.capture());
        List<DailyRecordImage> images = StreamSupport
                .stream(captor.getValue().spliterator(), false)
                .toList();
        assertEquals(List.of(firstKey, secondKey), images.stream()
                .map(DailyRecordImage::getObjectKey)
                .toList());
        assertEquals(List.of(1, 2), images.stream()
                .map(DailyRecordImage::getSortOrder)
                .toList());
        verify(recordImageOwnershipService).claimForAttachment(user.getId(), keys);
    }

    @Test
    void attach_rejectsMoreThanFiveImages() {
        List<String> keys = List.of("1", "2", "3", "4", "5", "6");

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, keys)
        );

        assertSame(RecordImageErrorCode.INVALID_IMAGE_COUNT, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void attach_rejectsImageKeyFromAnotherUserPath() {
        List<String> keys = List.of("skin-images/2/image.jpg");

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, keys)
        );

        assertSame(RecordImageErrorCode.INVALID_IMAGE_KEY, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void attach_rejectsNullContentType() {
        String key = "skin-images/1/image.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentLength(100L)
                        .build());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key))
        );

        assertSame(RecordImageErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
        verify(dailyRecordImageRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void attach_rejectsUnsupportedContentType() {
        String key = "skin-images/1/image.gif";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/gif")
                        .contentLength(100L)
                        .build());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key))
        );

        assertSame(RecordImageErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
    }

    @Test
    void attach_rejectsImageLargerThanUploadLimit() {
        String key = "skin-images/1/large.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(10_485_761L)
                        .build());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key))
        );

        assertSame(RecordImageErrorCode.IMAGE_SIZE_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void attach_rejectsZeroByteImageAsInvalidSize() {
        String key = "skin-images/1/empty.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(0L)
                        .build());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key))
        );

        assertSame(RecordImageErrorCode.IMAGE_SIZE_EXCEEDED, exception.getErrorCode());
        verify(dailyRecordImageRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void attach_rejectsDuplicateImageKeysBeforeS3Request() {
        String key = "skin-images/1/image.jpg";

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key, key))
        );

        assertSame(RecordImageErrorCode.INVALID_IMAGE_KEY, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(recordImageOwnershipService, never()).claimForAttachment(any(), any());
    }

    @Test
    void attach_rejectsAlreadyUsedImageDuringOwnershipClaim() {
        List<String> keys = List.of("skin-images/1/image.jpg");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(100L)
                        .build());
        doThrow(new GeneralException(RecordImageErrorCode.IMAGE_ALREADY_USED))
                .when(recordImageOwnershipService)
                .claimForAttachment(user.getId(), keys);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, keys)
        );

        assertSame(RecordImageErrorCode.IMAGE_ALREADY_USED, exception.getErrorCode());
        verify(dailyRecordImageRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void attach_returnsNotFoundWhenS3ObjectDoesNotExist() {
        String key = "skin-images/1/missing.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key))
        );

        assertSame(RecordImageErrorCode.IMAGE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void attach_acceptsExactlyFiveImages() {
        List<String> keys = List.of(
                "skin-images/1/1.jpg",
                "skin-images/1/2.jpg",
                "skin-images/1/3.jpg",
                "skin-images/1/4.jpg",
                "skin-images/1/5.jpg"
        );
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(100L)
                        .build());

        service.attach(dailyRecord, user, keys);

        verify(s3Client, times(5)).headObject(any(HeadObjectRequest.class));
        verify(dailyRecordImageRepository).saveAllAndFlush(any());
    }

    @Test
    void attach_rejectsPathTraversalBeforeS3Request() {
        String key = "skin-images/1/../2/image.jpg";

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.attach(dailyRecord, user, List.of(key))
        );

        assertSame(RecordImageErrorCode.INVALID_IMAGE_KEY, exception.getErrorCode());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void replace_preservesRetainedMetadataAndTransitionsAddedAndRemovedKeys() {
        String retainedKey = "skin-images/1/retained.jpg";
        String removedKey = "skin-images/1/removed.jpg";
        String addedKey = "skin-images/1/added.jpg";
        DailyRecordImage retained = DailyRecordImage.builder()
                .dailyRecord(dailyRecord)
                .objectKey(retainedKey)
                .mimeType("image/webp")
                .fileSize(123L)
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(retained, "id", 101L);
        LocalDateTime retainedCreatedAt = LocalDateTime.of(2026, 8, 12, 10, 30);
        ReflectionTestUtils.setField(retained, "createdAt", retainedCreatedAt);
        DailyRecordImage removed = DailyRecordImage.builder()
                .dailyRecord(dailyRecord)
                .objectKey(removedKey)
                .mimeType("image/jpeg")
                .fileSize(456L)
                .sortOrder(2)
                .build();
        when(dailyRecordImageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(dailyRecord))
                .thenReturn(List.of(retained, removed));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/png")
                        .contentLength(789L)
                        .build());

        service.replace(
                dailyRecord,
                user,
                List.of(addedKey, retainedKey)
        );

        verify(recordImageOwnershipService).replaceAttachments(
                1L,
                List.of(removedKey),
                List.of(addedKey)
        );
        verify(dailyRecordImageRepository).deleteAll(List.of(removed));
        ArgumentCaptor<Iterable<DailyRecordImage>> captor = iterableCaptor();
        verify(dailyRecordImageRepository).saveAllAndFlush(captor.capture());
        List<DailyRecordImage> replacements = StreamSupport
                .stream(captor.getValue().spliterator(), false)
                .toList();
        assertEquals(List.of(addedKey, retainedKey), replacements.stream()
                .map(DailyRecordImage::getObjectKey)
                .toList());
        assertEquals(List.of(789L, 123L), replacements.stream()
                .map(DailyRecordImage::getFileSize)
                .toList());
        assertEquals(List.of(1, 2), replacements.stream()
                .map(DailyRecordImage::getSortOrder)
                .toList());
        assertSame(retained, replacements.get(1));
        assertEquals(101L, replacements.get(1).getId());
        assertEquals(retainedCreatedAt, replacements.get(1).getCreatedAt());
        verify(s3Client, times(1)).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void replace_withEmptyListRemovesAllImagesWithoutS3Lookup() {
        String firstKey = "skin-images/1/first.jpg";
        String secondKey = "skin-images/1/second.jpg";
        DailyRecordImage first = DailyRecordImage.builder()
                .dailyRecord(dailyRecord)
                .objectKey(firstKey)
                .mimeType("image/jpeg")
                .fileSize(100L)
                .sortOrder(1)
                .build();
        DailyRecordImage second = DailyRecordImage.builder()
                .dailyRecord(dailyRecord)
                .objectKey(secondKey)
                .mimeType("image/png")
                .fileSize(200L)
                .sortOrder(2)
                .build();
        when(dailyRecordImageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(dailyRecord))
                .thenReturn(List.of(first, second));

        service.replace(dailyRecord, user, List.of());

        verify(recordImageOwnershipService).replaceAttachments(
                1L,
                List.of(firstKey, secondKey),
                List.of()
        );
        verify(dailyRecordImageRepository).deleteAll(List.of(first, second));
        verify(dailyRecordImageRepository).saveAllAndFlush(List.of());
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void replace_rejectsNullImageKeysAtPublicBoundary() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> service.replace(dailyRecord, user, null)
        );

        assertEquals("imageKeys", exception.getMessage());
        verify(dailyRecordImageRepository, never())
                .findAllByDailyRecordOrderBySortOrderAsc(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Iterable<DailyRecordImage>> iterableCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
    }
}
