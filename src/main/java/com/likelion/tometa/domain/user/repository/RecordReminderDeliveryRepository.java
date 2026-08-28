package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.RecordReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface RecordReminderDeliveryRepository
        extends JpaRepository<RecordReminderDelivery, Long> {

    boolean existsByUser_IdAndReminderDate(Long userId, LocalDate reminderDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update RecordReminderDelivery delivery
               set delivery.notificationStatus = 'claimed',
                   delivery.notificationStartedAt = :startedAt,
                   delivery.notificationAttemptId = :attemptId
             where delivery.user.id = :userId
               and delivery.reminderDate = :reminderDate
               and (
                    delivery.notificationStatus = 'pending'
                    or (
                        delivery.notificationStatus = 'claimed'
                        and delivery.notificationStartedAt <= :staleBefore
                    )
               )
            """)
    int claim(
            @Param("userId") Long userId,
            @Param("reminderDate") LocalDate reminderDate,
            @Param("attemptId") String attemptId,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update RecordReminderDelivery delivery
               set delivery.notificationStatus = 'sending',
                   delivery.notificationStartedAt = :deliveryStartedAt
             where delivery.user.id = :userId
               and delivery.reminderDate = :reminderDate
               and delivery.notificationStatus = 'claimed'
               and delivery.notificationAttemptId = :attemptId
            """)
    int beginDelivery(
            @Param("userId") Long userId,
            @Param("reminderDate") LocalDate reminderDate,
            @Param("attemptId") String attemptId,
            @Param("deliveryStartedAt") LocalDateTime deliveryStartedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update RecordReminderDelivery delivery
               set delivery.notificationStatus = 'sent',
                   delivery.notificationStartedAt = null,
                   delivery.notificationSentAt = :sentAt
             where delivery.user.id = :userId
               and delivery.reminderDate = :reminderDate
               and delivery.notificationStatus = 'sending'
               and delivery.notificationAttemptId = :attemptId
            """)
    int markSent(
            @Param("userId") Long userId,
            @Param("reminderDate") LocalDate reminderDate,
            @Param("attemptId") String attemptId,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update RecordReminderDelivery delivery
               set delivery.notificationStatus = 'unknown'
             where delivery.user.id = :userId
               and delivery.reminderDate = :reminderDate
               and delivery.notificationStatus = 'sending'
               and delivery.notificationAttemptId = :attemptId
            """)
    int markUnknown(
            @Param("userId") Long userId,
            @Param("reminderDate") LocalDate reminderDate,
            @Param("attemptId") String attemptId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update RecordReminderDelivery delivery
               set delivery.notificationStatus = 'unknown'
             where delivery.notificationStatus = 'sending'
               and delivery.notificationStartedAt <= :staleBefore
            """)
    int markStaleDeliveriesUnknown(
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update RecordReminderDelivery delivery
               set delivery.notificationStatus = 'pending',
                   delivery.notificationStartedAt = null,
                   delivery.notificationAttemptId = null
             where delivery.user.id = :userId
               and delivery.reminderDate = :reminderDate
               and delivery.notificationStatus = 'claimed'
               and delivery.notificationAttemptId = :attemptId
            """)
    int resetClaim(
            @Param("userId") Long userId,
            @Param("reminderDate") LocalDate reminderDate,
            @Param("attemptId") String attemptId
    );
}
