package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
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

    List<DailyReport> findAllByDailyRecord_UserAndDailyRecord_RecordDateBetweenAndReportStatusOrderByDailyRecord_RecordDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String reportStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from DailyReport report where report.dailyRecord = :dailyRecord")
    Optional<DailyReport> findByDailyRecordForUpdate(
            @Param("dailyRecord") DailyRecord dailyRecord
    );
}
