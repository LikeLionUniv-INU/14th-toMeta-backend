package com.likelion.tometa.domain.cosmetic.entity;

import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cosmetic_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cosmetic_tags_product_type_order",
                columnNames = {"cosmetic_product_id", "tag_type", "tag_order"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CosmeticTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cosmetic_tag_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cosmetic_product_id", nullable = false)
    private CosmeticProduct cosmeticProduct;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false, length = 20)
    private CosmeticTagType tagType;

    @Column(name = "tag_name", nullable = false, length = 100)
    private String name;

    @Column(name = "tag_order", nullable = false)
    private int tagOrder;

    @Builder
    private CosmeticTag(
            CosmeticProduct cosmeticProduct,
            CosmeticTagType tagType,
            String name,
            int tagOrder
    ) {
        this.cosmeticProduct = cosmeticProduct;
        this.tagType = tagType;
        this.name = name;
        this.tagOrder = tagOrder;
    }
}
