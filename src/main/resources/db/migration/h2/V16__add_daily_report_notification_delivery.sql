alter table daily_reports
    add column notification_status varchar(20) default 'pending' not null;

alter table daily_reports
    add column notification_started_at timestamp(6);

alter table daily_reports
    add column notification_attempt_id varchar(36);

alter table daily_reports
    add column notification_sent_at timestamp(6);

update daily_reports
set notification_status = 'unknown'
where report_status = 'completed';

create index idx_daily_reports_notification_delivery
    on daily_reports (
                      report_status,
                      notification_status,
                      notification_started_at
        );