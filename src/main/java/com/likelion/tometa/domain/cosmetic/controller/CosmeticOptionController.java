package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticOptionResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticOptionService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cosmetic-options")
public class CosmeticOptionController {

    private final CosmeticOptionService cosmeticOptionService;

    @GetMapping
    public ResponseEntity<ApiResponse<CosmeticOptionResponseDto>> getCosmeticOptions(
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        CosmeticOptionResponseDto result = cosmeticOptionService
                .getCosmeticOptions(sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
