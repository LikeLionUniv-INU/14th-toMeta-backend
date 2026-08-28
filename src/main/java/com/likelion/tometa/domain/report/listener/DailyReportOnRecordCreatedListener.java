package com.likelion.tometa.domain.report.listener;

import com.likelion.tometa.domain.record.event.DailyRecordCreatedEvent;
import com.likelion.tometa.domain.report.service.DailyReportGenerationService;
import com.likelion.tometa.domain.report.support.DailyReportPublicationPolicy;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReportOnRecordCreatedListener {

    private final DailyReportPublicationPolicy publicationPolicy;
    private final UserRepository userRepository;
    private final DailyReportGenerationService generationService;
    private final PushNotificationService pushNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generateIfPublicationTimeReached(DailyRecordCreatedEvent event) {
        if (!publicationPolicy.isPublicationTimeReached(event.recordDate())) {
            return;
        }

        try {
            User user = userRepository.findById(event.userId()).orElseThrow();
            ReportGenerationResult<?> result = generationService.generate(
                    user,
                    event.recordDate()
            );
            if (result.generated()) {
                sendNotification(event);
            }
        } catch (RuntimeException e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(event.userId())
                    .addArgument(event.recordDate())
                    .log("Immediate daily report generation failed. userId={}, reportDate={}");
        }
    }

    private void sendNotification(DailyRecordCreatedEvent event) {
        try {
            pushNotificationService.sendDailyReportNotification(
                    event.userId(),
                    event.recordDate()
            );
        } catch (RuntimeException e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(event.userId())
                    .addArgument(event.recordDate())
                    .log("Immediate daily report notification failed. userId={}, reportDate={}");
        }
    }
}
