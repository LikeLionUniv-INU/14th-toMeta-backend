package com.likelion.tometa.domain.record.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import com.likelion.tometa.domain.record.enums.DailyRecordSelectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Entity
@Table(
        name = "daily_record_selections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_record_selections_source",
                        columnNames = {
                                "daily_record_id",
                                "usage_period",
                                "selection_type",
                                "source_id"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_daily_record_selections_sort",
                        columnNames = {
                                "daily_record_id",
                                "usage_period",
                                "selection_type",
                                "sort_order"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecordSelection extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_record_selection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_record_id", nullable = false)
    private DailyRecord dailyRecord;

    @Column(name = "usage_period", nullable = false, length = 10)
    private String usagePeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 10)
    private DailyRecordSelectionType selectionType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "name_snapshot", nullable = false, length = 255)
    private String nameSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags_snapshot", nullable = false, columnDefinition = "json")
    private List<String> tagsSnapshot;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private DailyRecordSelection(
            DailyRecord dailyRecord,
            String usagePeriod,
            DailyRecordSelectionType selectionType,
            Long sourceId,
            String nameSnapshot,
            List<String> tagsSnapshot,
            int sortOrder
    ) {
        this.dailyRecord = dailyRecord;
        this.usagePeriod = usagePeriod;
        this.selectionType = selectionType;
        this.sourceId = sourceId;
        this.nameSnapshot = nameSnapshot;
        this.tagsSnapshot = List.copyOf(tagsSnapshot);
        this.sortOrder = sortOrder;
    }
}
