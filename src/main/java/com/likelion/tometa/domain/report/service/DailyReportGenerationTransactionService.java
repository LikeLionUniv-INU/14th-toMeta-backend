package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.health.repository.DailyHealthSummaryRepository;
import com.likelion.tometa.domain.record.code.RecordErrorCode;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmetic;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.code.ReportErrorCode;
import com.likelion.tometa.domain.report.dto.response.DailyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.report.support.DailyReportAiResult;
import com.likelion.tometa.domain.report.support.DailyReportGenerationContext;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyReportGenerationTransactionService {

    private static final String COMPLETED = "completed";
    private static final String GENERATING = "generating";
    private static final String FEMALE = "female";
    private static final String MALE = "male";
    private static final String MORNING = "morning";
    private static final String NIGHT = "night";
    private static final int CYCLE_LENGTH = 28;

    private final DailyRecordRepository dailyRecordRepository;
    private final DailyRecordCosmeticRepository dailyRecordCosmeticRepository;
    private final DailyHealthSummaryRepository dailyHealthSummaryRepository;
    private final DailyReportRepository dailyReportRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public Preparation prepare(User user, LocalDate date) {
        DailyRecord dailyRecord = dailyRecordRepository
                .findByUserAndRecordDateForUpdate(user, date)
                .orElseThrow(() -> new GeneralException(
                        RecordErrorCode.DAILY_RECORD_NOT_FOUND
                ));

        DailyReport dailyReport = dailyReportRepository
                .findByDailyRecordForUpdate(dailyRecord)
                .orElseGet(() -> dailyReportRepository.saveAndFlush(
                        DailyReport.builder()
                                .dailyRecord(dailyRecord)
                                .build()
                ));

        if (COMPLETED.equals(dailyReport.getReportStatus())) {
            return Preparation.completed(toResponse(dailyReport));
        }

        if (GENERATING.equals(dailyReport.getReportStatus())) {
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_GENERATION_IN_PROGRESS
            );
        }

        DailyHealthSummary healthSummary = dailyHealthSummaryRepository
                .findByUser_IdAndSummaryDate(user.getId(), date)
                .orElse(null);

        List<DailyRecordCosmetic> cosmetics =
                dailyRecordCosmeticRepository
                        .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(
                                dailyRecord
                        );

        long generationVersion = dailyReport.getGenerationVersion();
        int markedGenerating = dailyReportRepository.markGeneratingIfCurrent(
                dailyReport.getId(),
                generationVersion
        );
        if (markedGenerating == 0) {
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_GENERATION_IN_PROGRESS
            );
        }

        return Preparation.pending(
                dailyReport.getId(),
                generationVersion,
                healthSummary == null ? null : healthSummary.getId(),
                createContext(
                        user,
                        dailyRecord,
                        cosmetics,
                        healthSummary
                )
        );
    }

    @Transactional
    public DailyReportGenerationResponseDto complete(
            Long reportId,
            long generationVersion,
            Long healthSummaryId,
            DailyReportAiResult aiResult
    ) {
        DailyHealthSummary healthSummary = healthSummaryId == null
                ? null
                : dailyHealthSummaryRepository.findById(healthSummaryId)
                .orElse(null);

        int completed = dailyReportRepository.completeGenerationIfCurrent(
                reportId,
                generationVersion,
                healthSummary,
                aiResult.aiSummary(),
                aiResult.aiAnalysis(),
                aiResult.personalizedSolution(),
                LocalDateTime.now()
        );
        if (completed == 0) {
            throw new GeneralException(
                    ReportErrorCode.DAILY_REPORT_GENERATION_STALE
            );
        }

        return toResponse(dailyReportRepository.findById(reportId).orElseThrow());
    }

    @Transactional
    public void reset(Long reportId, long generationVersion) {
        dailyReportRepository.resetGenerationIfCurrent(
                reportId,
                generationVersion
        );
    }

    private DailyReportGenerationContext createContext(
            User user,
            DailyRecord dailyRecord,
            List<DailyRecordCosmetic> cosmetics,
            DailyHealthSummary healthSummary
    ) {
        List<DailyReportGenerationContext.Cosmetic> morningCosmetics =
                toCosmetics(cosmetics, MORNING);

        List<DailyReportGenerationContext.Cosmetic> nightCosmetics =
                toCosmetics(cosmetics, NIGHT);

        return new DailyReportGenerationContext(
                dailyRecord.getRecordDate(),
                user.getGender(),
                dailyRecord.getSkinStatus(),
                morningCosmetics,
                nightCosmetics,
                dailyRecord.getFoodMemo(),
                dailyRecord.getMemo(),
                toHealthSummary(user, healthSummary)
        );
    }

    private List<DailyReportGenerationContext.Cosmetic> toCosmetics(
            List<DailyRecordCosmetic> cosmetics,
            String usagePeriod
    ) {
        return cosmetics.stream()
                .filter(cosmetic ->
                        usagePeriod.equals(cosmetic.getUsagePeriod()))
                .map(cosmetic ->
                        new DailyReportGenerationContext.Cosmetic(
                                cosmetic.getProductNameSnapshot(),
                                cosmetic.getProductTypeSnapshot(),
                                extractIngredientNames(
                                        cosmetic.getIngredientsSnapshot()
                                )
                        ))
                .toList();
    }

    private List<String> extractIngredientNames(String ingredientsSnapshot) {
        if (ingredientsSnapshot == null || ingredientsSnapshot.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = jsonMapper.readTree(ingredientsSnapshot);

            if (!root.isArray()) {
                return List.of();
            }

            List<String> ingredients = new ArrayList<>();

            for (JsonNode ingredient : root) {
                String name = ingredient.path("name").asText();

                if (!name.isBlank()) {
                    ingredients.add(name);
                }
            }

            return List.copyOf(ingredients);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private DailyReportGenerationContext.HealthSummary toHealthSummary(
            User user,
            DailyHealthSummary healthSummary
    ) {
        if (healthSummary == null) {
            return null;
        }

        DailyReportGenerationContext.MenstrualCycle menstrualCycle = null;

        if (FEMALE.equals(user.getGender())) {
            menstrualCycle =
                    new DailyReportGenerationContext.MenstrualCycle(
                            healthSummary.getMenstrualCycleDay(),
                            CYCLE_LENGTH
                    );
        }

        return new DailyReportGenerationContext.HealthSummary(
                healthSummary.getSleepMinutes(),
                healthSummary.getSkinTemperatureCelsius(),
                healthSummary.getExerciseMinutes(),
                healthSummary.getTotalCaloriesBurned(),
                menstrualCycle,
                MALE.equals(user.getGender())
                        ? healthSummary.getAvgSpo2()
                        : null
        );
    }

    private DailyReportGenerationResponseDto toResponse(
            DailyReport dailyReport
    ) {
        return new DailyReportGenerationResponseDto(
                dailyReport.getId(),
                dailyReport.getDailyRecord().getRecordDate(),
                dailyReport.getReportStatus(),
                dailyReport.getAiSummary(),
                dailyReport.getAiAnalysis(),
                dailyReport.getPersonalizedSolution()
        );
    }

    public record Preparation(
            Long reportId,
            long generationVersion,
            Long healthSummaryId,
            DailyReportGenerationContext context,
            DailyReportGenerationResponseDto completedResponse
    ) {

        public static Preparation pending(
                Long reportId,
                long generationVersion,
                Long healthSummaryId,
                DailyReportGenerationContext context
        ) {
            return new Preparation(
                    reportId,
                    generationVersion,
                    healthSummaryId,
                    context,
                    null
            );
        }

        public static Preparation completed(
                DailyReportGenerationResponseDto response
        ) {
            return new Preparation(
                    response.dailyReportId(),
                    0L,
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
