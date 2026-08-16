package com.likelion.tometa.domain.home.service;

import com.likelion.tometa.domain.home.dto.response.HomeResponseDto;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.tip.service.DailySkinCareTipService;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String COMPLETED_REPORT_STATUS = "completed";

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final DailyRecordRepository dailyRecordRepository;
    private final DailyReportRepository dailyReportRepository;
    private final DailySkinCareTipService dailySkinCareTipService;

    @Transactional
    public HomeResponseDto getHome(String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);
        LocalDate today = LocalDate.now(KOREA_ZONE);

        return new HomeResponseDto(
                user.getNickname(),
                getWeek(user, today),
                getYesterdayReport(user, today.minusDays(1)),
                getLatestDailyReport(user),
                getDailyTip(user, today)
        );
    }

    private HomeResponseDto.Week getWeek(User user, LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        Map<LocalDate, DailyRecord> records = dailyRecordRepository
                .findAllByUserAndRecordDateBetween(user, weekStart, weekEnd)
                .stream()
                .collect(Collectors.toMap(DailyRecord::getRecordDate, Function.identity()));

        List<HomeResponseDto.Day> days = IntStream.range(0, 7)
                .mapToObj(index -> {
                    LocalDate date = weekStart.plusDays(index);
                    DailyRecord record = records.get(date);
                    return new HomeResponseDto.Day(date, record == null ? null : record.getSkinStatus());
                })
                .toList();

        return new HomeResponseDto.Week(weekStart, weekEnd, days);
    }

    private HomeResponseDto.YesterdayReport getYesterdayReport(User user, LocalDate yesterday) {
        Optional<DailyRecord> dailyRecord = dailyRecordRepository.findByUserAndRecordDate(user, yesterday);

        if (dailyRecord.isEmpty()) {
            return new HomeResponseDto.YesterdayReport(false, false, null, null, null);
        }

        Optional<DailyReport> dailyReport = dailyReportRepository.findByDailyRecord(dailyRecord.get());

        if (dailyReport.isEmpty() || !COMPLETED_REPORT_STATUS.equals(dailyReport.get().getReportStatus())) {
            return new HomeResponseDto.YesterdayReport(true, false, null, null, null);
        }

        DailyReport report = dailyReport.get();

        return new HomeResponseDto.YesterdayReport(
                true,
                true,
                report.getId(),
                report.getAiSummary(),
                report.getPersonalizedSolution()
        );
    }

    private HomeResponseDto.LatestDailyReport getLatestDailyReport(User user) {
        return dailyReportRepository
                .findFirstByDailyRecord_UserAndReportStatusOrderByDailyRecord_RecordDateDesc(
                        user,
                        COMPLETED_REPORT_STATUS
                )
                .map(report -> new HomeResponseDto.LatestDailyReport(
                        report.getId(),
                        report.getDailyRecord().getRecordDate()
                ))
                .orElse(null);
    }

    private String getDailyTip(User user, LocalDate today) {
        try {
            return dailySkinCareTipService.assignOrGet(user, today);
        } catch (DataIntegrityViolationException e) {
            return dailySkinCareTipService.findAssignedTip(user, today).orElseThrow(() -> e);
        }
    }
}