package com.likelion.tometa.domain.tip.repository;

import com.likelion.tometa.domain.tip.entity.UserDailySkinCareTip;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserDailySkinCareTipRepository extends JpaRepository<UserDailySkinCareTip, Long> {

    Optional<UserDailySkinCareTip> findByUserAndTipDate(User user, LocalDate tipDate);
}