package com.likelion.tometa.domain.report.repository;

import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.entity.WeeklyReportAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyReportAnalysisRepository extends JpaRepository<WeeklyReportAnalysis, Long> {

    List<WeeklyReportAnalysis> findAllByWeeklyReportOrderBySortOrderAsc(WeeklyReport weeklyReport);
}
