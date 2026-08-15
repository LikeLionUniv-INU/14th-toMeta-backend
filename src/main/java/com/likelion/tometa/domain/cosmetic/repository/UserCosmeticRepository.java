package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCosmeticRepository extends JpaRepository<UserCosmetic, Long> {

    Optional<UserCosmetic> findByIdAndUserAndDeletedAtIsNull(Long id, User user);

    List<UserCosmetic> findAllByIdInAndUserAndDeletedAtIsNull(
            Collection<Long> ids,
            User user
    );
}
