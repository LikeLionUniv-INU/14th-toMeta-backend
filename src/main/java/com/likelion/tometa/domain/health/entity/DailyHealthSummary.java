package com.likelion.tometa.domain.health.entity;

import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "daily_health_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_health_summaries_user_date",
                columnNames = {"user_id", "summary_date"}
        ),
        indexes = @Index(
                name = "idx_daily_health_summaries_summary_date",
                columnList = "summary_date"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyHealthSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_health_summary_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "sleep_minutes")
    private Integer sleepMinutes;

    @Column(name = "skin_temperature_celsius", precision = 4, scale = 2)
    private BigDecimal skinTemperatureCelsius;

    @Column(name = "stress_level", length = 20)
    private String stressLevel;

    @Column(name = "body_water_mass_kg", precision = 5, scale = 2)
    private BigDecimal bodyWaterMassKg;

    @Column(name = "hydration_intake_ml")
    private Integer hydrationIntakeMl;

    @Column(name = "menstruation_status", length = 30)
    private String menstruationStatus;

    @Column(name = "menstrual_cycle_day")
    private Integer menstrualCycleDay;

    @Column(name = "exercise_performed")
    private Boolean exercisePerformed;

    @Column(name = "exercise_minutes")
    private Integer exerciseMinutes;

    @Column(name = "total_calories_burned")
    private Integer totalCaloriesBurned;

    @Column(name = "average_heart_rate")
    private Integer averageHeartRate;

    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "resting_heart_rate")
    private Integer restingHeartRate;

    @Column(name = "hrv_rmssd", precision = 7, scale = 2)
    private BigDecimal hrvRmssd;

    @Column(name = "avg_spo2", precision = 5, scale = 2)
    private BigDecimal avgSpo2;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Builder
    private DailyHealthSummary(User user, LocalDate summaryDate) {
        this.user = user;
        this.summaryDate = summaryDate;
        this.calculatedAt = LocalDateTime.now();
    }

    public void update(
            Integer sleepMinutes,
            BigDecimal skinTemperatureCelsius,
            String stressLevel,
            BigDecimal bodyWaterMassKg,
            Integer hydrationIntakeMl,
            String menstruationStatus,
            Boolean exercisePerformed,
            Integer exerciseMinutes,
            Integer averageHeartRate,
            Integer maxHeartRate,
            Integer restingHeartRate,
            BigDecimal hrvRmssd,
            Integer steps
    ) {
        this.sleepMinutes = sleepMinutes;
        this.skinTemperatureCelsius = skinTemperatureCelsius;
        this.stressLevel = stressLevel;
        this.bodyWaterMassKg = bodyWaterMassKg;
        this.hydrationIntakeMl = hydrationIntakeMl;
        this.menstruationStatus = menstruationStatus;
        this.exercisePerformed = exercisePerformed;
        this.exerciseMinutes = exerciseMinutes;
        this.averageHeartRate = averageHeartRate;
        this.maxHeartRate = maxHeartRate;
        this.restingHeartRate = restingHeartRate;
        this.hrvRmssd = hrvRmssd;
        this.steps = steps;
        this.calculatedAt = LocalDateTime.now();
    }

    public void updateReportMetrics(
            Integer sleepMinutes,
            BigDecimal skinTemperatureCelsius,
            Integer exerciseMinutes,
            Integer totalCaloriesBurned,
            Integer menstrualCycleDay,
            BigDecimal avgSpo2
    ) {
        this.sleepMinutes = sleepMinutes;
        this.skinTemperatureCelsius = skinTemperatureCelsius;
        this.exerciseMinutes = exerciseMinutes;
        this.totalCaloriesBurned = totalCaloriesBurned;
        this.menstrualCycleDay = menstrualCycleDay;
        this.avgSpo2 = avgSpo2;
        this.calculatedAt = LocalDateTime.now();
    }

    public void updateSteps(Integer steps) {
        this.steps = steps;
        this.calculatedAt = LocalDateTime.now();
    }
}
