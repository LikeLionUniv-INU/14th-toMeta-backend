package com.likelion.tometa.domain.report.listener;

import com.likelion.tometa.domain.record.event.DailyRecordCreatedEvent;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.support.DailyReportPublicationPolicy;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.report.scheduler.daily-generation-cron=0 0 0 * * *"
})
@Import({
        DailyReportOnRecordCreatedListener.class,
        DailyReportPublicationPolicy.class,
        DailyReportOnRecordCreatedListenerIntegrationTest.ClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailyReportOnRecordCreatedListenerIntegrationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private DailyReportGenerationService generationService;
    @MockitoBean
    private PushNotificationService pushNotificationService;

    @Test
    void committedRecordEvent_generatesOnlyAfterCommit() {
        User user = userRepository.saveAndFlush(User.builder().build());
        LocalDate reportDate = LocalDate.now(KOREA_ZONE).minusDays(1);
        when(generationService.generate(any(User.class), eq(reportDate)))
                .thenReturn(ReportGenerationResult.alreadyCompleted(null));

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new DailyRecordCreatedEvent(
                    user.getId(),
                    reportDate
            ));
            verifyNoInteractions(generationService);
        });

        verify(generationService).generate(any(User.class), eq(reportDate));
    }

    @Test
    void rolledBackRecordEvent_doesNotGenerate() {
        LocalDate reportDate = LocalDate.now(KOREA_ZONE).minusDays(1);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new DailyRecordCreatedEvent(1L, reportDate));
            status.setRollbackOnly();
        });

        verifyNoInteractions(generationService, pushNotificationService);
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
