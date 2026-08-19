package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.record.entity.RecordImageObject;
import com.likelion.tometa.domain.record.enums.RecordImageObjectStatus;
import com.likelion.tometa.domain.record.repository.RecordImageObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
@Import({
        RecordImageOwnershipService.class,
        RecordImageOwnershipConcurrencyIntegrationTest.ClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecordImageOwnershipConcurrencyIntegrationTest {

    private static final String OBJECT_KEY = "skin-images/1/image.jpg";

    @Autowired
    private RecordImageOwnershipService recordImageOwnershipService;

    @MockitoSpyBean
    private RecordImageObjectRepository recordImageObjectRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            recordImageObjectRepository.deleteAllInBatch();
            recordImageObjectRepository.saveAndFlush(RecordImageObject.pending(1L, OBJECT_KEY));
        });
    }

    @Test
    @Timeout(10)
    void attachmentAndCleanup_areSerializedByObjectKeyLock() throws Exception {
        CountDownLatch attachmentLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseAttachment = new CountDownLatch(1);
        CountDownLatch cleanupLockQueryEntered = new CountDownLatch(1);
        AtomicInteger lockInvocationCount = new AtomicInteger();

        // Spring Data 인터페이스 프록시는 호출할 실제 메서드가 없으므로 기본 응답으로 위임한다.
        Answer<?> repositoryDelegate = mockingDetails(recordImageObjectRepository)
                .getMockCreationSettings()
                .getDefaultAnswer();
        doAnswer(invocation -> {
            int invocationCount = lockInvocationCount.incrementAndGet();
            if (invocationCount == 2) {
                cleanupLockQueryEntered.countDown();
            }
            Object result = repositoryDelegate.answer(invocation);
            if (invocationCount == 1) {
                attachmentLockAcquired.countDown();
                await(releaseAttachment);
            }
            return result;
        }).when(recordImageObjectRepository).findByObjectKeyForUpdate(OBJECT_KEY);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> attachment = executor.submit(() ->
                    recordImageOwnershipService.claimForAttachment(1L, List.of(OBJECT_KEY))
            );
            assertTrue(attachmentLockAcquired.await(2, TimeUnit.SECONDS));

            Future<Optional<String>> cleanup = executor.submit(() ->
                    recordImageOwnershipService.claimForCleanup(OBJECT_KEY)
            );
            assertTrue(cleanupLockQueryEntered.await(2, TimeUnit.SECONDS));
            assertThrows(
                    TimeoutException.class,
                    () -> cleanup.get(200, TimeUnit.MILLISECONDS)
            );

            releaseAttachment.countDown();
            attachment.get(3, TimeUnit.SECONDS);
            assertTrue(cleanup.get(3, TimeUnit.SECONDS).isEmpty());
        } finally {
            releaseAttachment.countDown();
        }

        RecordImageObject object = transactionTemplate.execute(status ->
                recordImageObjectRepository.findAll().getFirst()
        );
        assertEquals(RecordImageObjectStatus.ATTACHED, object.getStatus());
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
