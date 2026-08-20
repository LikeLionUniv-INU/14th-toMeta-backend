package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    Optional<DailyRecord> findByUserAndRecordDate(User user, LocalDate recordDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            select dailyRecord
            from DailyRecord dailyRecord
            where dailyRecord.user = :user
              and dailyRecord.recordDate = :recordDate
            """)
    Optional<DailyRecord> findByUserAndRecordDateForUpdate(
            @Param("user") User user,
            @Param("recordDate") LocalDate recordDate
    );

    boolean existsByUserAndRecordDate(User user, LocalDate recordDate);

    List<DailyRecord> findAllByUserAndRecordDateBetween(
            User user,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
            select distinct dailyRecord.user.id
            from DailyRecord dailyRecord
            left join DailyReport dailyReport
              on dailyReport.dailyRecord = dailyRecord
            where dailyRecord.recordDate = :recordDate
              and (
                    dailyReport is null
                    or dailyReport.reportStatus <> 'completed'
              )
            order by dailyRecord.user.id
            """)
    List<Long> findDailyReportGenerationTargetUserIds(
            @Param("recordDate") LocalDate recordDate
    );
}
