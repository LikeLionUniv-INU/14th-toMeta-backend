package com.likelion.tometa.domain.health.controller;

import com.likelion.tometa.domain.health.dto.request.HealthConnectionRequestDto;
import com.likelion.tometa.domain.health.dto.request.HealthSyncRequestDto;
import com.likelion.tometa.domain.health.dto.response.HealthConnectStatusResponseDto;
import com.likelion.tometa.domain.health.dto.response.HealthConnectionResponseDto;
import com.likelion.tometa.domain.health.service.HealthConnectService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/health-connect")
public class HealthConnectController {

    private final HealthConnectService healthConnectService;

    @PostMapping("/connections")
    public ResponseEntity<ApiResponse<HealthConnectionResponseDto>> connect(
            @Valid @RequestBody HealthConnectionRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        HealthConnectionResponseDto result = healthConnectService.connect(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<HealthConnectStatusResponseDto>> getStatus(
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        HealthConnectStatusResponseDto result = healthConnectService.getStatus(sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> sync(
            @Valid @RequestBody HealthSyncRequestDto request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        healthConnectService.sync(request, authorizationHeader);

        return ResponseEntity.ok(ApiResponse.success());
    }
}