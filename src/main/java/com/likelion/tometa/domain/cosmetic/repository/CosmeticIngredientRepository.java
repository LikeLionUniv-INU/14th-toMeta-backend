package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CosmeticIngredientRepository extends JpaRepository<CosmeticIngredient, Long> {
}
