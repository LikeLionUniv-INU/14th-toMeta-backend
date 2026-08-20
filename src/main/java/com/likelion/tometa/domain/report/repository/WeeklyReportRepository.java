package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    Optional<WeeklyReport> findByUserAndWeekStartDate(
            User user,
            LocalDate weekStartDate
    );

    Optional<WeeklyReport> findByIdAndUserAndReportStatus(
            Long id,
            User user,
            String reportStatus
    );

    List<WeeklyReport>
    findAllByUserAndWeekStartDateBetweenAndReportStatusOrderByWeekStartDateAsc(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            String reportStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report
            from WeeklyReport report
            where report.user = :user
              and report.weekStartDate = :weekStartDate
            """)
    Optional<WeeklyReport> findByUserAndWeekStartDateForUpdate(
            @Param("user") User user,
            @Param("weekStartDate") LocalDate weekStartDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report
            from WeeklyReport report
            where report.id = :reportId
            """)
    Optional<WeeklyReport> findByIdForUpdate(
            @Param("reportId") Long reportId
    );
}
