package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.code.RecordImageErrorCode;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.ALLOWED_CONTENT_TYPES;
import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.MAX_IMAGE_COUNT;
import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.objectKeyPrefix;

@Service
@RequiredArgsConstructor
public class DailyRecordImageAttachmentService {

    private final S3Client s3Client;
    private final S3StorageProperties properties;
    private final DailyRecordImageRepository dailyRecordImageRepository;

    public void attach(DailyRecord dailyRecord, User user, List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return;
        }

        validateKeys(user, imageKeys);

        if (dailyRecordImageRepository.existsByObjectKeyIn(imageKeys)) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_ALREADY_USED);
        }

        List<DailyRecordImage> images = IntStream.range(0, imageKeys.size())
                .mapToObj(index -> createImage(
                        dailyRecord,
                        imageKeys.get(index),
                        index + 1
                ))
                .toList();

        try {
            dailyRecordImageRepository.saveAllAndFlush(images);
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_ALREADY_USED);
        }

    }

    private DailyRecordImage createImage(
            DailyRecord dailyRecord,
            String objectKey,
            int sortOrder
    ) {
        HeadObjectResponse object = headObject(objectKey);
        String contentType = object.contentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new GeneralException(RecordImageErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        Long contentLength = object.contentLength();
        if (contentLength == null) {
            throw new GeneralException(RecordImageErrorCode.INVALID_IMAGE_KEY);
        }
        if (contentLength <= 0 || contentLength > properties.maxUploadSizeBytes()) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_SIZE_EXCEEDED);
        }

        return DailyRecordImage.builder()
                .dailyRecord(dailyRecord)
                .objectKey(objectKey)
                .mimeType(contentType)
                .fileSize(contentLength)
                .sortOrder(sortOrder)
                .build();
    }

    private HeadObjectResponse headObject(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new GeneralException(RecordImageErrorCode.IMAGE_NOT_FOUND);
            }
            throw new GeneralException(RecordImageErrorCode.IMAGE_STORAGE_ACCESS_FAILED);
        } catch (SdkException e) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_STORAGE_ACCESS_FAILED);
        }
    }

    private void validateKeys(User user, List<String> imageKeys) {
        if (imageKeys.size() > MAX_IMAGE_COUNT) {
            throw new GeneralException(RecordImageErrorCode.INVALID_IMAGE_COUNT);
        }
        String userPrefix = objectKeyPrefix(user.getId());
        if (imageKeys.stream().anyMatch(key -> key == null
                || key.isBlank()
                || key.contains("..")
                || key.contains("//")
                || !key.startsWith(userPrefix))
                || new HashSet<>(imageKeys).size() != imageKeys.size()) {
            throw new GeneralException(RecordImageErrorCode.INVALID_IMAGE_KEY);
        }
    }
}
