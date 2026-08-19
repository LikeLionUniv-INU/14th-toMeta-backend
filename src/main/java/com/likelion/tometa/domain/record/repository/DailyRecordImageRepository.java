package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DailyRecordImageRepository extends JpaRepository<DailyRecordImage, Long> {

    boolean existsByObjectKeyIn(Collection<String> objectKeys);

    @Query("""
            select image.objectKey
            from DailyRecordImage image
            where image.objectKey in :objectKeys
            """)
    List<String> findReferencedObjectKeys(@Param("objectKeys") Collection<String> objectKeys);
}
