alter table daily_reports
    add column notification_status varchar(20) not null default 'pending',
    add column notification_started_at datetime(6) null,
    add column notification_attempt_id varchar(36) null,
    add column notification_sent_at datetime(6) null;

update daily_reports
set notification_status = 'unknown'
where report_status = 'completed';

create index idx_daily_reports_notification_delivery
    on daily_reports (
                      report_status,
                      notification_status,
                      notification_started_at
        );