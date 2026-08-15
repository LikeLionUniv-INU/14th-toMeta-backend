package com.likelion.tometa.domain.onboarding.dto.response;

public record OnboardingStatusResponseDto(
        boolean profileCompleted,
        boolean healthConnectLinked,
        boolean notificationSettingsCompleted
) {
}
