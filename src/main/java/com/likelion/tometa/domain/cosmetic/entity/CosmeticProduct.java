package com.likelion.tometa.domain.cosmetic.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cosmetic_products",
        indexes = @Index(name = "idx_cosmetic_products_product_name", columnList = "product_name")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CosmeticProduct extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cosmetic_product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_key", length = 255)
    private String sourceKey;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Column(name = "product_type", nullable = false, length = 50)
    private String productType;

    @Lob
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Builder
    private CosmeticProduct(
            User createdByUser,
            String sourceType,
            String sourceKey,
            String productName,
            String brandName,
            String productType,
            String imageUrl
    ) {
        this.createdByUser = createdByUser;
        this.sourceType = sourceType;
        this.sourceKey = sourceKey;
        this.productName = productName;
        this.brandName = brandName;
        this.productType = productType;
        this.imageUrl = imageUrl;
    }
}
