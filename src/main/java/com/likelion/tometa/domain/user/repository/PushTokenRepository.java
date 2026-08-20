package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.PushToken;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    boolean existsByUser_Id(Long userId);

    List<PushToken> findAllByUser_Id(Long userId);

    Optional<PushToken> findByUserAndDeviceId(
            User user,
            String deviceId
    );

    Optional<PushToken> findByDeviceIdAndFirebaseInstallationId(
            String deviceId,
            String firebaseInstallationId
    );

    Optional<PushToken> findByIdAndUser(
            Long id,
            User user
    );

    @Modifying
    @Transactional
    @Query("""
            delete from PushToken pushToken
            where pushToken.id = :id
              and pushToken.firebaseInstallationId = :firebaseInstallationId
            """)
    int deleteByIdAndFirebaseInstallationId(
            @Param("id") Long id,
            @Param("firebaseInstallationId") String firebaseInstallationId
    );
}