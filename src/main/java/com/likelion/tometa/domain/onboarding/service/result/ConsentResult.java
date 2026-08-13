package com.likelion.tometa.domain.onboarding.service.result;

public record ConsentResult(String sessionToken) {

    public static ConsentResult existingSession() {
        return new ConsentResult(null);
    }

    public static ConsentResult newSession(String sessionToken) {
        return new ConsentResult(sessionToken);
    }

    public boolean hasNewSession() {
        return sessionToken != null;
    }
}