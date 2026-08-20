package com.likelion.tometa.domain.user.support;

import com.likelion.tometa.global.config.AnonymousSessionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AnonymousSessionCookieProvider {

    public static final String COOKIE_NAME = "anonymous_session";

    private final AnonymousSessionProperties properties;

    public ResponseCookie create(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(properties.expirationDays()))
                .build();
    }
}