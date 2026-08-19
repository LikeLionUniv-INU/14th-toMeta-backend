ALTER TABLE daily_health_summaries
    ADD COLUMN menstrual_cycle_day INTEGER;

ALTER TABLE daily_health_summaries
    ADD COLUMN total_calories_burned INTEGER;

ALTER TABLE daily_health_summaries
    ADD COLUMN avg_spo2 DECIMAL(5, 2);