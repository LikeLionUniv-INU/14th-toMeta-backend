package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.health.entity.DailyHealthSummary;
import com.likelion.tometa.domain.record.enums.SkinStatus;
import com.likelion.tometa.domain.report.code.ReportErrorCode;
import com.likelion.tometa.domain.report.dto.request.DailyReportNoteUpdateRequestDto;
import com.likelion.tometa.domain.report.dto.response.DailyReportResponseDto;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.tip.service.DailySkinCareTipService;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyReportService {

    private static final String COMPLETED_REPORT_STATUS = "completed";
    private static final String FEMALE = "female";
    private static final String MALE = "male";
    private static final int CYCLE_LENGTH = 28;

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final DailyReportRepository dailyReportRepository;
    private final DailySkinCareTipService dailySkinCareTipService;

    @Transactional
    public DailyReportResponseDto getDailyReport(
            LocalDate date,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        return dailyReportRepository
                .findByDailyRecord_UserAndDailyRecord_RecordDateAndReportStatus(
                        user,
                        date,
                        COMPLETED_REPORT_STATUS
                )
                .map(report -> toResponse(report, user))
                .orElseGet(() -> DailyReportResponseDto.notGenerated(date));
    }

    @Transactional
    public void updateNote(
            LocalDate date,
            DailyReportNoteUpdateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        DailyReport dailyReport = dailyReportRepository
                .findByDailyRecord_UserAndDailyRecord_RecordDateAndReportStatus(
                        user,
                        date,
                        COMPLETED_REPORT_STATUS
                )
                .orElseThrow(() -> new GeneralException(
                        ReportErrorCode.DAILY_REPORT_NOT_FOUND
                ));

        String note = request.note().isBlank()
                ? null
                : request.note();

        dailyReport.updateNote(note);
    }

    private DailyReportResponseDto toResponse(DailyReport report, User user) {
        LocalDate reportDate = report.getDailyRecord().getRecordDate();

        return new DailyReportResponseDto(
                reportDate,
                true,
                toSkinCondition(report.getDailyRecord().getSkinStatus()),
                toHealthSummary(report.getDailyHealthSummary(), user),
                report.getAiAnalysis(),
                report.getPersonalizedSolution(),
                report.getNote(),
                getDailyTip(user, reportDate)
        );
    }

    private DailyReportResponseDto.HealthSummary toHealthSummary(
            DailyHealthSummary summary,
            User user
    ) {
        if (summary == null) {
            return null;
        }

        DailyReportResponseDto.MenstrualCycle menstrualCycle = null;

        if (FEMALE.equals(user.getGender())) {
            menstrualCycle = new DailyReportResponseDto.MenstrualCycle(
                    summary.getMenstrualCycleDay(),
                    CYCLE_LENGTH
            );
        }

        return new DailyReportResponseDto.HealthSummary(
                summary.getSleepMinutes(),
                summary.getSkinTemperatureCelsius(),
                summary.getExerciseMinutes(),
                summary.getTotalCaloriesBurned(),
                menstrualCycle,
                MALE.equals(user.getGender()) ? summary.getAvgSpo2() : null
        );
    }

    private String toSkinCondition(String skinStatus) {
        return SkinStatus.from(skinStatus)
                .map(SkinStatus::name)
                .orElse(null);
    }

    private String getDailyTip(User user, LocalDate date) {
        try {
            return dailySkinCareTipService.assignOrGet(user, date);
        } catch (DataIntegrityViolationException e) {
            return dailySkinCareTipService.findAssignedTip(user, date)
                    .orElseThrow(() -> e);
        }
    }
}
