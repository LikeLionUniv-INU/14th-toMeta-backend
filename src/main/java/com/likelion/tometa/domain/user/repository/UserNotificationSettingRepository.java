package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserNotificationSettingRepository
        extends JpaRepository<UserNotificationSetting, Long> {

    boolean existsByUser_Id(Long userId);

    Optional<UserNotificationSetting> findByUser_Id(Long userId);
}
