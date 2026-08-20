package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.health.entity.HealthRawRecord;
import com.likelion.tometa.domain.health.repository.DailyHealthSummaryRepository;
import com.likelion.tometa.domain.health.repository.HealthRawRecordRepository;
import com.likelion.tometa.domain.report.code.ReportErrorCode;
import com.likelion.tometa.domain.report.dto.request.WeeklyReportNoteUpdateRequestDto;
import com.likelion.tometa.domain.report.dto.response.WeeklyReportResponseDto;
import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.entity.WeeklyReportAnalysis;
import com.likelion.tometa.domain.report.repository.WeeklyReportAnalysisRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final String COMPLETED = "completed";
    private static final String SLEEP_RECORD_TYPE = "SleepSessionRecord";
    private static final String FEMALE = "female";
    private static final String MALE = "male";
    private static final int CYCLE_LENGTH = 28;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int STAGE_AWAKE = 1;
    private static final int STAGE_AWAKE_OUT_OF_BED = 3;
    private static final int STAGE_SLEEPING_LIGHT = 4;
    private static final int STAGE_SLEEPING_DEEP = 5;
    private static final int STAGE_SLEEPING_REM = 6;
    private static final int STAGE_AWAKE_IN_BED = 7;

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final WeeklyReportRepository weeklyReportRepository;
    private final WeeklyReportAnalysisRepository weeklyReportAnalysisRepository;
    private final DailyHealthSummaryRepository dailyHealthSummaryRepository;
    private final HealthRawRecordRepository healthRawRecordRepository;
    private final JsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public WeeklyReportResponseDto getWeeklyReport(Long reportId, String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);

        WeeklyReport weeklyReport = weeklyReportRepository
                .findByIdAndUserAndReportStatus(reportId, user, COMPLETED)
                .orElseThrow(() -> new GeneralException(ReportErrorCode.WEEKLY_REPORT_NOT_FOUND));

        LocalDate startDate = weeklyReport.getWeekStartDate();
        LocalDate endDate = weeklyReport.getWeekEndDate();

        List<DailyHealthSummary> healthSummaries = dailyHealthSummaryRepository
                .findAllByUser_IdAndSummaryDateBetweenOrderBySummaryDateAsc(
                        user.getId(),
                        startDate,
                        endDate
                );

        Map<LocalDate, DailyHealthSummary> healthByDate = healthSummaries.stream()
                .collect(Collectors.toMap(
                        DailyHealthSummary::getSummaryDate,
                        Function.identity()
                ));

        Map<LocalDate, SleepAccumulator> sleepByDate = getSleepByDate(
                user,
                startDate,
                endDate
        );

        List<String> analyses = weeklyReportAnalysisRepository
                .findAllByWeeklyReportOrderBySortOrderAsc(weeklyReport)
                .stream()
                .map(WeeklyReportAnalysis::getContent)
                .toList();

        WeeklyReportResponseDto.HealthSummary healthSummary =
                new WeeklyReportResponseDto.HealthSummary(
                        createSleepSessions(startDate, healthByDate, sleepByDate),
                        createSkinTemperature(startDate, healthByDate),
                        createExerciseDuration(startDate, healthByDate),
                        createTotalCaloriesBurned(startDate, healthByDate),
                        createMenstrualCycle(user, startDate, healthByDate),
                        createAvgSpo2(user, startDate, healthByDate)
                );

        return new WeeklyReportResponseDto(
                weeklyReport.getId(),
                calculateWeekNumber(startDate),
                startDate,
                endDate,
                healthSummary,
                analyses,
                weeklyReport.getPersonalizedSolution(),
                weeklyReport.getNote()
        );
    }

    @Transactional
    public void updateNote(
            Long reportId,
            WeeklyReportNoteUpdateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        WeeklyReport weeklyReport = weeklyReportRepository
                .findByIdAndUserAndReportStatus(reportId, user, COMPLETED)
                .orElseThrow(() -> new GeneralException(
                        ReportErrorCode.WEEKLY_REPORT_NOT_FOUND
                ));

        String note = request.note().isBlank()
                ? null
                : request.note();

        weeklyReport.updateNote(note);
    }

    private List<WeeklyReportResponseDto.SleepSession> createSleepSessions(
            LocalDate startDate,
            Map<LocalDate, DailyHealthSummary> healthByDate,
            Map<LocalDate, SleepAccumulator> sleepByDate
    ) {
        return dates(startDate)
                .map(date -> {
                    SleepAccumulator sleep = sleepByDate.get(date);

                    if (sleep != null) {
                        return sleep.toResponse(date);
                    }

                    DailyHealthSummary summary = healthByDate.get(date);

                    if (summary != null && summary.getSleepMinutes() != null) {
                        return new WeeklyReportResponseDto.SleepSession(
                                date,
                                summary.getSleepMinutes(),
                                null,
                                null,
                                null,
                                null
                        );
                    }

                    return new WeeklyReportResponseDto.SleepSession(
                            date,
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                })
                .toList();
    }

    private List<WeeklyReportResponseDto.DecimalValue> createSkinTemperature(
            LocalDate startDate,
            Map<LocalDate, DailyHealthSummary> healthByDate
    ) {
        return dates(startDate)
                .map(date -> {
                    DailyHealthSummary summary = healthByDate.get(date);

                    return new WeeklyReportResponseDto.DecimalValue(
                            date,
                            summary == null
                                    ? null
                                    : summary.getSkinTemperatureCelsius()
                    );
                })
                .toList();
    }

    private List<WeeklyReportResponseDto.IntegerValue> createExerciseDuration(
            LocalDate startDate,
            Map<LocalDate, DailyHealthSummary> healthByDate
    ) {
        return dates(startDate)
                .map(date -> {
                    DailyHealthSummary summary = healthByDate.get(date);

                    return new WeeklyReportResponseDto.IntegerValue(
                            date,
                            summary == null
                                    ? null
                                    : summary.getExerciseMinutes()
                    );
                })
                .toList();
    }

    private List<WeeklyReportResponseDto.IntegerValue> createTotalCaloriesBurned(
            LocalDate startDate,
            Map<LocalDate, DailyHealthSummary> healthByDate
    ) {
        return dates(startDate)
                .map(date -> {
                    DailyHealthSummary summary = healthByDate.get(date);

                    return new WeeklyReportResponseDto.IntegerValue(
                            date,
                            summary == null
                                    ? null
                                    : summary.getTotalCaloriesBurned()
                    );
                })
                .toList();
    }

    private List<WeeklyReportResponseDto.MenstrualCycle> createMenstrualCycle(
            User user,
            LocalDate startDate,
            Map<LocalDate, DailyHealthSummary> healthByDate
    ) {
        if (!FEMALE.equals(user.getGender())) {
            return null;
        }

        return dates(startDate)
                .map(date -> {
                    DailyHealthSummary summary = healthByDate.get(date);

                    Integer menstrualCycleDay = summary == null
                            ? null
                            : summary.getMenstrualCycleDay();

                    return new WeeklyReportResponseDto.MenstrualCycle(
                            date,
                            menstrualCycleDay,
                            menstrualCycleDay == null
                                    ? null
                                    : CYCLE_LENGTH
                    );
                })
                .toList();
    }

    private List<WeeklyReportResponseDto.DecimalValue> createAvgSpo2(
            User user,
            LocalDate startDate,
            Map<LocalDate, DailyHealthSummary> healthByDate
    ) {
        if (!MALE.equals(user.getGender())) {
            return null;
        }

        return dates(startDate)
                .map(date -> {
                    DailyHealthSummary summary = healthByDate.get(date);

                    return new WeeklyReportResponseDto.DecimalValue(
                            date,
                            summary == null
                                    ? null
                                    : summary.getAvgSpo2()
                    );
                })
                .toList();
    }

    private Map<LocalDate, SleepAccumulator> getSleepByDate(
            User user,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LocalDateTime startUtc = startDate
                .atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        LocalDateTime endExclusiveUtc = endDate
                .plusDays(1)
                .atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        List<HealthRawRecord> records =
                healthRawRecordRepository
                        .findAllByUserAndRecordTypeAndEndTimeRange(
                                user,
                                SLEEP_RECORD_TYPE,
                                startUtc,
                                endExclusiveUtc
                        );

        Map<String, HealthRawRecord> uniqueRecords = new LinkedHashMap<>();

        records.forEach(record ->
                uniqueRecords.putIfAbsent(
                        record.getHcRecordId(),
                        record
                )
        );

        Map<LocalDate, SleepAccumulator> result = new HashMap<>();

        for (HealthRawRecord record : uniqueRecords.values()) {
            if (record.getEndTime() == null) {
                continue;
            }

            LocalDate date = toKoreaDate(record.getEndTime());

            if (date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }

            SleepAccumulator accumulator =
                    result.computeIfAbsent(
                            date,
                            ignored -> new SleepAccumulator()
                    );

            accumulator.addTotalDuration(
                    record.getStartTime(),
                    record.getEndTime()
            );

            addSleepStages(accumulator, record);
        }

        return result;
    }

    private void addSleepStages(
            SleepAccumulator accumulator,
            HealthRawRecord record
    ) {
        try {
            SleepPayload payload = jsonMapper.readValue(
                    record.getPayload(),
                    SleepPayload.class
            );

            if (payload == null || payload.stages() == null) {
                return;
            }

            for (SleepStagePayload stage : payload.stages().values()) {
                if (stage == null
                        || stage.stageType() == null
                        || stage.startTime() == null
                        || stage.endTime() == null) {
                    continue;
                }

                long seconds = Duration.between(
                        Instant.parse(stage.startTime()),
                        Instant.parse(stage.endTime())
                ).getSeconds();

                if (seconds <= 0) {
                    continue;
                }

                accumulator.addStage(
                        stage.stageType(),
                        seconds
                );
            }
        } catch (RuntimeException e) {
            log.warn(
                    "주간 리포트 수면 payload 파싱 실패. healthRawRecordId={}",
                    record.getId()
            );
        }
    }

    private int calculateWeekNumber(LocalDate startDate) {
        YearMonth yearMonth = YearMonth.from(startDate);

        LocalDate firstMonday = yearMonth
                .atDay(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        return (int) ChronoUnit.WEEKS.between(
                firstMonday,
                startDate
        ) + 1;
    }

    private LocalDate toKoreaDate(LocalDateTime utcDateTime) {
        return utcDateTime
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toLocalDate();
    }

    private java.util.stream.Stream<LocalDate> dates(
            LocalDate startDate
    ) {
        return IntStream.rangeClosed(0, 6)
                .mapToObj(startDate::plusDays);
    }

    private record SleepPayload(
            Map<String, SleepStagePayload> stages
    ) {
    }

    private record SleepStagePayload(
            String startTime,
            String endTime,
            Integer stageType
    ) {
    }

    private static class SleepAccumulator {

        private long totalSeconds;
        private long awakeSeconds;
        private long lightSeconds;
        private long deepSeconds;
        private long remSeconds;
        private boolean hasTotalDuration;
        private boolean hasClassifiedStage;

        private void addTotalDuration(
                LocalDateTime startTime,
                LocalDateTime endTime
        ) {
            if (startTime == null
                    || endTime == null
                    || !endTime.isAfter(startTime)) {
                return;
            }

            totalSeconds += Duration.between(
                    startTime,
                    endTime
            ).getSeconds();

            hasTotalDuration = true;
        }

        private void addStage(
                int stageType,
                long seconds
        ) {
            switch (stageType) {
                case STAGE_AWAKE,
                     STAGE_AWAKE_OUT_OF_BED,
                     STAGE_AWAKE_IN_BED -> {
                    awakeSeconds += seconds;
                    hasClassifiedStage = true;
                }

                case STAGE_SLEEPING_LIGHT -> {
                    lightSeconds += seconds;
                    hasClassifiedStage = true;
                }

                case STAGE_SLEEPING_DEEP -> {
                    deepSeconds += seconds;
                    hasClassifiedStage = true;
                }

                case STAGE_SLEEPING_REM -> {
                    remSeconds += seconds;
                    hasClassifiedStage = true;
                }

                default -> {
                }
            }
        }

        private WeeklyReportResponseDto.SleepSession toResponse(
                LocalDate date
        ) {
            return new WeeklyReportResponseDto.SleepSession(
                    date,
                    hasTotalDuration
                            ? toMinutes(totalSeconds)
                            : null,
                    hasClassifiedStage
                            ? toMinutes(awakeSeconds)
                            : null,
                    hasClassifiedStage
                            ? toMinutes(lightSeconds)
                            : null,
                    hasClassifiedStage
                            ? toMinutes(deepSeconds)
                            : null,
                    hasClassifiedStage
                            ? toMinutes(remSeconds)
                            : null
            );
        }

        private int toMinutes(long seconds) {
            return Math.toIntExact(
                    Math.round(seconds / 60.0)
            );
        }
    }
}