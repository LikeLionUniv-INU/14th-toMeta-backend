package com.likelion.tometa.domain.health.repository;

import com.likelion.tometa.domain.health.entity.HealthRawRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HealthRawRecordRepository extends JpaRepository<HealthRawRecord, Long> {

    Optional<HealthRawRecord> findByHealthConnection_IdAndHcRecordId(
            Long healthConnectionId,
            String hcRecordId
    );
}
