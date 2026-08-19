package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordImageReadUrlServiceTest {

    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private PresignedGetObjectRequest presignedRequest;

    private RecordImageReadUrlService service;

    @BeforeEach
    void setUp() {
        S3StorageProperties properties = new S3StorageProperties(
                "private-bucket",
                "ap-northeast-2",
                10,
                60,
                10_485_760
        );
        service = new RecordImageReadUrlService(s3Presigner, properties);
    }

    @Test
    void issueReadUrl_presignsPrivateObjectForOneHour() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(URI.create("https://signed.example/image.jpg").toURL());

        String result = service.issueReadUrl("skin-images/1/image.jpg");

        assertEquals("https://signed.example/image.jpg", result);
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertEquals(Duration.ofHours(1), captor.getValue().signatureDuration());
        assertEquals("private-bucket", captor.getValue().getObjectRequest().bucket());
        assertEquals("skin-images/1/image.jpg", captor.getValue().getObjectRequest().key());
    }

    @Test
    void issueReadUrl_mapsSdkFailureToImageError() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("failed"));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.issueReadUrl("skin-images/1/image.jpg")
        );

        assertSame(
                RecordImageErrorCode.PRESIGNED_DOWNLOAD_URL_ISSUE_FAILED,
                exception.getErrorCode()
        );
    }
}
