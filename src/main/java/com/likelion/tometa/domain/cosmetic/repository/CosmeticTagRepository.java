package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CosmeticTagRepository extends JpaRepository<CosmeticTag, Long> {

    @Query("""
            select tag
            from CosmeticTag tag
            where tag.cosmeticProduct.id in :cosmeticProductIds
            order by tag.cosmeticProduct.id, tag.tagType, tag.tagOrder, tag.id
            """)
    List<CosmeticTag> findAllByCosmeticProductIds(
            @Param("cosmeticProductIds") Collection<Long> cosmeticProductIds
    );
}
