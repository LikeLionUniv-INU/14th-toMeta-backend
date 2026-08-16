package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CosmeticSetRepository extends JpaRepository<CosmeticSet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CosmeticSet> findByIdAndUser(Long id, User user);
}
