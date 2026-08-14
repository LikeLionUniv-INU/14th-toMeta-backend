package com.likelion.tometa.domain.health.repository;

import com.likelion.tometa.domain.health.entity.HealthConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthConnectionRepository extends JpaRepository<HealthConnection, Long> {

    boolean existsByUser_IdAndRevokedAtIsNull(Long userId);
}
