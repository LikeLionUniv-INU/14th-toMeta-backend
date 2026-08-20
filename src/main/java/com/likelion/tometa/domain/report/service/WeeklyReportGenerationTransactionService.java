package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.health.repository.DailyHealthSummaryRepository;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.code.ReportErrorCode;
import com.likelion.tometa.domain.report.dto.response.WeeklyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.entity.WeeklyReportAnalysis;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportAnalysisRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.report.support.WeeklyReportAiResult;
import com.likelion.tometa.domain.report.support.WeeklyReportGenerationContext;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class WeeklyReportGenerationTransactionService {

    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(2);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String COMPLETED = "completed";
    private static final String GENERATING = "generating";
    private static final String FEMALE = "female";
    private static final String MALE = "male";

    private final UserRepository userRepository;
    private final DailyRecordRepository dailyRecordRepository;
    private final DailyReportRepository dailyReportRepository;
    private final DailyHealthSummaryRepository dailyHealthSummaryRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final WeeklyReportAnalysisRepository weeklyReportAnalysisRepository;

    @Transactional
    public Preparation prepare(
            User requestedUser,
            LocalDate startDate
    ) {
        validateStartDate(startDate);

        User user = userRepository.findWithLockById(requestedUser.getId())
                .orElseThrow(() -> new GeneralException(
                        GlobalErrorCode.INTERNAL_SERVER_ERROR
                ));

        LocalDate endDate = startDate.plusDays(6);

        WeeklyReport weeklyReport = weeklyReportRepository
                .findByUserAndWeekStartDateForUpdate(user, startDate)
                .orElseGet(() -> weeklyReportRepository.saveAndFlush(
                        WeeklyReport.builder()
                                .user(user)
                                .weekStartDate(startDate)
                                .weekEndDate(endDate)
                                .build()
                ));

        if (COMPLETED.equals(weeklyReport.getReportStatus())) {
            return Preparation.completed(
                    toResponse(
                            weeklyReport,
                            getAnalysisContents(weeklyReport)
                    )
            );
        }

        if (GENERATING.equals(weeklyReport.getReportStatus())) {
            if (!isGenerationExpired(weeklyReport)) {
                throw new GeneralException(
                        ReportErrorCode.WEEKLY_REPORT_GENERATION_IN_PROGRESS
                );
            }

            weeklyReport.markCollecting();
        }

        List<DailyRecord> dailyRecords = dailyRecordRepository
                .findAllByUserAndRecordDateBetween(
                        user,
                        startDate,
                        endDate
                );

        if (dailyRecords.isEmpty()) {
            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_SOURCE_NOT_FOUND
            );
        }

        List<DailyReport> dailyReports =
                dailyReportRepository
                        .findAllByDailyRecord_UserAndDailyRecord_RecordDateBetweenAndReportStatusOrderByDailyRecord_RecordDateAsc(
                                user,
                                startDate,
                                endDate,
                                COMPLETED
                        );

        List<DailyHealthSummary> healthSummaries =
                dailyHealthSummaryRepository
                        .findAllByUser_IdAndSummaryDateBetweenOrderBySummaryDateAsc(
                                user.getId(),
                                startDate,
                                endDate
                        );

        weeklyReport.markGenerating();

        return Preparation.pending(
                weeklyReport.getId(),
                weeklyReport.getGenerationStartedAt(),
                createContext(
                        user,
                        startDate,
                        endDate,
                        dailyRecords,
                        dailyReports,
                        healthSummaries
                )
        );
    }

    @Transactional
    public WeeklyReportGenerationResponseDto complete(
            Long reportId,
            LocalDateTime generationStartedAt,
            WeeklyReportAiResult aiResult
    ) {
        WeeklyReport weeklyReport = weeklyReportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() -> new GeneralException(
                        GlobalErrorCode.INTERNAL_SERVER_ERROR
                ));

        if (!isCurrentGeneration(
                weeklyReport,
                generationStartedAt
        )) {
            if (COMPLETED.equals(weeklyReport.getReportStatus())) {
                return toResponse(
                        weeklyReport,
                        getAnalysisContents(weeklyReport)
                );
            }

            throw new GeneralException(
                    ReportErrorCode.WEEKLY_REPORT_GENERATION_IN_PROGRESS
            );
        }

        weeklyReport.complete(
                aiResult.weeklySummary(),
                aiResult.personalizedSolution()
        );

        List<WeeklyReportAnalysis> analyses =
                IntStream.range(0, aiResult.analyses().size())
                        .mapToObj(index ->
                                WeeklyReportAnalysis.builder()
                                        .weeklyReport(weeklyReport)
                                        .content(aiResult.analyses().get(index))
                                        .sortOrder(index + 1)
                                        .build()
                        )
                        .toList();

        weeklyReportAnalysisRepository.saveAll(analyses);

        return toResponse(
                weeklyReport,
                aiResult.analyses()
        );
    }

    @Transactional
    public void reset(
            Long reportId,
            LocalDateTime generationStartedAt
    ) {
        weeklyReportRepository.findByIdForUpdate(reportId)
                .filter(report -> isCurrentGeneration(
                        report,
                        generationStartedAt
                ))
                .ifPresent(WeeklyReport::markCollecting);
    }

    private WeeklyReportGenerationContext createContext(
            User user,
            LocalDate startDate,
            LocalDate endDate,
            List<DailyRecord> dailyRecords,
            List<DailyReport> dailyReports,
            List<DailyHealthSummary> healthSummaries
    ) {
        Map<LocalDate, DailyRecord> recordByDate =
                dailyRecords.stream()
                        .collect(Collectors.toMap(
                                DailyRecord::getRecordDate,
                                Function.identity()
                        ));

        Map<LocalDate, DailyReport> reportByDate =
                dailyReports.stream()
                        .collect(Collectors.toMap(
                                report ->
                                        report.getDailyRecord().getRecordDate(),
                                Function.identity()
                        ));

        Map<LocalDate, DailyHealthSummary> healthByDate =
                healthSummaries.stream()
                        .collect(Collectors.toMap(
                                DailyHealthSummary::getSummaryDate,
                                Function.identity()
                        ));

        List<WeeklyReportGenerationContext.Day> days =
                IntStream.rangeClosed(0, 6)
                        .mapToObj(index -> {
                            LocalDate date =
                                    startDate.plusDays(index);

                            return toDay(
                                    user,
                                    date,
                                    recordByDate.get(date),
                                    reportByDate.get(date),
                                    healthByDate.get(date)
                            );
                        })
                        .toList();

        return new WeeklyReportGenerationContext(
                startDate,
                endDate,
                user.getGender(),
                days
        );
    }

    private WeeklyReportGenerationContext.Day toDay(
            User user,
            LocalDate date,
            DailyRecord dailyRecord,
            DailyReport report,
            DailyHealthSummary healthSummary
    ) {
        if (dailyRecord == null) {
            return new WeeklyReportGenerationContext.Day(
                    date,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    toHealthSummary(user, healthSummary)
            );
        }

        return new WeeklyReportGenerationContext.Day(
                date,
                true,
                dailyRecord.getSkinStatus(),
                dailyRecord.getFoodMemo(),
                dailyRecord.getMemo(),
                report == null ? null : report.getAiSummary(),
                report == null ? null : report.getAiAnalysis(),
                toHealthSummary(user, healthSummary)
        );
    }

    private WeeklyReportGenerationContext.HealthSummary toHealthSummary(
            User user,
            DailyHealthSummary summary
    ) {
        if (summary == null) {
            return null;
        }

        Integer menstrualCycleDay =
                FEMALE.equals(user.getGender())
                        ? summary.getMenstrualCycleDay()
                        : null;

        BigDecimal avgSpo2 =
                MALE.equals(user.getGender())
                        ? summary.getAvgSpo2()
                        : null;

        return new WeeklyReportGenerationContext.HealthSummary(
                summary.getSleepMinutes(),
                summary.getSkinTemperatureCelsius(),
                summary.getExerciseMinutes(),
                summary.getTotalCaloriesBurned(),
                menstrualCycleDay,
                avgSpo2
        );
    }

    private List<String> getAnalysisContents(
            WeeklyReport weeklyReport
    ) {
        return weeklyReportAnalysisRepository
                .findAllByWeeklyReportOrderBySortOrderAsc(weeklyReport)
                .stream()
                .map(WeeklyReportAnalysis::getContent)
                .toList();
    }

    private WeeklyReportGenerationResponseDto toResponse(
            WeeklyReport weeklyReport,
            List<String> analyses
    ) {
        return new WeeklyReportGenerationResponseDto(
                weeklyReport.getId(),
                weeklyReport.getWeekStartDate(),
                weeklyReport.getWeekEndDate(),
                weeklyReport.getReportStatus(),
                weeklyReport.getWeeklySummary(),
                analyses,
                weeklyReport.getPersonalizedSolution()
        );
    }

    private void validateStartDate(LocalDate startDate) {
        if (startDate == null
                || startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new GeneralException(
                    GlobalErrorCode.BAD_REQUEST
            );
        }

        LocalDate endDate = startDate.plusDays(6);

        if (!endDate.isBefore(LocalDate.now(KOREA_ZONE))) {
            throw new GeneralException(
                    GlobalErrorCode.BAD_REQUEST
            );
        }
    }

    private boolean isGenerationExpired(
            WeeklyReport weeklyReport
    ) {
        LocalDateTime generationStartedAt =
                weeklyReport.getGenerationStartedAt();

        return generationStartedAt == null
                || generationStartedAt
                .plus(GENERATION_TIMEOUT)
                .isBefore(LocalDateTime.now());
    }

    private boolean isCurrentGeneration(
            WeeklyReport weeklyReport,
            LocalDateTime generationStartedAt
    ) {
        return GENERATING.equals(
                weeklyReport.getReportStatus()
        ) && Objects.equals(
                weeklyReport.getGenerationStartedAt(),
                generationStartedAt
        );
    }

    public record Preparation(
            Long reportId,
            LocalDateTime generationStartedAt,
            WeeklyReportGenerationContext context,
            WeeklyReportGenerationResponseDto completedResponse
    ) {

        public static Preparation pending(
                Long reportId,
                LocalDateTime generationStartedAt,
                WeeklyReportGenerationContext context
        ) {
            return new Preparation(
                    reportId,
                    generationStartedAt,
                    context,
                    null
            );
        }

        public static Preparation completed(
                WeeklyReportGenerationResponseDto response
        ) {
            return new Preparation(
                    response.weeklyReportId(),
                    null,
                    null,
                    response
            );
        }

        public boolean requiresGeneration() {
            return context != null;
        }
    }
}
