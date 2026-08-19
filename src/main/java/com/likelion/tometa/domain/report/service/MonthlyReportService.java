package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.record.enums.SkinStatus;
import com.likelion.tometa.domain.report.dto.response.MonthlyReportListResponseDto;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.entity.WeeklyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.report.repository.WeeklyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class MonthlyReportService {

    private static final String COMPLETED_REPORT_STATUS = "completed";

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final DailyReportRepository dailyReportRepository;
    private final WeeklyReportRepository weeklyReportRepository;

    @Transactional
    public MonthlyReportListResponseDto getMonthlyReports(
            int year,
            int month,
            String sessionToken
    ) {
        validateYearMonth(year, month);

        User user = sessionUserResolver.resolve(sessionToken);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<DailyReport> dailyReports =
                dailyReportRepository
                        .findAllByDailyRecord_UserAndDailyRecord_RecordDateBetweenAndReportStatusOrderByDailyRecord_RecordDateAsc(
                                user,
                                startDate,
                                endDate,
                                COMPLETED_REPORT_STATUS
                        );

        Map<LocalDate, DailyReport> dailyReportByDate =
                dailyReports.stream()
                        .collect(Collectors.toMap(
                                report -> report.getDailyRecord().getRecordDate(),
                                Function.identity()
                        ));

        List<MonthlyReportListResponseDto.DailyReportItem> dailyReportItems =
                IntStream.rangeClosed(1, yearMonth.lengthOfMonth())
                        .mapToObj(day -> toDailyReportItem(
                                yearMonth.atDay(day),
                                dailyReportByDate
                        ))
                        .toList();

        List<MonthlyReportListResponseDto.WeeklyReportItem> weeklyReportItems =
                weeklyReportRepository
                        .findAllByUserAndWeekStartDateBetweenAndReportStatusOrderByWeekStartDateAsc(
                                user,
                                startDate,
                                endDate,
                                "completed"
                        )
                        .stream()
                        .map(report -> toWeeklyReportItem(yearMonth, report))
                        .toList();

        return new MonthlyReportListResponseDto(
                year,
                month,
                dailyReportItems,
                weeklyReportItems
        );
    }

    private MonthlyReportListResponseDto.DailyReportItem toDailyReportItem(
            LocalDate date,
            Map<LocalDate, DailyReport> dailyReportByDate
    ) {
        DailyReport report = dailyReportByDate.get(date);

        if (report == null) {
            return new MonthlyReportListResponseDto.DailyReportItem(
                    date,
                    false,
                    null
            );
        }

        return new MonthlyReportListResponseDto.DailyReportItem(
                date,
                true,
                toSkinCondition(report.getDailyRecord().getSkinStatus())
        );
    }

    private MonthlyReportListResponseDto.WeeklyReportItem toWeeklyReportItem(
            YearMonth yearMonth,
            WeeklyReport report
    ) {
        return new MonthlyReportListResponseDto.WeeklyReportItem(
                report.getId(),
                calculateWeekNumber(yearMonth, report.getWeekStartDate()),
                report.getWeekStartDate(),
                report.getWeekEndDate()
        );
    }

    private int calculateWeekNumber(
            YearMonth yearMonth,
            LocalDate weekStartDate
    ) {
        LocalDate firstMonday = yearMonth.atDay(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        return (int) ChronoUnit.WEEKS.between(
                firstMonday,
                weekStartDate
        ) + 1;
    }

    private String toSkinCondition(String skinStatus) {
        return SkinStatus.from(skinStatus)
                .map(SkinStatus::name)
                .orElse(null);
    }

    private void validateYearMonth(int year, int month) {
        if (year < 1 || month < 1 || month > 12) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }
}
