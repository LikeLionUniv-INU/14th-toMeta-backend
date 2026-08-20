package com.likelion.tometa.domain.record.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "daily_record_cosmetic_set_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_record_set_items_cosmetic",
                        columnNames = {"daily_record_cosmetic_set_id", "user_cosmetic_id"}
                ),
                @UniqueConstraint(
                        name = "uk_daily_record_set_items_sort",
                        columnNames = {"daily_record_cosmetic_set_id", "sort_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecordCosmeticSetItem extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_record_cosmetic_set_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_record_cosmetic_set_id", nullable = false)
    private DailyRecordCosmeticSet dailyRecordCosmeticSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_cosmetic_id", nullable = false)
    private UserCosmetic userCosmetic;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private DailyRecordCosmeticSetItem(
            DailyRecordCosmeticSet dailyRecordCosmeticSet,
            UserCosmetic userCosmetic,
            int sortOrder
    ) {
        this.dailyRecordCosmeticSet = dailyRecordCosmeticSet;
        this.userCosmetic = userCosmetic;
        this.sortOrder = sortOrder;
    }
}
