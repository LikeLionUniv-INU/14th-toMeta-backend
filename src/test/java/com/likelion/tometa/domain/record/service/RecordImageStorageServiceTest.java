package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.dto.request.RecordImageUploadUrlRequestDto;
import com.likelion.tometa.domain.record.dto.response.RecordImageUploadUrlResponseDto;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.config.S3StorageProperties;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordImageStorageServiceTest {

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private PresignedPutObjectRequest presignedRequest;

    private RecordImageStorageService service;
    private User user;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                "test-bucket",
                "ap-northeast-2",
                10,
                10_485_760
        );
        service = new RecordImageStorageService(
                sessionUserResolver,
                s3Presigner,
                properties
        );
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(sessionUserResolver.resolve("session-token")).thenReturn(user);
    }

    @Test
    void issueUploadUrl_returnsUploadInformation() throws Exception {
        RecordImageUploadUrlRequestDto request = new RecordImageUploadUrlRequestDto(List.of(
                new RecordImageUploadUrlRequestDto.ImageUploadRequest("image/jpeg", 100L),
                new RecordImageUploadUrlRequestDto.ImageUploadRequest("image/png", 200L)
        ));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(URI.create("https://example.com/upload").toURL());

        RecordImageUploadUrlResponseDto result = service.issueUploadUrl(
                request,
                "session-token"
        );

        assertEquals(2, result.uploads().size());
        assertEquals(List.of("image/jpeg", "image/png"), result.uploads().stream()
                .map(RecordImageUploadUrlResponseDto.UploadInfo::contentType)
                .toList());
        result.uploads().forEach(upload ->
                assertTrue(upload.objectKey().startsWith("skin-images/1/")));
    }

    @Test
    void issueUploadUrl_rejectsMoreThanFiveImages() {
        RecordImageUploadUrlRequestDto.ImageUploadRequest image =
                new RecordImageUploadUrlRequestDto.ImageUploadRequest("image/jpeg", 100L);
        RecordImageUploadUrlRequestDto request = new RecordImageUploadUrlRequestDto(
                List.of(image, image, image, image, image, image)
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.issueUploadUrl(request, "session-token")
        );

        assertSame(RecordImageErrorCode.INVALID_IMAGE_COUNT, exception.getErrorCode());
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }
}
