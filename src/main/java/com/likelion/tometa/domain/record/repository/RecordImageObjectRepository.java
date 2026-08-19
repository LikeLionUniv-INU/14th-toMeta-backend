package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.RecordImageObject;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecordImageObjectRepository extends JpaRepository<RecordImageObject, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select object
            from RecordImageObject object
            where object.objectKey = :objectKey
            """)
    Optional<RecordImageObject> findByObjectKeyForUpdate(@Param("objectKey") String objectKey);
}
