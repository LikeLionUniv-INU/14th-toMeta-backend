alter table weekly_reports
    add column notification_status varchar(20) not null default 'pending',
    add column notification_started_at datetime(6) null,
    add column notification_sent_at datetime(6) null;

create index idx_daily_records_report_generation
    on daily_records (record_date, user_id);

create index idx_weekly_reports_notification_schedule
    on weekly_reports (
        week_start_date,
        report_status,
        notification_status,
        notification_started_at
    );
