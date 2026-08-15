package com.likelion.tometa.domain.health.repository;

import com.likelion.tometa.domain.health.entity.HealthConnection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface HealthConnectionRepository extends JpaRepository<HealthConnection, Long> {

    boolean existsByUser_IdAndRevokedAtIsNull(Long userId);

    Optional<HealthConnection> findByUser_IdAndDeviceId(Long userId, String deviceId);

    Optional<HealthConnection> findTopByUser_IdAndRevokedAtIsNullOrderByLastSyncedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HealthConnection> findByDeviceTokenHashAndRevokedAtIsNull(String deviceTokenHash);
}
