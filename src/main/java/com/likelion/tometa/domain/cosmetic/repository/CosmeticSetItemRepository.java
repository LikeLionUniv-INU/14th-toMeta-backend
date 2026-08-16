package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CosmeticSetItemRepository extends JpaRepository<CosmeticSetItem, Long> {

    @Modifying
    @Query("delete from CosmeticSetItem item where item.cosmeticSet = :cosmeticSet")
    void deleteAllByCosmeticSet(@Param("cosmeticSet") CosmeticSet cosmeticSet);

    @Modifying
    @Query("delete from CosmeticSetItem item where item.cosmeticSet.id = :cosmeticSetId")
    void deleteAllByCosmeticSetId(@Param("cosmeticSetId") Long cosmeticSetId);

    @Query("""
            select item.cosmeticSet as cosmeticSet, count(item.id) as itemCount
            from CosmeticSetItem item
            where item.cosmeticSet in :cosmeticSets
            group by item.cosmeticSet
            """)
    List<CosmeticSetItemCount> countItemsByCosmeticSetIn(
            @Param("cosmeticSets") Collection<CosmeticSet> cosmeticSets
    );

    @Modifying
    @Query("""
            delete from CosmeticSetItem item
            where item.userCosmetic = :userCosmetic
              and item.cosmeticSet in :cosmeticSets
            """)
    void deleteAllByUserCosmeticAndCosmeticSetIn(
            @Param("userCosmetic") UserCosmetic userCosmetic,
            @Param("cosmeticSets") Collection<CosmeticSet> cosmeticSets
    );

    @Modifying
    @Query("delete from CosmeticSetItem item where item.cosmeticSet in :cosmeticSets")
    void deleteAllByCosmeticSetIn(
            @Param("cosmeticSets") Collection<CosmeticSet> cosmeticSets
    );

    interface CosmeticSetItemCount {

        CosmeticSet getCosmeticSet();

        Long getItemCount();
    }
}
