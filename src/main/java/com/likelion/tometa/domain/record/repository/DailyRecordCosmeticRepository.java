package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmetic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyRecordCosmeticRepository extends JpaRepository<DailyRecordCosmetic, Long> {

    List<DailyRecordCosmetic> findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(
            DailyRecord dailyRecord
    );
}
