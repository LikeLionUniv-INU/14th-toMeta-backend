package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.dto.request.RecordImageUploadUrlRequestDto;
import com.likelion.tometa.domain.record.dto.response.RecordImageUploadUrlResponseDto;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.ALLOWED_CONTENT_TYPES;
import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.MAX_IMAGE_COUNT;
import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.objectKeyPrefix;

@Service
@RequiredArgsConstructor
public class RecordImageStorageService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;
    private final RecordImageOwnershipService recordImageOwnershipService;

    public RecordImageUploadUrlResponseDto issueUploadUrl(
            RecordImageUploadUrlRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        validateImageCount(request.images());
        request.images().forEach(image -> {
            validateContentType(image.contentType());
            validateFileSize(image.fileSize());
        });

        Duration expiration = Duration.ofMinutes(properties.presignedUploadExpirationMinutes());
        Instant expiresAt = Instant.now().plus(expiration);

        List<RecordImageUploadUrlResponseDto.UploadInfo> uploads = request.images().stream()
                .map(image -> createPresignedUploadUrl(user, image, expiration, expiresAt))
                .toList();
        recordImageOwnershipService.registerPending(
                user.getId(),
                uploads.stream()
                        .map(RecordImageUploadUrlResponseDto.UploadInfo::objectKey)
                        .toList()
        );

        return new RecordImageUploadUrlResponseDto(uploads);
    }

    private RecordImageUploadUrlResponseDto.UploadInfo createPresignedUploadUrl(
            User user,
            RecordImageUploadUrlRequestDto.ImageUploadRequest image,
            Duration expiration,
            Instant expiresAt
    ) {
        String objectKey = createObjectKey(user.getId(), image.contentType());

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .contentType(image.contentType())
                    .contentLength(image.fileSize())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest =
                    s3Presigner.presignPutObject(presignRequest);

            return new RecordImageUploadUrlResponseDto.UploadInfo(
                    presignedRequest.url().toString(),
                    objectKey,
                    "PUT",
                    image.contentType(),
                    expiresAt
            );
        } catch (SdkException e) {
            throw new GeneralException(
                    RecordImageErrorCode.PRESIGNED_URL_ISSUE_FAILED
            );
        }
    }

    private void validateImageCount(List<RecordImageUploadUrlRequestDto.ImageUploadRequest> images) {
        if (images == null || images.isEmpty() || images.size() > MAX_IMAGE_COUNT) {
            throw new GeneralException(RecordImageErrorCode.INVALID_IMAGE_COUNT);
        }
    }

    private void validateContentType(String contentType) {
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new GeneralException(RecordImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private void validateFileSize(long fileSize) {
        if (fileSize <= 0 || fileSize > properties.maxUploadSizeBytes()) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_SIZE_EXCEEDED);
        }
    }

    private String createObjectKey(Long userId, String contentType) {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        String extension = EXTENSIONS.get(contentType);

        return "%s%04d/%02d/%02d/%s.%s".formatted(
                objectKeyPrefix(userId),
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

}
