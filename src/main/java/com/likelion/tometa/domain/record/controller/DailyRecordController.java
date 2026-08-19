package com.likelion.tometa.domain.record.controller;

import com.likelion.tometa.domain.record.dto.request.DailyRecordCreateRequestDto;
import com.likelion.tometa.domain.record.dto.request.DailyRecordUpdateRequestDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordCreateResponseDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordDetailResponseDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordUpdateResponseDto;
import com.likelion.tometa.domain.record.service.DailyRecordService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/daily-records")
public class DailyRecordController {

    private final DailyRecordService dailyRecordService;

    @PostMapping
    public ResponseEntity<ApiResponse<DailyRecordCreateResponseDto>> createDailyRecord(
            @Valid @RequestBody DailyRecordCreateRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        DailyRecordCreateResponseDto result = dailyRecordService.create(request, sessionToken);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{date}")
    public ResponseEntity<ApiResponse<DailyRecordDetailResponseDto>> getDailyRecord(
            @PathVariable("date") LocalDate date,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        DailyRecordDetailResponseDto result = dailyRecordService.getByDate(
                date,
                sessionToken
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{date}")
    public ResponseEntity<ApiResponse<DailyRecordUpdateResponseDto>> updateDailyRecord(
            @PathVariable("date") LocalDate date,
            @Valid @RequestBody DailyRecordUpdateRequestDto request,
            @CookieValue(name = AnonymousSessionCookieProvider.COOKIE_NAME, required = false)
            String sessionToken
    ) {
        DailyRecordUpdateResponseDto result = dailyRecordService.update(
                date,
                request,
                sessionToken
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
