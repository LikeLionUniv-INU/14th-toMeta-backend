package com.likelion.tometa.domain.tip.repository;

import com.likelion.tometa.domain.tip.entity.SkinCareTip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkinCareTipRepository extends JpaRepository<SkinCareTip, Long> {

    List<SkinCareTip> findAllByActiveTrue();
}