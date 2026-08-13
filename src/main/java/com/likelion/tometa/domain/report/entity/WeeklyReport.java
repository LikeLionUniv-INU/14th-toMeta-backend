package com.likelion.tometa.domain.report.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "weekly_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weekly_reports_user_start",
                columnNames = {"user_id", "week_start_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Lob
    @Column(name = "weekly_summary", nullable = false, columnDefinition = "TEXT")
    private String weeklySummary;

    @Lob
    @Column(name = "personalized_solution", nullable = false, columnDefinition = "TEXT")
    private String personalizedSolution;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "regenerated_at")
    private LocalDateTime regeneratedAt;

    @Builder
    private WeeklyReport(
            User user,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            String weeklySummary,
            String personalizedSolution
    ) {
        this.user = user;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.weeklySummary = weeklySummary;
        this.personalizedSolution = personalizedSolution;
        this.generatedAt = LocalDateTime.now();
    }

    public void regenerate(String weeklySummary, String personalizedSolution) {
        this.weeklySummary = weeklySummary;
        this.personalizedSolution = personalizedSolution;
        this.regeneratedAt = LocalDateTime.now();
    }

    public void updateNote(String note) {
        this.note = note;
    }
}
