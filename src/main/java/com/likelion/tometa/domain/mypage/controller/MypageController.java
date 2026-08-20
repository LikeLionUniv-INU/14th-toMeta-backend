package com.likelion.tometa.domain.mypage.controller;

import com.likelion.tometa.domain.mypage.dto.request.NotificationSettingsUpdateRequestDto;
import com.likelion.tometa.domain.mypage.dto.response.MypageResponseDto;
import com.likelion.tometa.domain.mypage.dto.response.UserProfileResponseDto;
import com.likelion.tometa.domain.mypage.service.MypageService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class MypageController {

    private final MypageService mypageService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MypageResponseDto>> getMypage(
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        MypageResponseDto result = mypageService.getMypage(sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserProfileResponseDto>> getUserProfile(
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        UserProfileResponseDto result = mypageService.getUserProfile(sessionToken);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<Void>> updateNotificationSettings(
            @RequestBody NotificationSettingsUpdateRequestDto request,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        mypageService.updateNotificationSettings(request, sessionToken);

        return ResponseEntity.ok(ApiResponse.success());
    }
}
