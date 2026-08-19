package com.likelion.tometa.domain.cosmetic.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCacheEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CosmeticSearchCacheConfig {

    private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(10);
    private static final long CACHE_MAX_SIZE = 1_000;

    @Bean
    public Cache<String, CosmeticSearchCacheEntry> cosmeticSearchCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRATION)
                .maximumSize(CACHE_MAX_SIZE)
                .build();
    }
}
