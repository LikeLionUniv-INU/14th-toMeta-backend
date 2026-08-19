package com.likelion.tometa.domain.record.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class RecordImageCleanupConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
