package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetUpdateRequestDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticProductRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
@Import({CosmeticSetService.class, UserCosmeticService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CosmeticSetConcurrencyIntegrationTest {

    private static final String FIRST_SESSION_TOKEN = "first-session-token";
    private static final String SECOND_SESSION_TOKEN = "second-session-token";

    @Autowired
    private CosmeticSetService cosmeticSetService;

    @Autowired
    private UserCosmeticService userCosmeticService;

    @MockitoSpyBean
    private CosmeticSetRepository cosmeticSetRepository;

    @Autowired
    private CosmeticSetItemRepository cosmeticSetItemRepository;

    @Autowired
    private CosmeticProductRepository cosmeticProductRepository;

    @Autowired
    private UserCosmeticRepository userCosmeticRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AnonymousSessionUserResolver sessionUserResolver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private User user;
    private Long cosmeticSetId;
    private List<Long> firstUserCosmeticIds;
    private List<Long> secondUserCosmeticIds;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            cosmeticSetItemRepository.deleteAllInBatch();
            cosmeticSetRepository.deleteAllInBatch();
            userCosmeticRepository.deleteAllInBatch();
            cosmeticProductRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();

            user = userRepository.save(User.builder().build());

            List<CosmeticProduct> products = cosmeticProductRepository.saveAll(List.of(
                    cosmeticProduct("첫 번째 토너"),
                    cosmeticProduct("첫 번째 크림"),
                    cosmeticProduct("두 번째 세럼"),
                    cosmeticProduct("두 번째 로션")
            ));
            List<UserCosmetic> userCosmetics = userCosmeticRepository.saveAll(
                    products.stream()
                            .map(product -> UserCosmetic.builder()
                                    .user(user)
                                    .cosmeticProduct(product)
                                    .build())
                            .toList()
            );
            firstUserCosmeticIds = List.of(
                    userCosmetics.get(0).getId(),
                    userCosmetics.get(1).getId()
            );
            secondUserCosmeticIds = List.of(
                    userCosmetics.get(2).getId(),
                    userCosmetics.get(3).getId()
            );

            CosmeticSet cosmeticSet = cosmeticSetRepository.save(
                    CosmeticSet.builder()
                            .user(user)
                            .name("기존 세트")
                            .usageTime(CosmeticSetUsageTime.MORNING)
                            .build()
            );
            cosmeticSetId = cosmeticSet.getId();
        });

        when(sessionUserResolver.resolve(anyString())).thenReturn(user);
    }

    @Test
    @Timeout(10)
    void concurrentPatchRequests_areSerializedByCosmeticSetLock() throws Exception {
        CountDownLatch firstPatchReplacedItems = new CountDownLatch(1);
        CountDownLatch releaseFirstPatch = new CountDownLatch(1);
        CountDownLatch secondLockQueryEntered = new CountDownLatch(1);
        CountDownLatch secondPatchReplacedItems = new CountDownLatch(1);
        AtomicInteger lockQueryInvocationCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> firstPatch = null;
        Future<?> secondPatch = null;

        try {
            Answer<?> repositoryDelegate = mockingDetails(cosmeticSetRepository)
                    .getMockCreationSettings()
                    .getDefaultAnswer();
            doAnswer(invocation -> {
                if (lockQueryInvocationCount.incrementAndGet() == 2) {
                    secondLockQueryEntered.countDown();
                }
                return repositoryDelegate.answer(invocation);
            }).when(cosmeticSetRepository).findByIdAndUser(cosmeticSetId, user);

            firstPatch = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                cosmeticSetService.updateCosmeticSet(
                        cosmeticSetId,
                        itemsUpdateRequest(firstUserCosmeticIds),
                        FIRST_SESSION_TOKEN
                );
                firstPatchReplacedItems.countDown();
                await(releaseFirstPatch);
            }));

            boolean firstPatchCompletedUpdate = firstPatchReplacedItems.await(
                    2,
                    TimeUnit.SECONDS
            );
            if (!firstPatchCompletedUpdate && firstPatch.isDone()) {
                firstPatch.get(1, TimeUnit.SECONDS);
            }
            assertTrue(firstPatchCompletedUpdate);

            secondPatch = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    cosmeticSetService.updateCosmeticSet(
                            cosmeticSetId,
                            itemsUpdateRequest(secondUserCosmeticIds),
                            SECOND_SESSION_TOKEN
                    );
                    secondPatchReplacedItems.countDown();
                });
            });

            assertTrue(secondLockQueryEntered.await(2, TimeUnit.SECONDS));
            assertFalse(secondPatchReplacedItems.await(500, TimeUnit.MILLISECONDS));

            releaseFirstPatch.countDown();
            firstPatch.get(3, TimeUnit.SECONDS);
            secondPatch.get(3, TimeUnit.SECONDS);

            List<Long> finalUserCosmeticIds = transactionTemplate.execute(status ->
                    cosmeticSetItemRepository.findAll().stream()
                            .sorted(Comparator.comparing(CosmeticSetItem::getItemOrder))
                            .map(item -> item.getUserCosmetic().getId())
                            .toList()
            );

            assertEquals(secondUserCosmeticIds, finalUserCosmeticIds);
        } finally {
            releaseFirstPatch.countDown();
            if (firstPatch != null) {
                firstPatch.cancel(true);
            }
            if (secondPatch != null) {
                secondPatch.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    @Test
    void deleteCosmeticSet_removesItemsAndOwnedSet() {
        transactionTemplate.executeWithoutResult(status -> cosmeticSetService.updateCosmeticSet(
                cosmeticSetId,
                itemsUpdateRequest(firstUserCosmeticIds),
                FIRST_SESSION_TOKEN
        ));
        assertEquals(2L, cosmeticSetItemRepository.count());

        cosmeticSetService.deleteCosmeticSet(cosmeticSetId, FIRST_SESSION_TOKEN);

        assertFalse(cosmeticSetRepository.existsById(cosmeticSetId));
        assertEquals(0L, cosmeticSetItemRepository.count());
    }

    @Test
    void deleteUserCosmetic_removesSetWhenOneItemWouldRemain() {
        transactionTemplate.executeWithoutResult(status -> cosmeticSetService.updateCosmeticSet(
                cosmeticSetId,
                itemsUpdateRequest(firstUserCosmeticIds),
                FIRST_SESSION_TOKEN
        ));

        userCosmeticService.deleteUserCosmetic(
                firstUserCosmeticIds.get(0),
                FIRST_SESSION_TOKEN
        );

        assertFalse(cosmeticSetRepository.existsById(cosmeticSetId));
        assertEquals(0L, cosmeticSetItemRepository.count());
        assertTrue(userCosmeticRepository.findById(firstUserCosmeticIds.get(0))
                .orElseThrow()
                .isDeleted());
    }

    @Test
    void deleteUserCosmetic_keepsSetWhenTwoItemsRemain() {
        List<Long> threeUserCosmeticIds = List.of(
                firstUserCosmeticIds.get(0),
                firstUserCosmeticIds.get(1),
                secondUserCosmeticIds.get(0)
        );
        transactionTemplate.executeWithoutResult(status -> cosmeticSetService.updateCosmeticSet(
                cosmeticSetId,
                itemsUpdateRequest(threeUserCosmeticIds),
                FIRST_SESSION_TOKEN
        ));

        userCosmeticService.deleteUserCosmetic(
                firstUserCosmeticIds.get(0),
                FIRST_SESSION_TOKEN
        );

        assertTrue(cosmeticSetRepository.existsById(cosmeticSetId));
        List<Long> remainingUserCosmeticIds = transactionTemplate.execute(status ->
                cosmeticSetItemRepository.findAll().stream()
                        .sorted(Comparator.comparing(CosmeticSetItem::getItemOrder))
                        .map(item -> item.getUserCosmetic().getId())
                        .toList()
        );
        assertEquals(
                List.of(firstUserCosmeticIds.get(1), secondUserCosmeticIds.get(0)),
                remainingUserCosmeticIds
        );
        assertTrue(userCosmeticRepository.findById(firstUserCosmeticIds.get(0))
                .orElseThrow()
                .isDeleted());
    }

    private CosmeticSetUpdateRequestDto itemsUpdateRequest(List<Long> userCosmeticIds) {
        return new CosmeticSetUpdateRequestDto(null, null, userCosmeticIds);
    }

    private CosmeticProduct cosmeticProduct(String productName) {
        return CosmeticProduct.builder()
                .createdByUser(user)
                .sourceType("manual")
                .productName(productName)
                .productType("toner")
                .build();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
