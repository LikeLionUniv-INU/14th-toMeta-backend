package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findAllByUserAndWeekStartDateBetweenOrderByWeekStartDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate
    );
}
