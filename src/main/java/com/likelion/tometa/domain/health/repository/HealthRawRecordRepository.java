package com.likelion.tometa.domain.health.repository;

import com.likelion.tometa.domain.health.entity.HealthRawRecord;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HealthRawRecordRepository extends JpaRepository<HealthRawRecord, Long> {

    Optional<HealthRawRecord> findByHealthConnection_IdAndHcRecordId(
            Long healthConnectionId,
            String hcRecordId
    );

    @Query("""
            select record
            from HealthRawRecord record
            where record.healthConnection.user = :user
              and record.recordType = :recordType
              and record.endTime >= :startTime
              and record.endTime < :endTimeExclusive
            order by record.endTime asc
            """)
    List<HealthRawRecord> findAllByUserAndRecordTypeAndEndTimeRange(
            @Param("user") User user,
            @Param("recordType") String recordType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive
    );
}
