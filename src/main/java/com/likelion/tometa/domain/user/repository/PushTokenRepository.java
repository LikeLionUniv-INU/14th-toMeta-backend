package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.PushToken;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByUserAndDeviceId(User user, String deviceId);

    Optional<PushToken> findByDeviceIdAndFirebaseInstallationId(String deviceId, String firebaseInstallationId);

    Optional<PushToken> findByIdAndUser(Long id, User user);
}
