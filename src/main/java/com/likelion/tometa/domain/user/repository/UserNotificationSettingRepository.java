package com.likelion.tometa.domain.user.repository;

import com.likelion.tometa.domain.user.entity.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface UserNotificationSettingRepository
        extends JpaRepository<UserNotificationSetting, Long> {

    boolean existsByUser_Id(Long userId);

    Optional<UserNotificationSetting> findByUser_Id(Long userId);

    @Query("""
            select setting.user.id
            from UserNotificationSetting setting
            where setting.recordReminderEnabled = true
              and setting.recordReminderTime is not null
              and setting.recordReminderTime <= :reminderTime
              and not exists (
                    select dailyRecord.id
                    from DailyRecord dailyRecord
                    where dailyRecord.user = setting.user
                      and dailyRecord.recordDate = :reminderDate
              )
              and (
                    not exists (
                        select delivery.id
                        from RecordReminderDelivery delivery
                        where delivery.user = setting.user
                          and delivery.reminderDate = :reminderDate
                    )
                    or exists (
                        select retryableDelivery.id
                        from RecordReminderDelivery retryableDelivery
                        where retryableDelivery.user = setting.user
                          and retryableDelivery.reminderDate = :reminderDate
                          and (
                                retryableDelivery.notificationStatus = 'pending'
                                or (
                                    retryableDelivery.notificationStatus = 'claimed'
                                    and retryableDelivery.notificationStartedAt <= :staleBefore
                                )
                          )
                    )
              )
            order by setting.user.id
            """)
    List<Long> findRecordReminderTargetUserIds(
            @Param("reminderDate") LocalDate reminderDate,
            @Param("reminderTime") LocalTime reminderTime,
            @Param("staleBefore") LocalDateTime staleBefore
    );
}
