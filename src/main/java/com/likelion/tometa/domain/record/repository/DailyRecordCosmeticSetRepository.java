package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmeticSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyRecordCosmeticSetRepository extends JpaRepository<DailyRecordCosmeticSet, Long> {

    List<DailyRecordCosmeticSet> findAllByDailyRecord(DailyRecord dailyRecord);
}
