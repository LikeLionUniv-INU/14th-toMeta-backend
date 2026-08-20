ALTER TABLE weekly_reports
    ADD COLUMN report_status VARCHAR(20) NOT NULL DEFAULT 'completed';