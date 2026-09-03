package com.likelion.tometa.domain.report.listener;

import com.likelion.tometa.domain.report.event.DailyReportRegeneratedEvent;
import com.likelion.tometa.domain.report.service.WeeklyReportGenerationService;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReportOnDailyReportRegeneratedListenerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 26);
    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 8, 24);

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeeklyReportGenerationService generationService;

    private WeeklyReportOnDailyReportRegeneratedListener listener;
    private DailyReportRegeneratedEvent event;

    @BeforeEach
    void setUp() {
        listener = new WeeklyReportOnDailyReportRegeneratedListener(
                userRepository,
                generationService
        );

        event = new DailyReportRegeneratedEvent(
                USER_ID,
                REPORT_DATE
        );
    }

    @Test
    void dailyReportRegenerated_regeneratesContainingWeeklyReport() {
        User user = User.builder().build();

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));
        when(generationService.regenerate(user, WEEK_START_DATE))
                .thenReturn(ReportGenerationResult.generated(null));

        listener.regenerateWeeklyReport(event);

        verify(generationService).regenerate(
                user,
                WEEK_START_DATE
        );
    }

    @Test
    void weeklyReportRegenerationFailure_doesNotFailDailyReportRegeneration() {
        User user = User.builder().build();

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));
        when(generationService.regenerate(user, WEEK_START_DATE))
                .thenThrow(new RuntimeException("weekly generation failed"));

        assertDoesNotThrow(
                () -> listener.regenerateWeeklyReport(event)
        );
    }
}
