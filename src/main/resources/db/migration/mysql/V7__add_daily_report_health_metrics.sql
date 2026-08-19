ALTER TABLE daily_health_summaries
    ADD COLUMN menstrual_cycle_day INT NULL,
    ADD COLUMN total_calories_burned INT NULL,
    ADD COLUMN avg_spo2 DECIMAL(5, 2) NULL;