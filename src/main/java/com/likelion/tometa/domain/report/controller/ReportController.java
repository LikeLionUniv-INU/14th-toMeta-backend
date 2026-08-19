package com.likelion.tometa.domain.report.controller;

import com.likelion.tometa.domain.report.dto.response.DailyReportResponseDto;
import com.likelion.tometa.domain.report.service.DailyReportService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final DailyReportService dailyReportService;

    @GetMapping("/daily/{date}")
    public ResponseEntity<ApiResponse<DailyReportResponseDto>> getDailyReport(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            )
            String sessionToken
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dailyReportService.getDailyReport(date, sessionToken))
        );
    }
}
