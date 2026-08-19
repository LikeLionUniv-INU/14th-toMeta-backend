package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CosmeticIngredientRepository extends JpaRepository<CosmeticIngredient, Long> {

    @Query("""
            select cosmeticIngredient
            from CosmeticIngredient cosmeticIngredient
            where cosmeticIngredient.cosmeticProduct.id in :cosmeticProductIds
              and cosmeticIngredient.main = true
            order by cosmeticIngredient.cosmeticProduct.id,
                     cosmeticIngredient.ingredientOrder
            """)
    List<CosmeticIngredient> findAllMainByCosmeticProductIds(
            @Param("cosmeticProductIds") Collection<Long> cosmeticProductIds
    );

    @Query("""
            select cosmeticIngredient
            from CosmeticIngredient cosmeticIngredient
            left join fetch cosmeticIngredient.ingredient
            where cosmeticIngredient.cosmeticProduct.id in :cosmeticProductIds
            order by cosmeticIngredient.cosmeticProduct.id,
                     cosmeticIngredient.ingredientOrder
            """)
    List<CosmeticIngredient> findAllByCosmeticProductIdsOrderByIngredientOrder(
            @Param("cosmeticProductIds") Collection<Long> cosmeticProductIds
    );
}
