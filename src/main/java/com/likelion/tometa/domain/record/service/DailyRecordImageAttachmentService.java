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
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final RecordImageOwnershipService recordImageOwnershipService;

    public void attach(DailyRecord dailyRecord, User user, List<String> imageKeys) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return;
        }

        validateKeys(user, imageKeys);

        List<DailyRecordImage> images = IntStream.range(0, imageKeys.size())
                .mapToObj(index -> createImage(
                        dailyRecord,
                        imageKeys.get(index),
                        index + 1
                ))
                .toList();
        recordImageOwnershipService.claimForAttachment(user.getId(), imageKeys);

        try {
            dailyRecordImageRepository.saveAllAndFlush(images);
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(RecordImageErrorCode.IMAGE_ALREADY_USED);
        }

    }

    @Transactional
    public void replace(DailyRecord dailyRecord, User user, List<String> imageKeys) {
        Objects.requireNonNull(imageKeys, "imageKeys");
        validateKeys(user, imageKeys);

        List<DailyRecordImage> existingImages = dailyRecordImageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(dailyRecord);
        Map<String, DailyRecordImage> existingByKey = existingImages.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyRecordImage::getObjectKey,
                        image -> image,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<String> removedKeys = existingImages.stream()
                .map(DailyRecordImage::getObjectKey)
                .filter(key -> !imageKeys.contains(key))
                .toList();
        List<String> addedKeys = imageKeys.stream()
                .filter(key -> !existingByKey.containsKey(key))
                .toList();

        Map<String, DailyRecordImage> addedByKey = addedKeys.stream()
                .collect(java.util.stream.Collectors.toMap(
                        key -> key,
                        key -> createImage(dailyRecord, key, 1),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<DailyRecordImage> retainedImages = imageKeys.stream()
                .map(existingByKey::get)
                .filter(Objects::nonNull)
                .toList();

        recordImageOwnershipService.replaceAttachments(
                user.getId(),
                removedKeys,
                addedKeys
        );
        for (int index = 0; index < retainedImages.size(); index++) {
            retainedImages.get(index).updateSortOrder(MAX_IMAGE_COUNT + index + 1);
        }
        if (!retainedImages.isEmpty()) {
            dailyRecordImageRepository.flush();
        }

        List<DailyRecordImage> removedImages = removedKeys.stream()
                .map(existingByKey::get)
                .toList();
        if (!removedImages.isEmpty()) {
            dailyRecordImageRepository.deleteAll(removedImages);
            dailyRecordImageRepository.flush();
        }

        List<DailyRecordImage> finalImages = IntStream.range(0, imageKeys.size())
                .mapToObj(index -> {
                    String key = imageKeys.get(index);
                    DailyRecordImage image = existingByKey.getOrDefault(
                            key,
                            addedByKey.get(key)
                    );
                    image.updateSortOrder(index + 1);
                    return image;
                })
                .toList();
        dailyRecordImageRepository.saveAllAndFlush(finalImages);
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
