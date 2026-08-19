package com.likelion.tometa.domain.report.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "daily_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_reports_record_id",
                        columnNames = "daily_record_id"
                ),
                @UniqueConstraint(
                        name = "uk_daily_reports_health_summary_id",
                        columnNames = "daily_health_summary_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_report_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_record_id", nullable = false, unique = true)
    private DailyRecord dailyRecord;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_health_summary_id", unique = true)
    private DailyHealthSummary dailyHealthSummary;

    @Column(name = "report_status", nullable = false, length = 20)
    private String reportStatus;

    @Lob
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Lob
    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;

    @Lob
    @Column(name = "personalized_solution", columnDefinition = "TEXT")
    private String personalizedSolution;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "regenerated_at")
    private LocalDateTime regeneratedAt;

    @Column(name = "generation_version", nullable = false)
    private long generationVersion;

    @Builder
    private DailyReport(DailyRecord dailyRecord, DailyHealthSummary dailyHealthSummary) {
        this.dailyRecord = dailyRecord;
        this.dailyHealthSummary = dailyHealthSummary;
        this.reportStatus = "collecting";
        this.generationVersion = 0L;
    }

    public void markGenerating() {
        this.reportStatus = "generating";
    }

    public void markCollecting() {
        this.reportStatus = "collecting";
    }

    public void complete(
            DailyHealthSummary dailyHealthSummary,
            String aiSummary,
            String aiAnalysis,
            String personalizedSolution
    ) {
        this.dailyHealthSummary = dailyHealthSummary;
        this.aiSummary = aiSummary;
        this.aiAnalysis = aiAnalysis;
        this.personalizedSolution = personalizedSolution;
        this.reportStatus = "completed";
        LocalDateTime completedAt = LocalDateTime.now();
        if (this.generatedAt == null) {
            this.generatedAt = completedAt;
        } else {
            this.regeneratedAt = completedAt;
        }
    }

    public void regenerate(
            DailyHealthSummary dailyHealthSummary,
            String aiSummary,
            String aiAnalysis,
            String personalizedSolution
    ) {
        this.dailyHealthSummary = dailyHealthSummary;
        this.aiSummary = aiSummary;
        this.aiAnalysis = aiAnalysis;
        this.personalizedSolution = personalizedSolution;
        this.reportStatus = "completed";
        this.regeneratedAt = LocalDateTime.now();
    }

    public void updateNote(String note) {
        this.note = note;
    }

    public void invalidateForRegeneration() {
        this.generationVersion++;
        this.reportStatus = "collecting";
        this.aiSummary = null;
        this.aiAnalysis = null;
        this.personalizedSolution = null;
        this.regeneratedAt = null;
    }
}
