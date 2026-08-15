package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationSettingRepository
        extends JpaRepository<UserNotificationSetting, Long> {

    boolean existsByUser_Id(Long userId);
}
