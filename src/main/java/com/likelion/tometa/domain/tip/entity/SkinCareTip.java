package com.likelion.tometa.domain.tip.entity;

import com.likelion.tometa.domain.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "skin_care_tips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinCareTip extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skin_care_tip_id")
    private Long id;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private SkinCareTip(String content, boolean active) {
        this.content = content;
        this.active = active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
