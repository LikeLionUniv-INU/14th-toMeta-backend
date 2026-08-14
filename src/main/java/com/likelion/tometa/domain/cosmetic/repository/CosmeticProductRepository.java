package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CosmeticProductRepository extends JpaRepository<CosmeticProduct, Long> {
}
