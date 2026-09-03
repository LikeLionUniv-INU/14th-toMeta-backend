package com.likelion.tometa.domain.report.listener;

import com.likelion.tometa.domain.record.event.DailyRecordUpdatedEvent;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.support.DailyReportPublicationPolicy;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyReportOnRecordUpdatedListenerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 24);

    @Mock
    private DailyReportPublicationPolicy publicationPolicy;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DailyReportGenerationService generationService;

    private DailyReportOnRecordUpdatedListener listener;
    private DailyRecordUpdatedEvent event;

    @BeforeEach
    void setUp() {
        listener = new DailyReportOnRecordUpdatedListener(
                publicationPolicy,
                userRepository,
                generationService
        );

        event = new DailyRecordUpdatedEvent(
                USER_ID,
                REPORT_DATE
        );
    }

    @Test
    void recordUpdatedBeforePublicationTime_doesNotRegenerateReport() {
        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(false);

        listener.regenerateIfPublicationTimeReached(event);

        verifyNoInteractions(
                userRepository,
                generationService
        );
    }

    @Test
    void recordUpdatedAfterPublicationTime_regeneratesReportImmediately() {
        User user = User.builder().build();

        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(true);
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        listener.regenerateIfPublicationTimeReached(event);

        verify(generationService).generate(
                user,
                REPORT_DATE
        );
    }

    @Test
    void regenerationFailure_doesNotFailCommittedRecordUpdate() {
        User user = User.builder().build();

        when(publicationPolicy.isPublicationTimeReached(REPORT_DATE))
                .thenReturn(true);
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));
        when(generationService.generate(user, REPORT_DATE))
                .thenThrow(new RuntimeException("generation failed"));

        assertDoesNotThrow(
                () -> listener.regenerateIfPublicationTimeReached(event)
        );
    }
}
