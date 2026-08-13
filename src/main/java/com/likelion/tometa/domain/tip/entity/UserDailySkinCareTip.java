package com.likelion.tometa.domain.tip.entity;

import com.likelion.tometa.domain.common.entity.BaseCreatedEntity;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "user_daily_skin_care_tips",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_daily_skin_care_tips_user_date",
                columnNames = {"user_id", "tip_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailySkinCareTip extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_daily_tip_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skin_care_tip_id", nullable = false)
    private SkinCareTip skinCareTip;

    @Column(name = "tip_date", nullable = false)
    private LocalDate tipDate;

    @Builder
    private UserDailySkinCareTip(User user, SkinCareTip skinCareTip, LocalDate tipDate) {
        this.user = user;
        this.skinCareTip = skinCareTip;
        this.tipDate = tipDate;
    }
}
