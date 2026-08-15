package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSetService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cosmetic-sets")
public class CosmeticSetController {

    private final CosmeticSetService cosmeticSetService;

    @PostMapping
    public ResponseEntity<ApiResponse<CosmeticSetCreateResponseDto>> createCosmeticSet(
            @Valid @RequestBody CosmeticSetCreateRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        CosmeticSetCreateResponseDto result = cosmeticSetService
                .createCosmeticSet(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
