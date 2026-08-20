package com.likelion.tometa.domain.record.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.record.constant.DailyRecordPolicy;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "daily_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_records_user_date",
                columnNames = {"user_id", "record_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "skin_status", nullable = false, length = 20)
    private String skinStatus;

    @Column(name = "food_memo", length = DailyRecordPolicy.MAX_MEMO_LENGTH)
    private String foodMemo;

    @Column(name = "memo", length = DailyRecordPolicy.MAX_MEMO_LENGTH)
    private String memo;

    @Builder
    private DailyRecord(
            User user,
            LocalDate recordDate,
            String skinStatus,
            String foodMemo,
            String memo
    ) {
        this.user = user;
        this.recordDate = recordDate;
        this.skinStatus = skinStatus;
        this.foodMemo = foodMemo;
        this.memo = memo;
    }

    public void update(
            String skinStatus,
            String foodMemo,
            String memo
    ) {
        this.skinStatus = skinStatus;
        this.foodMemo = foodMemo;
        this.memo = memo;
    }
}
