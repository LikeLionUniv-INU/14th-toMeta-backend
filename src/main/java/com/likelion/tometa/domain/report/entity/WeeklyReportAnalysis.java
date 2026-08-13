package com.likelion.tometa.domain.report.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "weekly_report_analyses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weekly_report_analyses_report_sort",
                columnNames = {"weekly_report_id", "sort_order"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyReportAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_report_analysis_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weekly_report_id", nullable = false)
    private WeeklyReport weeklyReport;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private WeeklyReportAnalysis(WeeklyReport weeklyReport, String content, int sortOrder) {
        this.weeklyReport = weeklyReport;
        this.content = content;
        this.sortOrder = sortOrder;
    }
}
