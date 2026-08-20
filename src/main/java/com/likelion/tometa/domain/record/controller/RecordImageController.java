package com.likelion.tometa.domain.record.controller;

import com.likelion.tometa.domain.record.dto.request.RecordImageUploadUrlRequestDto;
import com.likelion.tometa.domain.record.dto.response.RecordImageUploadUrlResponseDto;
import com.likelion.tometa.domain.record.service.RecordImageStorageService;
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
@RequestMapping("/api")
public class RecordImageController {

    private final RecordImageStorageService recordImageStorageService;

    @PostMapping({
            "/record-images/presigned-upload-url",
            "/images/presigned-upload-urls"
    })
    public ResponseEntity<ApiResponse<RecordImageUploadUrlResponseDto>> issueUploadUrl(
            @Valid @RequestBody RecordImageUploadUrlRequestDto request,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        RecordImageUploadUrlResponseDto result =
                recordImageStorageService.issueUploadUrl(request, sessionToken);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
