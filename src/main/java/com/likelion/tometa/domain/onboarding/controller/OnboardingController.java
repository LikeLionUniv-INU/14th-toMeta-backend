package com.likelion.tometa.domain.onboarding.controller;

import com.likelion.tometa.domain.onboarding.dto.request.ConsentRequestDto;
import com.likelion.tometa.domain.onboarding.dto.response.OnboardingStatusResponseDto;
import com.likelion.tometa.domain.onboarding.service.result.ConsentResult;
import com.likelion.tometa.domain.onboarding.service.OnboardingService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AnonymousSessionCookieProvider cookieProvider;

    @PostMapping("/consents")
    public ResponseEntity<ApiResponse<Void>> agreeToConsents(
            @Valid @RequestBody ConsentRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        ConsentResult result = onboardingService.agreeToConsents(request, sessionToken);

        if (!result.hasNewSession()) {
            return ResponseEntity.ok(ApiResponse.success());
        }

        String cookie = cookieProvider.create(result.sessionToken()).toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(ApiResponse.success());
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<OnboardingStatusResponseDto>> getOnboardingStatus(
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        OnboardingStatusResponseDto result = onboardingService.getOnboardingStatus(sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}