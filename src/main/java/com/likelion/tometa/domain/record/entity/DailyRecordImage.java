package com.likelion.tometa.domain.record.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "daily_record_images",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_record_images_object_key",
                        columnNames = "object_key"
                ),
                @UniqueConstraint(
                        name = "uk_daily_record_images_record_sort",
                        columnNames = {"daily_record_id", "sort_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecordImage extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_record_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_record_id", nullable = false)
    private DailyRecord dailyRecord;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private DailyRecordImage(
            DailyRecord dailyRecord,
            String objectKey,
            String mimeType,
            Long fileSize,
            int sortOrder
    ) {
        this.dailyRecord = dailyRecord;
        this.objectKey = objectKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.sortOrder = sortOrder;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
