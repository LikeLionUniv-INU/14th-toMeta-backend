package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CosmeticSetRepository extends JpaRepository<CosmeticSet, Long> {

    Optional<CosmeticSet> findByIdAndUser(Long id, User user);
}
