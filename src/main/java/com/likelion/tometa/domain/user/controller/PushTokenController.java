package com.likelion.tometa.domain.user.controller;

import com.likelion.tometa.domain.user.dto.request.PushTokenRegisterRequestDto;
import com.likelion.tometa.domain.user.dto.response.PushTokenRegisterResponseDto;
import com.likelion.tometa.domain.user.service.PushTokenService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/push-tokens")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<PushTokenRegisterResponseDto>> register(
            @Valid @RequestBody PushTokenRegisterRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        PushTokenRegisterResponseDto result = pushTokenService.register(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{pushTokenId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long pushTokenId,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        pushTokenService.delete(pushTokenId, sessionToken);

        return ResponseEntity.ok(ApiResponse.success());
    }
}