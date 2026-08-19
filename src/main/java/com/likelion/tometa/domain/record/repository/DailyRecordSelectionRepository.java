package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyRecordSelectionRepository
        extends JpaRepository<DailyRecordSelection, Long> {

    List<DailyRecordSelection> findAllByDailyRecord(DailyRecord dailyRecord);
}
