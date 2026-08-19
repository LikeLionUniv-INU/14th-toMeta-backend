package com.likelion.tometa.domain.record.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "daily_record_cosmetics",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_record_cosmetics_user_period",
                        columnNames = {"daily_record_id", "user_cosmetic_id", "usage_period"}
                ),
                @UniqueConstraint(
                        name = "uk_daily_record_cosmetics_sort",
                        columnNames = {"daily_record_id", "usage_period", "sort_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecordCosmetic extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_record_cosmetic_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_record_id", nullable = false)
    private DailyRecord dailyRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_cosmetic_id", nullable = false)
    private UserCosmetic userCosmetic;

    @Column(name = "usage_period", nullable = false, length = 10)
    private String usagePeriod;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(name = "brand_name_snapshot", length = 100)
    private String brandNameSnapshot;

    @Column(name = "product_type_snapshot", nullable = false, length = 50)
    private String productTypeSnapshot;

    @Column(name = "custom_name_snapshot", length = 100)
    private String customNameSnapshot;

    @Column(name = "ingredients_snapshot", columnDefinition = "json")
    private String ingredientsSnapshot;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private DailyRecordCosmetic(
            DailyRecord dailyRecord,
            UserCosmetic userCosmetic,
            String usagePeriod,
            String productNameSnapshot,
            String brandNameSnapshot,
            String productTypeSnapshot,
            String customNameSnapshot,
            String ingredientsSnapshot,
            int sortOrder
    ) {
        this.dailyRecord = dailyRecord;
        this.userCosmetic = userCosmetic;
        this.usagePeriod = usagePeriod;
        this.productNameSnapshot = productNameSnapshot;
        this.brandNameSnapshot = brandNameSnapshot;
        this.productTypeSnapshot = productTypeSnapshot;
        this.customNameSnapshot = customNameSnapshot;
        this.ingredientsSnapshot = ingredientsSnapshot;
        this.sortOrder = sortOrder;
    }
}
