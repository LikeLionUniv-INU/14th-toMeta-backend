create table record_reminder_deliveries (
    record_reminder_delivery_id bigint not null auto_increment,
    user_id bigint not null,
    reminder_date date not null,
    notification_status varchar(20) not null default 'pending',
    notification_started_at datetime(6) null,
    notification_attempt_id varchar(36) null,
    notification_sent_at datetime(6) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (record_reminder_delivery_id),
    constraint uk_record_reminder_deliveries_user_date
        unique (user_id, reminder_date),
    constraint fk_record_reminder_deliveries_user
        foreign key (user_id) references users (user_id)
) engine=InnoDB;

create index idx_record_reminder_delivery_schedule
    on record_reminder_deliveries (
        reminder_date,
        notification_status,
        notification_started_at
    );

create index idx_record_reminder_setting_schedule
    on user_notification_settings (
        record_reminder_enabled,
        record_reminder_time,
        user_id
    );
