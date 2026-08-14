package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCosmeticRepository extends JpaRepository<UserCosmetic, Long> {
}
