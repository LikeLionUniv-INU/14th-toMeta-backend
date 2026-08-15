package com.likelion.tometa.domain.cosmetic.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "user_cosmetics",
        indexes = @Index(
                name = "idx_user_cosmetics_user_deleted",
                columnList = "user_id, deleted_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCosmetic extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_cosmetic_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cosmetic_product_id", nullable = false)
    private CosmeticProduct cosmeticProduct;

    @Column(name = "custom_name", length = 100)
    private String customName;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private UserCosmetic(
            User user,
            CosmeticProduct cosmeticProduct,
            String customName
    ) {
        this.user = user;
        this.cosmeticProduct = cosmeticProduct;
        this.customName = customName;
    }

    public void updateCustomName(String customName) {
        this.customName = customName;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
