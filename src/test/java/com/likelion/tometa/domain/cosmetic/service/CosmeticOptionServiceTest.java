package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticOptionResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticTagRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmeticOptionServiceTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;

    @Mock
    private UserCosmeticRepository userCosmeticRepository;

    @Mock
    private CosmeticSetRepository cosmeticSetRepository;

    @Mock
    private CosmeticSetItemRepository cosmeticSetItemRepository;

    @Mock
    private CosmeticTagRepository cosmeticTagRepository;

    @InjectMocks
    private CosmeticOptionService cosmeticOptionService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
    }

    @Test
    void getCosmeticOptions_mapsManualAndSearchCosmeticsAndSet() {
        CosmeticProduct manual = product(101L, "manual", "진정 크림", null, "cream");
        CosmeticProduct search = product(102L, "search", "다이브인 세럼", "토리든", "serum");
        UserCosmetic manualUserCosmetic = userCosmetic(11L, manual);
        UserCosmetic searchUserCosmetic = userCosmetic(12L, search);
        CosmeticSet cosmeticSet = cosmeticSet(21L, "수분 세트", CosmeticSetUsageTime.BOTH);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, manualUserCosmetic, 1),
                setItem(32L, cosmeticSet, searchUserCosmetic, 2)
        );
        List<CosmeticTag> tags = List.of(
                tag(manual, CosmeticTagType.INGREDIENT, "시카", 1),
                tag(manual, CosmeticTagType.INGREDIENT, "수분", 2),
                tag(manual, CosmeticTagType.INGREDIENT, "진정", 3),
                tag(search, CosmeticTagType.BENEFIT, "보습", 1),
                tag(search, CosmeticTagType.INGREDIENT, "수분", 1),
                tag(search, CosmeticTagType.INGREDIENT, "히알루론산", 2),
                tag(search, CosmeticTagType.INGREDIENT, "판테놀", 3)
        );

        when(userCosmeticRepository.findAllActiveByUserOrderByNewest(user))
                .thenReturn(List.of(searchUserCosmetic, manualUserCosmetic));
        when(cosmeticSetRepository.findAllByUserOrderByCreatedAtDescIdDesc(user))
                .thenReturn(List.of(cosmeticSet));
        when(cosmeticSetItemRepository.findAllActiveByCosmeticSetsOrderByItemOrder(
                List.of(cosmeticSet)
        )).thenReturn(items);
        when(cosmeticTagRepository.findAllByCosmeticProductIds(anyCollection()))
                .thenReturn(tags);

        CosmeticOptionResponseDto result = cosmeticOptionService
                .getCosmeticOptions(SESSION_TOKEN);

        assertEquals(1, result.sets().size());
        assertEquals(21L, result.sets().getFirst().setId());
        assertEquals("수분 세트", result.sets().getFirst().name());
        assertEquals("both", result.sets().getFirst().usageTime());
        assertEquals("수분", result.sets().getFirst().tags().getFirst());
        assertEquals(3, result.sets().getFirst().tags().size());

        assertEquals(2, result.cosmetics().size());
        CosmeticOptionResponseDto.CosmeticOption searchOption = result.cosmetics().get(0);
        assertEquals(12L, searchOption.userCosmeticId());
        assertEquals("토리든 다이브인 세럼", searchOption.productName());
        assertEquals("토리든", searchOption.brandName());
        assertEquals(List.of("세럼", "보습", "수분", "히알루론산"), searchOption.tags());

        CosmeticOptionResponseDto.CosmeticOption manualOption = result.cosmetics().get(1);
        assertEquals(11L, manualOption.userCosmeticId());
        assertEquals("진정 크림", manualOption.productName());
        assertNull(manualOption.brandName());
        assertEquals(List.of("cream", "시카", "수분", "진정"), manualOption.tags());
        verify(sessionUserResolver).resolve(SESSION_TOKEN);
    }

    @Test
    void getCosmeticOptions_ordersDuplicateSetTagsByCountThenMinimumOrder() {
        CosmeticProduct first = product(101L, "search", "first", "brand", "toner");
        CosmeticProduct second = product(102L, "search", "second", "brand", "serum");
        CosmeticProduct third = product(103L, "search", "third", "brand", "cream");
        CosmeticSet cosmeticSet = cosmeticSet(21L, "priority", CosmeticSetUsageTime.MORNING);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, first), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, second), 2),
                setItem(33L, cosmeticSet, userCosmetic(13L, third), 3)
        );
        List<CosmeticTag> tags = List.of(
                tag(first, CosmeticTagType.INGREDIENT, "freq", 3),
                tag(first, CosmeticTagType.INGREDIENT, "alpha", 1),
                tag(first, CosmeticTagType.INGREDIENT, "beta", 2),
                tag(second, CosmeticTagType.INGREDIENT, "freq", 2),
                tag(second, CosmeticTagType.INGREDIENT, "alpha", 4),
                tag(third, CosmeticTagType.INGREDIENT, "freq", 5),
                tag(third, CosmeticTagType.INGREDIENT, "beta", 1)
        );

        when(userCosmeticRepository.findAllActiveByUserOrderByNewest(user))
                .thenReturn(List.of());
        when(cosmeticSetRepository.findAllByUserOrderByCreatedAtDescIdDesc(user))
                .thenReturn(List.of(cosmeticSet));
        when(cosmeticSetItemRepository.findAllActiveByCosmeticSetsOrderByItemOrder(
                List.of(cosmeticSet)
        )).thenReturn(items);
        when(cosmeticTagRepository.findAllByCosmeticProductIds(anyCollection()))
                .thenReturn(tags);

        CosmeticOptionResponseDto result = cosmeticOptionService
                .getCosmeticOptions(SESSION_TOKEN);

        assertEquals(List.of("freq", "alpha", "beta"), result.sets().getFirst().tags());
    }

    @Test
    void getCosmeticOptions_fillsFromTwoCosmeticsDeterministicallyWithoutDuplicates() {
        CosmeticProduct first = product(101L, "manual", "first", null, "toner");
        CosmeticProduct second = product(102L, "manual", "second", null, "serum");
        CosmeticSet cosmeticSet = cosmeticSet(21L, "fallback", CosmeticSetUsageTime.NIGHT);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, first), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, second), 2)
        );
        List<CosmeticTag> tags = List.of(
                tag(first, CosmeticTagType.INGREDIENT, "A1", 1),
                tag(first, CosmeticTagType.INGREDIENT, "A2", 2),
                tag(second, CosmeticTagType.INGREDIENT, "B1", 1),
                tag(second, CosmeticTagType.INGREDIENT, "B2", 2)
        );

        when(userCosmeticRepository.findAllActiveByUserOrderByNewest(user))
                .thenReturn(List.of());
        when(cosmeticSetRepository.findAllByUserOrderByCreatedAtDescIdDesc(user))
                .thenReturn(List.of(cosmeticSet));
        when(cosmeticSetItemRepository.findAllActiveByCosmeticSetsOrderByItemOrder(
                List.of(cosmeticSet)
        )).thenReturn(items);
        when(cosmeticTagRepository.findAllByCosmeticProductIds(anyCollection()))
                .thenReturn(tags);

        List<String> firstResult = cosmeticOptionService
                .getCosmeticOptions(SESSION_TOKEN).sets().getFirst().tags();
        List<String> secondResult = cosmeticOptionService
                .getCosmeticOptions(SESSION_TOKEN).sets().getFirst().tags();

        assertEquals(firstResult, secondResult);
        assertEquals(3, firstResult.size());
        assertEquals(3, firstResult.stream().distinct().count());
        assertTrue(firstResult.stream().anyMatch(name -> name.startsWith("A")));
        assertTrue(firstResult.stream().anyMatch(name -> name.startsWith("B")));
    }

    @Test
    void getCosmeticOptions_doesNotAggregateSameNameAcrossDifferentTagTypes() {
        CosmeticProduct first = product(101L, "search", "first", "brand", "toner");
        CosmeticProduct second = product(102L, "search", "second", "brand", "serum");
        CosmeticSet cosmeticSet = cosmeticSet(21L, "tag type", CosmeticSetUsageTime.BOTH);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, first), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, second), 2)
        );

        when(userCosmeticRepository.findAllActiveByUserOrderByNewest(user))
                .thenReturn(List.of());
        when(cosmeticSetRepository.findAllByUserOrderByCreatedAtDescIdDesc(user))
                .thenReturn(List.of(cosmeticSet));
        when(cosmeticSetItemRepository.findAllActiveByCosmeticSetsOrderByItemOrder(
                List.of(cosmeticSet)
        )).thenReturn(items);
        when(cosmeticTagRepository.findAllByCosmeticProductIds(anyCollection()))
                .thenReturn(List.of(
                        tag(first, CosmeticTagType.BENEFIT, "same", 1),
                        tag(second, CosmeticTagType.INGREDIENT, "same", 1)
                ));

        List<String> tags = cosmeticOptionService
                .getCosmeticOptions(SESSION_TOKEN).sets().getFirst().tags();

        assertEquals(List.of("same", "same"), tags);
    }

    @Test
    void getCosmeticOptions_returnsEmptyArraysWhenNothingIsRegistered() {
        when(userCosmeticRepository.findAllActiveByUserOrderByNewest(user))
                .thenReturn(List.of());
        when(cosmeticSetRepository.findAllByUserOrderByCreatedAtDescIdDesc(user))
                .thenReturn(List.of());

        CosmeticOptionResponseDto result = cosmeticOptionService
                .getCosmeticOptions(SESSION_TOKEN);

        assertTrue(result.sets().isEmpty());
        assertTrue(result.cosmetics().isEmpty());
        verify(cosmeticSetItemRepository, never())
                .findAllActiveByCosmeticSetsOrderByItemOrder(anyCollection());
        verifyNoInteractions(cosmeticTagRepository);
    }

    private CosmeticProduct product(
            Long id,
            String sourceType,
            String productName,
            String brandName,
            String productType
    ) {
        CosmeticProduct product = CosmeticProduct.builder()
                .sourceType(sourceType)
                .productName(productName)
                .brandName(brandName)
                .productType(productType)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private UserCosmetic userCosmetic(Long id, CosmeticProduct product) {
        UserCosmetic userCosmetic = UserCosmetic.builder()
                .user(user)
                .cosmeticProduct(product)
                .build();
        ReflectionTestUtils.setField(userCosmetic, "id", id);
        return userCosmetic;
    }

    private CosmeticSet cosmeticSet(
            Long id,
            String name,
            CosmeticSetUsageTime usageTime
    ) {
        CosmeticSet cosmeticSet = CosmeticSet.builder()
                .user(user)
                .name(name)
                .usageTime(usageTime)
                .build();
        ReflectionTestUtils.setField(cosmeticSet, "id", id);
        return cosmeticSet;
    }

    private CosmeticSetItem setItem(
            Long id,
            CosmeticSet cosmeticSet,
            UserCosmetic userCosmetic,
            int order
    ) {
        CosmeticSetItem item = CosmeticSetItem.builder()
                .cosmeticSet(cosmeticSet)
                .userCosmetic(userCosmetic)
                .itemOrder(order)
                .build();
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private CosmeticTag tag(
            CosmeticProduct product,
            CosmeticTagType type,
            String name,
            int order
    ) {
        return CosmeticTag.builder()
                .cosmeticProduct(product)
                .tagType(type)
                .name(name)
                .tagOrder(order)
                .build();
    }
}
