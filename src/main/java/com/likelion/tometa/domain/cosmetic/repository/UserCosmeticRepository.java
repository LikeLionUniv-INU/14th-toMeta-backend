package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserCosmeticRepository extends JpaRepository<UserCosmetic, Long> {

    @Query("""
            select userCosmetic
            from UserCosmetic userCosmetic
            join fetch userCosmetic.cosmeticProduct
            where userCosmetic.user = :user
              and userCosmetic.deletedAt is null
            order by userCosmetic.createdAt desc, userCosmetic.id desc
            """)
    List<UserCosmetic> findAllActiveByUserOrderByNewest(@Param("user") User user);

    Optional<UserCosmetic> findByIdAndUserAndDeletedAtIsNull(Long id, User user);

    List<UserCosmetic> findAllByIdInAndUserAndDeletedAtIsNull(
            Collection<Long> ids,
            User user
    );

    @Query("""
            select userCosmetic
            from UserCosmetic userCosmetic
            join fetch userCosmetic.cosmeticProduct
            where userCosmetic.id in :ids
              and userCosmetic.user = :user
              and userCosmetic.deletedAt is null
            order by userCosmetic.id
            """)
    List<UserCosmetic> findAllActiveByIdsAndUserForRecord(
            @Param("ids") Collection<Long> ids,
            @Param("user") User user
    );
}
