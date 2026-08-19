package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByDailyRecord(DailyRecord dailyRecord);

    Optional<DailyReport> findFirstByDailyRecord_UserAndReportStatusOrderByDailyRecord_RecordDateDesc(
            User user,
            String reportStatus
    );

    Optional<DailyReport> findByDailyRecord_UserAndDailyRecord_RecordDateAndReportStatus(
            User user,
            LocalDate recordDate,
            String reportStatus
    );
}