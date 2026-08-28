package com.likelion.tometa.domain.user.service;

import com.likelion.tometa.domain.user.entity.RecordReminderDelivery;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.RecordReminderDeliveryRepository;
import com.likelion.tometa.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RecordReminderDeliveryInitializer {

    private final RecordReminderDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initialize(Long userId, LocalDate reminderDate) {
        if (deliveryRepository.existsByUser_IdAndReminderDate(
                userId,
                reminderDate
        )) {
            return;
        }

        User user = userRepository.getReferenceById(userId);
        deliveryRepository.saveAndFlush(
                RecordReminderDelivery.builder()
                        .user(user)
                        .reminderDate(reminderDate)
                        .build()
        );
    }
}
