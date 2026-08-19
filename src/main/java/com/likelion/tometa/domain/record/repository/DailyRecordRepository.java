package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    Optional<DailyRecord> findByUserAndRecordDate(User user, LocalDate recordDate);

    boolean existsByUserAndRecordDate(User user, LocalDate recordDate);

    List<DailyRecord> findAllByUserAndRecordDateBetween(User user, LocalDate startDate, LocalDate endDate);
}
