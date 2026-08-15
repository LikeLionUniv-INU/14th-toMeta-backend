package com.likelion.tometa.domain.cosmetic.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cosmetic_set_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_cosmetic_set_items_set_cosmetic",
                        columnNames = {"cosmetic_set_id", "user_cosmetic_id"}
                ),
                @UniqueConstraint(
                        name = "uk_cosmetic_set_items_set_order",
                        columnNames = {"cosmetic_set_id", "item_order"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CosmeticSetItem extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cosmetic_set_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cosmetic_set_id", nullable = false)
    private CosmeticSet cosmeticSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_cosmetic_id", nullable = false)
    private UserCosmetic userCosmetic;

    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Builder
    private CosmeticSetItem(
            CosmeticSet cosmeticSet,
            UserCosmetic userCosmetic,
            Integer itemOrder
    ) {
        this.cosmeticSet = cosmeticSet;
        this.userCosmetic = userCosmetic;
        this.itemOrder = itemOrder;
    }
}
