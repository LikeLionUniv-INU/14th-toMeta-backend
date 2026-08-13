package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.AnonymousSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnonymousSessionRepository
        extends JpaRepository<AnonymousSession, Long> {

    Optional<AnonymousSession> findByTokenHash(String tokenHash);
}
