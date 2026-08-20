package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class DailyRecordImageReplacementIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DailyRecordRepository dailyRecordRepository;
    @Autowired
    private DailyRecordImageRepository imageRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void replace_reordersRetainedImagesWithoutChangingIdentityOrCreatedAt() {
        User user = userRepository.save(User.builder().build());
        DailyRecord record = dailyRecordRepository.save(DailyRecord.builder()
                .user(user)
                .recordDate(LocalDate.of(2026, 8, 12))
                .skinStatus("normal")
                .build());
        String firstKey = "skin-images/%d/first.jpg".formatted(user.getId());
        String secondKey = "skin-images/%d/second.jpg".formatted(user.getId());
        imageRepository.saveAllAndFlush(List.of(
                image(record, firstKey, 1),
                image(record, secondKey, 2)
        ));
        entityManager.clear();
        DailyRecord reloadedRecord = dailyRecordRepository
                .findById(record.getId())
                .orElseThrow();
        Map<String, IdentitySnapshot> before = imageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(reloadedRecord)
                .stream()
                .collect(Collectors.toMap(
                        DailyRecordImage::getObjectKey,
                        image -> new IdentitySnapshot(image.getId(), image.getCreatedAt())
                ));
        S3Client s3Client = mock(S3Client.class);
        RecordImageOwnershipService ownershipService =
                mock(RecordImageOwnershipService.class);
        DailyRecordImageAttachmentService service =
                new DailyRecordImageAttachmentService(
                        s3Client,
                        new S3StorageProperties(
                                "test-bucket",
                                "ap-northeast-2",
                                10,
                                60,
                                10_485_760
                        ),
                        imageRepository,
                        ownershipService
                );
        service.replace(reloadedRecord, user, List.of(secondKey, firstKey));
        entityManager.clear();

        List<DailyRecordImage> reordered = imageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(reloadedRecord);
        assertEquals(List.of(secondKey, firstKey), reordered.stream()
                .map(DailyRecordImage::getObjectKey)
                .toList());
        for (DailyRecordImage image : reordered) {
            IdentitySnapshot original = before.get(image.getObjectKey());
            assertEquals(original.id(), image.getId());
            assertEquals(original.createdAt(), image.getCreatedAt());
        }
        verify(ownershipService).replaceAttachments(
                user.getId(),
                List.of(),
                List.of()
        );
        verify(s3Client, never()).headObject(
                org.mockito.ArgumentMatchers.any(
                        software.amazon.awssdk.services.s3.model.HeadObjectRequest.class
                )
        );
    }

    private DailyRecordImage image(
            DailyRecord record,
            String objectKey,
            int sortOrder
    ) {
        return DailyRecordImage.builder()
                .dailyRecord(record)
                .objectKey(objectKey)
                .mimeType("image/jpeg")
                .fileSize(100L)
                .sortOrder(sortOrder)
                .build();
    }

    private record IdentitySnapshot(Long id, LocalDateTime createdAt) {
    }
}
