package com.likelion.tometa.domain.cosmetic.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cosmetic_ingredients",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cosmetic_ingredients_product_order",
                columnNames = {"cosmetic_product_id", "ingredient_order"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CosmeticIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cosmetic_ingredient_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cosmetic_product_id", nullable = false)
    private CosmeticProduct cosmeticProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;

    @Column(name = "ingredient_order", nullable = false)
    private int ingredientOrder;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Builder
    private CosmeticIngredient(
            CosmeticProduct cosmeticProduct,
            Ingredient ingredient,
            String ingredientName,
            int ingredientOrder,
            boolean main
    ) {
        this.cosmeticProduct = cosmeticProduct;
        this.ingredient = ingredient;
        this.ingredientName = ingredientName;
        this.ingredientOrder = ingredientOrder;
        this.main = main;
    }
}
