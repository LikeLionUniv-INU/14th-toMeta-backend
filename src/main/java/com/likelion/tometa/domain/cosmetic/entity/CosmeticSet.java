package com.likelion.tometa.domain.cosmetic.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cosmetic_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CosmeticSet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cosmetic_set_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_time", nullable = false, length = 20)
    private CosmeticSetUsageTime usageTime;

    @Builder
    private CosmeticSet(
            User user,
            String name,
            CosmeticSetUsageTime usageTime
    ) {
        this.user = user;
        this.name = name;
        this.usageTime = usageTime;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateUsageTime(CosmeticSetUsageTime usageTime) {
        this.usageTime = usageTime;
    }
}
