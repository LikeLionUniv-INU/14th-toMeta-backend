package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequest;
import com.likelion.tometa.domain.cosmetic.dto.response.ManualCosmeticCreateResponse;
import com.likelion.tometa.domain.cosmetic.service.UserCosmeticService;
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
@RequestMapping("/api/user-cosmetics")
public class UserCosmeticController {

    private final UserCosmeticService userCosmeticService;

    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<ManualCosmeticCreateResponse>> createManualCosmetic(
            @Valid @RequestBody ManualCosmeticCreateRequest request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        ManualCosmeticCreateResponse result =
                userCosmeticService.createManualCosmetic(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
