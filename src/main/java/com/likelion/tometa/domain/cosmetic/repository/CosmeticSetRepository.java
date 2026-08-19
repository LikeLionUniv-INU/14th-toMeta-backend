package com.likelion.tometa.domain.cosmetic.repository;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface CosmeticSetRepository extends JpaRepository<CosmeticSet, Long> {

    List<CosmeticSet> findAllByUserOrderByCreatedAtDescIdDesc(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CosmeticSet> findByIdAndUser(Long id, User user);

    @Query("""
            select cosmeticSet
            from CosmeticSet cosmeticSet
            where cosmeticSet.id = :id
              and cosmeticSet.user = :user
            """)
    Optional<CosmeticSet> findByIdAndUserForRead(
            @Param("id") Long id,
            @Param("user") User user
    );

    @Query("""
            select cosmeticSet
            from CosmeticSet cosmeticSet
            where cosmeticSet.id in :ids
              and cosmeticSet.user = :user
            order by cosmeticSet.id
            """)
    List<CosmeticSet> findAllByIdInAndUserOrderById(
            @Param("ids") Collection<Long> ids,
            @Param("user") User user
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item.cosmeticSet
            from CosmeticSetItem item
            where item.userCosmetic = :userCosmetic
              and item.cosmeticSet.user = :user
            order by item.cosmeticSet.id
            """)
    List<CosmeticSet> findAllContainingUserCosmeticForUpdate(
            @Param("userCosmetic") UserCosmetic userCosmetic,
            @Param("user") User user
    );
}
