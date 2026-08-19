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
        name = "daily_record_cosmetic_sets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_record_sets_source_period",
                        columnNames = {"daily_record_id", "source_cosmetic_set_id", "usage_period"}
                ),
                @UniqueConstraint(
                        name = "uk_daily_record_sets_sort",
                        columnNames = {"daily_record_id", "usage_period", "sort_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecordCosmeticSet extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_record_cosmetic_set_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_record_id", nullable = false)
    private DailyRecord dailyRecord;

    @Column(name = "source_cosmetic_set_id", nullable = false)
    private Long sourceCosmeticSetId;

    @Column(name = "set_name_snapshot", nullable = false, length = 100)
    private String setNameSnapshot;

    @Column(name = "set_usage_time_snapshot", nullable = false, length = 20)
    private String setUsageTimeSnapshot;

    @Column(name = "usage_period", nullable = false, length = 10)
    private String usagePeriod;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private DailyRecordCosmeticSet(
            DailyRecord dailyRecord,
            Long sourceCosmeticSetId,
            String setNameSnapshot,
            String setUsageTimeSnapshot,
            String usagePeriod,
            int sortOrder
    ) {
        this.dailyRecord = dailyRecord;
        this.sourceCosmeticSetId = sourceCosmeticSetId;
        this.setNameSnapshot = setNameSnapshot;
        this.setUsageTimeSnapshot = setUsageTimeSnapshot;
        this.usagePeriod = usagePeriod;
        this.sortOrder = sortOrder;
    }
}
