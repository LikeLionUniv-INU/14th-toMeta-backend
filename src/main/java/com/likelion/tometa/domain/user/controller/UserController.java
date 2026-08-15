package com.likelion.tometa.domain.user.controller;

import com.likelion.tometa.domain.user.dto.request.UserNotificationSettingRequestDto;
import com.likelion.tometa.domain.user.dto.request.UserProfileRequestDto;
import com.likelion.tometa.domain.user.service.UserService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<Void>> saveProfile(
            @Valid @RequestBody UserProfileRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        userService.saveProfile(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<Void>> saveNotificationSettings(
            @Valid @RequestBody UserNotificationSettingRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false) String sessionToken
    ) {
        userService.saveNotificationSettings(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success());
    }
}