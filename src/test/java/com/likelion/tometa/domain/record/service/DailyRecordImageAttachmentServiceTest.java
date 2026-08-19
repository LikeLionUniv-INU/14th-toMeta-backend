package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.global.config.S3StorageProperties;
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

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRecordImageAttachmentServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private DailyRecordImageRepository dailyRecordImageRepository;

    private DailyRecordImageAttachmentService service;
    private User user;
    private DailyRecord dailyRecord;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                "test-bucket",
                "ap-northeast-2",
                10,
                10_485_760
        );
        service = new DailyRecordImageAttachmentService(
                s3Client,
                properties,
                dailyRecordImageRepository
        );
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        dailyRecord = DailyRecord.builder().user(user).build();
    }

    @Test
    void attach_savesImagesInRequestOrderAndMarksUploadsReferenced() {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Iterable<DailyRecordImage>> iterableCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
    }
}
