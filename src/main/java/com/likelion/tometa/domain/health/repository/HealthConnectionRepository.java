package com.likelion.tometa.domain.health.repository;

import com.likelion.tometa.domain.health.entity.HealthConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HealthConnectionRepository extends JpaRepository<HealthConnection, Long> {

    boolean existsByUser_IdAndRevokedAtIsNull(Long userId);

    Optional<HealthConnection> findByUser_IdAndDeviceId(Long userId, String deviceId);
}
