package com.likelion.tometa.domain.tip.service;

import com.likelion.tometa.domain.tip.entity.SkinCareTip;
import com.likelion.tometa.domain.tip.entity.UserDailySkinCareTip;
import com.likelion.tometa.domain.tip.repository.SkinCareTipRepository;
import com.likelion.tometa.domain.tip.repository.UserDailySkinCareTipRepository;
import com.likelion.tometa.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class DailySkinCareTipService {

    private final UserDailySkinCareTipRepository userDailySkinCareTipRepository;
    private final SkinCareTipRepository skinCareTipRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String assignOrGet(User user, LocalDate today) {
        Optional<UserDailySkinCareTip> assignedTip = userDailySkinCareTipRepository.findByUserAndTipDate(user, today);

        if (assignedTip.isPresent()) {
            return assignedTip.get().getSkinCareTip().getContent();
        }

        List<SkinCareTip> activeTips = skinCareTipRepository.findAllByActiveTrue();

        if (activeTips.isEmpty()) {
            return null;
        }

        SkinCareTip selectedTip = activeTips.get(ThreadLocalRandom.current().nextInt(activeTips.size()));

        userDailySkinCareTipRepository.saveAndFlush(
                UserDailySkinCareTip.builder()
                        .user(user)
                        .skinCareTip(selectedTip)
                        .tipDate(today)
                        .build()
        );

        return selectedTip.getContent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<String> findAssignedTip(User user, LocalDate today) {
        return userDailySkinCareTipRepository.findByUserAndTipDate(user, today)
                .map(tip -> tip.getSkinCareTip().getContent());
    }
}