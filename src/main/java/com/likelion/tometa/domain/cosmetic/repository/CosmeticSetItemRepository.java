package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CosmeticSetItemRepository extends JpaRepository<CosmeticSetItem, Long> {

    @Modifying
    @Query("delete from CosmeticSetItem item where item.cosmeticSet = :cosmeticSet")
    void deleteAllByCosmeticSet(@Param("cosmeticSet") CosmeticSet cosmeticSet);

    @Modifying
    @Query("delete from CosmeticSetItem item where item.cosmeticSet.id = :cosmeticSetId")
    void deleteAllByCosmeticSetId(@Param("cosmeticSetId") Long cosmeticSetId);
}
