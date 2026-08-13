package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    boolean existsByUserIdAndConsentTypeAndVersion(
            Long userId,
            String consentType,
            String version
    );
}
