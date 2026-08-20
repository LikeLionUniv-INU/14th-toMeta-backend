package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DailyRecordImageRepository extends JpaRepository<DailyRecordImage, Long> {

    List<DailyRecordImage> findAllByDailyRecordOrderBySortOrderAsc(
            DailyRecord dailyRecord
    );

    List<DailyRecordImage> findAllByDailyRecordIn(
            List<DailyRecord> dailyRecords
    );
}
