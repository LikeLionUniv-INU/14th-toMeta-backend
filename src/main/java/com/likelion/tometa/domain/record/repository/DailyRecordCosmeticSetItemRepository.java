package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmeticSetItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyRecordCosmeticSetItemRepository
        extends JpaRepository<DailyRecordCosmeticSetItem, Long> {

    List<DailyRecordCosmeticSetItem> findAllByDailyRecordCosmeticSet_DailyRecord(
            DailyRecord dailyRecord
    );
}
