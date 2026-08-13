package com.likelion.tometa.domain.health.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "health_raw_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_health_raw_records_connection_record",
                columnNames = {"health_connection_id", "hc_record_id"}
        ),
        indexes = {
                @Index(name = "idx_health_raw_records_record_type", columnList = "record_type"),
                @Index(name = "idx_health_raw_records_start_time", columnList = "start_time")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthRawRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "health_raw_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "health_connection_id", nullable = false)
    private HealthConnection healthConnection;

    @Column(name = "hc_record_id", nullable = false, length = 255)
    private String hcRecordId;

    @Column(name = "record_type", nullable = false, length = 50)
    private String recordType;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Builder
    private HealthRawRecord(
            HealthConnection healthConnection,
            String hcRecordId,
            String recordType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String payload
    ) {
        this.healthConnection = healthConnection;
        this.hcRecordId = hcRecordId;
        this.recordType = recordType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.payload = payload;
    }

    public void updatePayload(LocalDateTime startTime, LocalDateTime endTime, String payload) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.payload = payload;
    }
}
