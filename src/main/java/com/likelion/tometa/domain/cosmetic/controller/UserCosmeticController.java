package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.request.SearchedCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.SearchedCosmeticCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.service.SearchedCosmeticRegistrationService;
import com.likelion.tometa.domain.cosmetic.service.UserCosmeticService;
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
@RequestMapping("/api/user-cosmetics")
public class UserCosmeticController {

    private final UserCosmeticService userCosmeticService;
    private final SearchedCosmeticRegistrationService searchedCosmeticRegistrationService;

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<Void>> createManualCosmetic(
            @Valid @RequestBody ManualCosmeticCreateRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        userCosmeticService.createManualCosmetic(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/search-result")
    public ResponseEntity<ApiResponse<SearchedCosmeticCreateResponseDto>> createSearchedCosmetic(
            @Valid @RequestBody SearchedCosmeticCreateRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        SearchedCosmeticCreateResponseDto result = searchedCosmeticRegistrationService.create(
                request,
                sessionToken
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{userCosmeticId}")
    public ResponseEntity<ApiResponse<Void>> deleteUserCosmetic(
            @PathVariable("userCosmeticId") Long userCosmeticId,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        userCosmeticService.deleteUserCosmetic(userCosmeticId, sessionToken);

        return ResponseEntity.ok(ApiResponse.success());
    }
}
