package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSearchResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSearchService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cosmetics")
public class CosmeticSearchController {

    private final CosmeticSearchService cosmeticSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<CosmeticSearchResponseDto>> searchCosmetics(
            @RequestParam(value = "keyword", required = false) String keyword,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        CosmeticSearchResponseDto result = cosmeticSearchService.search(keyword, sessionToken);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
