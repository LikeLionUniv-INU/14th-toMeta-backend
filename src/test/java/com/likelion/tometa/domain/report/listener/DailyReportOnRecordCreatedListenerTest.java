package com.likelion.tometa.domain.report.listener;

import com.likelion.tometa.domain.record.event.DailyRecordCreatedEvent;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.support.DailyReportPublicationPolicy;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyReportOnRecordCreatedListenerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 24);

    @Mock
    private DailyReportPublicationPolicy publicationPolicy;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DailyReportGenerationService generationService;
    @Mock
    private PushNotificationService pushNotificationService;

    private DailyReportOnRecordCreatedListener listener;
    private DailyRecordCreatedEvent event;

    @BeforeEach
    void setUp() {
        listener = new DailyReportOnRecordCreatedListener(
                publicationPolicy,
                userRepository,
                generationService,
                pushNotificationService
        );
        event = new DailyRecordCreatedEvent(USER_ID, REPORT_DATE);
    }

    @Test
    void recordCreatedBeforePublicationTime_doesNotGenerateReport() {
        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(false);

        listener.generateIfPublicationTimeReached(event);

        verifyNoInteractions(userRepository, generationService, pushNotificationService);
    }

    @Test
    void recordCreatedAfterPublicationTime_generatesReportImmediately() {
        User user = User.builder().build();
        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(generationService.generate(user, REPORT_DATE))
                .thenReturn(ReportGenerationResult.generated(null));

        listener.generateIfPublicationTimeReached(event);

        verify(generationService).generate(user, REPORT_DATE);
        verify(pushNotificationService)
                .sendDailyReportNotification(USER_ID, REPORT_DATE);
    }

    @Test
    void alreadyGeneratedReport_isNotGeneratedOrNotifiedAgain() {
        User user = User.builder().build();
        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(generationService.generate(user, REPORT_DATE))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        listener.generateIfPublicationTimeReached(event);

        verify(generationService).generate(user, REPORT_DATE);
        verify(pushNotificationService, never())
                .sendDailyReportNotification(USER_ID, REPORT_DATE);
    }

    @Test
    void generationFailure_doesNotFailCommittedRecordRequest() {
        User user = User.builder().build();
        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(generationService.generate(user, REPORT_DATE))
                .thenThrow(new RuntimeException("generation failed"));

        assertDoesNotThrow(() -> listener.generateIfPublicationTimeReached(event));

        verify(pushNotificationService, never())
                .sendDailyReportNotification(USER_ID, REPORT_DATE);
    }
}
