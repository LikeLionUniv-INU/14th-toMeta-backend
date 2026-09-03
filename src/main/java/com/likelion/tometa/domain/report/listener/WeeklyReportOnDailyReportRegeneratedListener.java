package com.likelion.tometa.domain.report.listener;

import com.likelion.tometa.domain.report.event.DailyReportRegeneratedEvent;
import com.likelion.tometa.domain.report.service.WeeklyReportGenerationService;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportOnDailyReportRegeneratedListener {

    private final UserRepository userRepository;
    private final WeeklyReportGenerationService generationService;

    @EventListener
    public void regenerateWeeklyReport(DailyReportRegeneratedEvent event) {
        LocalDate weekStartDate = event.reportDate().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        try {
            User user = userRepository.findById(event.userId()).orElseThrow();

            generationService.regenerate(
                    user,
                    weekStartDate
            );
        } catch (RuntimeException e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(event.userId())
                    .addArgument(weekStartDate)
                    .log("Weekly report regeneration after daily report update failed. "
                            + "userId={}, weekStartDate={}");
        }
    }
}
