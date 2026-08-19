package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface DailyRecordImageRepository extends JpaRepository<DailyRecordImage, Long> {

    boolean existsByObjectKeyIn(Collection<String> objectKeys);
}
