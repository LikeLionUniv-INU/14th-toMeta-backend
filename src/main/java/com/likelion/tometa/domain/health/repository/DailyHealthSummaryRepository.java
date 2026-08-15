package com.likelion.tometa.domain.health.repository;

import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyHealthSummaryRepository extends JpaRepository<DailyHealthSummary, Long> {

    Optional<DailyHealthSummary> findByUser_IdAndSummaryDate(Long userId, LocalDate summaryDate);
}