package com.likelion.tometa.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.anonymous-session")
public record AnonymousSessionProperties(
        long expirationDays,
        boolean cookieSecure
) {
}