package com.likelion.tometa.domain.report.controller;

import com.likelion.tometa.domain.report.dto.request.DailyReportNoteUpdateRequestDto;
import com.likelion.tometa.domain.report.dto.request.WeeklyReportNoteUpdateRequestDto;
import com.likelion.tometa.domain.report.dto.response.DailyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.dto.response.DailyReportResponseDto;
import com.likelion.tometa.domain.report.dto.response.MonthlyReportListResponseDto;
import com.likelion.tometa.domain.report.dto.response.WeeklyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.dto.response.WeeklyReportResponseDto;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.service.DailyReportService;
import com.likelion.tometa.domain.report.service.MonthlyReportService;
import com.likelion.tometa.domain.report.service.WeeklyReportGenerationService;
import com.likelion.tometa.domain.report.service.WeeklyReportService;
import com.likelion.tometa.domain.user.support.AnonymousSessionCookieProvider;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final DailyReportService dailyReportService;
    private final DailyReportGenerationService dailyReportGenerationService;
    private final MonthlyReportService monthlyReportService;
    private final WeeklyReportService weeklyReportService;
    private final WeeklyReportGenerationService weeklyReportGenerationService;
    private final AnonymousSessionUserResolver sessionUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<MonthlyReportListResponseDto>> getMonthlyReports(
            @RequestParam int year,
            @RequestParam int month,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                monthlyReportService.getMonthlyReports(year, month, sessionToken)
        ));
    }

    @GetMapping("/daily/{date}")
    public ResponseEntity<ApiResponse<DailyReportResponseDto>> getDailyReport(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dailyReportService.getDailyReport(date, sessionToken)
        ));
    }

    @PostMapping("/daily/{date}/generate")
    public ResponseEntity<ApiResponse<DailyReportGenerationResponseDto>> generateDailyReport(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(
                dailyReportGenerationService.generate(user, date).response()
        ));
    }

    @PatchMapping("/daily/{date}/note")
    public ResponseEntity<ApiResponse<Void>> updateDailyReportNote(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DailyReportNoteUpdateRequestDto request,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        dailyReportService.updateNote(date, request, sessionToken);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/weekly/{reportId}")
    public ResponseEntity<ApiResponse<WeeklyReportResponseDto>> getWeeklyReport(
            @PathVariable Long reportId,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                weeklyReportService.getWeeklyReport(reportId, sessionToken)
        ));
    }

    @PatchMapping("/weekly/{reportId}/note")
    public ResponseEntity<ApiResponse<Void>> updateWeeklyReportNote(
            @PathVariable Long reportId,
            @Valid @RequestBody WeeklyReportNoteUpdateRequestDto request,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        weeklyReportService.updateNote(reportId, request, sessionToken);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/weekly/{startDate}/generate")
    public ResponseEntity<ApiResponse<WeeklyReportGenerationResponseDto>> generateWeeklyReport(
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @CookieValue(
                    name = AnonymousSessionCookieProvider.COOKIE_NAME,
                    required = false
            ) String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(
                weeklyReportGenerationService.generate(user, startDate).response()
        ));
    }
}
