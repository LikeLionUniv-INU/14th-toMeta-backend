package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    Optional<DailyRecord> findByUserAndRecordDate(User user, LocalDate recordDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
            from DailyRecord record
            where record.user = :user
              and record.recordDate = :recordDate
            """)
    Optional<DailyRecord> findByUserAndRecordDateForUpdate(
            @Param("user") User user,
            @Param("recordDate") LocalDate recordDate
    );

    boolean existsByUserAndRecordDate(User user, LocalDate recordDate);

    List<DailyRecord> findAllByUserAndRecordDateBetween(User user, LocalDate startDate, LocalDate endDate);
}
