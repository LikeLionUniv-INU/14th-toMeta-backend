package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetUpdateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetDetailResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmeticSetServiceTest {

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
    private CosmeticIngredientRepository cosmeticIngredientRepository;

    @InjectMocks
    private CosmeticSetService cosmeticSetService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
    }

    @Test
    void createCosmeticSet_savesSetAndItemsInRequestOrder() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "  진정템  ",
                "morning",
                List.of(11L, 12L, 15L)
        );
        UserCosmetic cosmetic11 = userCosmetic(11L);
        UserCosmetic cosmetic12 = userCosmetic(12L);
        UserCosmetic cosmetic15 = userCosmetic(15L);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findAllByIdInAndUserAndDeletedAtIsNull(
                request.userCosmeticIds(),
                user
        )).thenReturn(List.of(cosmetic15, cosmetic11, cosmetic12));
        when(cosmeticSetRepository.save(any(CosmeticSet.class)))
                .thenAnswer(invocation -> {
                    CosmeticSet cosmeticSet = invocation.getArgument(0);
                    ReflectionTestUtils.setField(cosmeticSet, "id", 7L);
                    return cosmeticSet;
                });

        CosmeticSetCreateResponseDto result = cosmeticSetService
                .createCosmeticSet(request, SESSION_TOKEN);

        assertEquals(7L, result.setId());

        ArgumentCaptor<CosmeticSet> setCaptor = ArgumentCaptor.forClass(CosmeticSet.class);
        verify(cosmeticSetRepository).save(setCaptor.capture());

        CosmeticSet savedSet = setCaptor.getValue();
        assertSame(user, savedSet.getUser());
        assertEquals("진정템", savedSet.getName());
        assertSame(CosmeticSetUsageTime.MORNING, savedSet.getUsageTime());

        verify(cosmeticSetItemRepository).saveAll(argThat(savedItems -> {
            List<CosmeticSetItem> items = StreamSupport
                    .stream(savedItems.spliterator(), false)
                    .toList();

            assertEquals(3, items.size());
            assertSame(savedSet, items.get(0).getCosmeticSet());
            assertSame(cosmetic11, items.get(0).getUserCosmetic());
            assertEquals(1, items.get(0).getItemOrder());
            assertSame(cosmetic12, items.get(1).getUserCosmetic());
            assertEquals(2, items.get(1).getItemOrder());
            assertSame(cosmetic15, items.get(2).getUserCosmetic());
            assertEquals(3, items.get(2).getItemOrder());
            return true;
        }));
    }

    @Test
    void createCosmeticSet_rejectsEmptyCosmeticIds() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                List.of()
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsNullCosmeticIds() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                null
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsFewerThanTwoCosmetics() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                List.of(11L)
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(
                CosmeticErrorCode.COSMETIC_SET_MIN_ITEMS_REQUIRED,
                exception.getErrorCode()
        );
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsDuplicateCosmeticIds() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                List.of(11L, 11L, 12L)
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_DUPLICATE_ITEM, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsUnsupportedUsageTime() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "MORNING",
                List.of(11L, 12L)
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsMissingOrUnownedCosmetic() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "night",
                List.of(11L, 12L)
        );
        UserCosmetic cosmetic11 = mock(UserCosmetic.class);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findAllByIdInAndUserAndDeletedAtIsNull(
                request.userCosmeticIds(),
                user
        )).thenReturn(List.of(cosmetic11));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cosmeticSetRepository, cosmeticSetItemRepository);
    }

    @Test
    void getCosmeticSetDetail_returnsItemsAndMainIngredientsInOrder() {
        CosmeticSet cosmeticSet = cosmeticSet("진정 꿀조합", CosmeticSetUsageTime.MORNING);
        ReflectionTestUtils.setField(cosmeticSet, "id", 7L);
        CosmeticProduct toner = cosmeticProduct(
                101L,
                "어성초 77% 진정 토너",
                "아누아",
                "skin_toner"
        );
        CosmeticProduct serum = cosmeticProduct(
                102L,
                "토리든 다이브인 저분자 히알루론산 세럼",
                "serum"
        );
        UserCosmetic tonerCosmetic = userCosmetic(12L, toner, null);
        UserCosmetic serumCosmetic = userCosmetic(15L, serum, "수분 세럼");
        List<CosmeticSetItem> items = List.of(
                cosmeticSetItem(cosmeticSet, tonerCosmetic, 1),
                cosmeticSetItem(cosmeticSet, serumCosmetic, 2)
        );
        List<CosmeticIngredient> ingredients = List.of(
                cosmeticIngredient(toner, "어성초", 1),
                cosmeticIngredient(toner, "판테놀", 2)
        );

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUserForRead(7L, user))
                .thenReturn(Optional.of(cosmeticSet));
        when(cosmeticSetItemRepository
                .findAllActiveByCosmeticSetOrderByItemOrder(cosmeticSet))
                .thenReturn(items);
        when(cosmeticIngredientRepository.findAllMainByCosmeticProductIds(
                List.of(101L, 102L)
        )).thenReturn(ingredients);

        CosmeticSetDetailResponseDto result = cosmeticSetService
                .getCosmeticSetDetail(7L, SESSION_TOKEN);

        assertEquals(7L, result.setId());
        assertEquals("진정 꿀조합", result.name());
        assertEquals("morning", result.usageTime());
        assertEquals(2, result.cosmetics().size());
        assertEquals(12L, result.cosmetics().get(0).userCosmeticId());
        assertEquals("어성초 77% 진정 토너", toner.getProductName());
        assertEquals("아누아 어성초 77% 진정 토너",
                result.cosmetics().get(0).productName());
        assertEquals(null, result.cosmetics().get(0).customName());
        assertEquals("skin_toner", result.cosmetics().get(0).productType());
        assertEquals(List.of("어성초", "판테놀"),
                result.cosmetics().get(0).mainIngredients());
        assertEquals(15L, result.cosmetics().get(1).userCosmeticId());
        assertEquals("수분 세럼", result.cosmetics().get(1).customName());
        assertEquals(List.of(), result.cosmetics().get(1).mainIngredients());
    }

    @Test
    void getCosmeticSetDetail_rejectsMissingOrUnownedSet() {
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUserForRead(99L, user))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.getCosmeticSetDetail(99L, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cosmeticSetItemRepository, cosmeticIngredientRepository);
    }

    @Test
    void deleteCosmeticSet_deletesItemsBeforeOwnedSet() {
        CosmeticSet cosmeticSet = mock(CosmeticSet.class);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        cosmeticSetService.deleteCosmeticSet(7L, SESSION_TOKEN);

        InOrder deletionOrder = inOrder(
                cosmeticSetItemRepository,
                cosmeticSetRepository
        );
        deletionOrder.verify(cosmeticSetItemRepository).deleteAllByCosmeticSetId(7L);
        deletionOrder.verify(cosmeticSetRepository).delete(cosmeticSet);
    }

    @Test
    void deleteCosmeticSet_rejectsMissingOrUnownedSet() {
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.deleteCosmeticSet(7L, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cosmeticSetItemRepository);
        verify(cosmeticSetRepository, never()).delete(any(CosmeticSet.class));
    }

    @Test
    void updateCosmeticSet_updatesAllFieldsAndReplacesItemsInRequestOrder() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                "  진정 꿀조합  ",
                "both",
                List.of(11L, 12L, 15L)
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.MORNING);
        UserCosmetic cosmetic11 = userCosmetic(11L);
        UserCosmetic cosmetic12 = userCosmetic(12L);
        UserCosmetic cosmetic15 = userCosmetic(15L);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));
        when(userCosmeticRepository.findAllByIdInAndUserAndDeletedAtIsNull(
                request.userCosmeticIds(),
                user
        )).thenReturn(List.of(cosmetic15, cosmetic11, cosmetic12));

        cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN);

        assertEquals("진정 꿀조합", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.BOTH, cosmeticSet.getUsageTime());
        InOrder itemReplacementOrder = inOrder(cosmeticSetItemRepository);
        itemReplacementOrder.verify(cosmeticSetItemRepository)
                .deleteAllByCosmeticSet(cosmeticSet);
        itemReplacementOrder.verify(cosmeticSetItemRepository).saveAll(argThat(savedItems -> {
            List<CosmeticSetItem> items = StreamSupport
                    .stream(savedItems.spliterator(), false)
                    .toList();

            assertEquals(3, items.size());
            assertSame(cosmetic11, items.get(0).getUserCosmetic());
            assertEquals(1, items.get(0).getItemOrder());
            assertSame(cosmetic12, items.get(1).getUserCosmetic());
            assertEquals(2, items.get(1).getItemOrder());
            assertSame(cosmetic15, items.get(2).getUserCosmetic());
            assertEquals(3, items.get(2).getItemOrder());
            return true;
        }));
    }

    @Test
    void updateCosmeticSet_updatesOnlyNameWithoutReplacingItems() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                "새 세트 이름",
                null,
                null
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.NIGHT);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN);

        assertEquals("새 세트 이름", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.NIGHT, cosmeticSet.getUsageTime());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsMissingOrUnownedSet() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                "새 세트 이름",
                null,
                null
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(99L, user))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(99L, request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsEmptyRequest() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                null,
                null,
                null
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.MORNING);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("기존 세트", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.MORNING, cosmeticSet.getUsageTime());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_validatesAllFieldsBeforeChangingSet() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                "새 세트 이름",
                "MORNING",
                null
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.NIGHT);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("기존 세트", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.NIGHT, cosmeticSet.getUsageTime());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsEmptyCosmeticIds() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                null,
                null,
                List.of()
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.BOTH);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsFewerThanTwoCosmetics() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                null,
                null,
                List.of(11L)
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.BOTH);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(
                CosmeticErrorCode.COSMETIC_SET_MIN_ITEMS_REQUIRED,
                exception.getErrorCode()
        );
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_updatesOnlyUsageTimeWithoutReplacingItems() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                null,
                "night",
                null
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.MORNING);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN);

        assertEquals("기존 세트", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.NIGHT, cosmeticSet.getUsageTime());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsDuplicateCosmeticIds() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                null,
                null,
                List.of(11L, 11L, 12L)
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.MORNING);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_DUPLICATE_ITEM, exception.getErrorCode());
        assertEquals("기존 세트", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.MORNING, cosmeticSet.getUsageTime());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsNonPositiveCosmeticId() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                null,
                null,
                List.of(11L, -1L)
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.MORNING);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("기존 세트", cosmeticSet.getName());
        assertSame(CosmeticSetUsageTime.MORNING, cosmeticSet.getUsageTime());
        verifyNoInteractions(userCosmeticRepository, cosmeticSetItemRepository);
    }

    @Test
    void updateCosmeticSet_rejectsMissingOrUnownedCosmeticBeforeChangingSet() {
        CosmeticSetUpdateRequestDto request = new CosmeticSetUpdateRequestDto(
                "새 세트 이름",
                null,
                List.of(11L, 12L)
        );
        CosmeticSet cosmeticSet = cosmeticSet("기존 세트", CosmeticSetUsageTime.MORNING);
        UserCosmetic cosmetic11 = mock(UserCosmetic.class);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));
        when(userCosmeticRepository.findAllByIdInAndUserAndDeletedAtIsNull(
                request.userCosmeticIds(),
                user
        )).thenReturn(List.of(cosmetic11));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.updateCosmeticSet(7L, request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND, exception.getErrorCode());
        assertEquals("기존 세트", cosmeticSet.getName());
        verify(cosmeticSetItemRepository, never()).deleteAllByCosmeticSet(any());
        verify(cosmeticSetItemRepository, never()).saveAll(any());
    }

    private UserCosmetic userCosmetic(Long id) {
        UserCosmetic userCosmetic = mock(UserCosmetic.class);
        when(userCosmetic.getId()).thenReturn(id);
        return userCosmetic;
    }

    private UserCosmetic userCosmetic(
            Long id,
            CosmeticProduct cosmeticProduct,
            String customName
    ) {
        UserCosmetic userCosmetic = UserCosmetic.builder()
                .user(user)
                .cosmeticProduct(cosmeticProduct)
                .customName(customName)
                .build();
        ReflectionTestUtils.setField(userCosmetic, "id", id);
        return userCosmetic;
    }

    private CosmeticProduct cosmeticProduct(
            Long id,
            String productName,
            String productType
    ) {
        return cosmeticProduct(id, productName, null, productType);
    }

    private CosmeticProduct cosmeticProduct(
            Long id,
            String productName,
            String brandName,
            String productType
    ) {
        CosmeticProduct cosmeticProduct = CosmeticProduct.builder()
                .createdByUser(user)
                .sourceType("manual")
                .productName(productName)
                .brandName(brandName)
                .productType(productType)
                .build();
        ReflectionTestUtils.setField(cosmeticProduct, "id", id);
        return cosmeticProduct;
    }

    private CosmeticSetItem cosmeticSetItem(
            CosmeticSet cosmeticSet,
            UserCosmetic userCosmetic,
            int itemOrder
    ) {
        return CosmeticSetItem.builder()
                .cosmeticSet(cosmeticSet)
                .userCosmetic(userCosmetic)
                .itemOrder(itemOrder)
                .build();
    }

    private CosmeticIngredient cosmeticIngredient(
            CosmeticProduct cosmeticProduct,
            String ingredientName,
            int ingredientOrder
    ) {
        return CosmeticIngredient.builder()
                .cosmeticProduct(cosmeticProduct)
                .ingredientName(ingredientName)
                .ingredientOrder(ingredientOrder)
                .main(true)
                .build();
    }

    private CosmeticSet cosmeticSet(
            String name,
            CosmeticSetUsageTime usageTime
    ) {
        return CosmeticSet.builder()
                .user(user)
                .name(name)
                .usageTime(usageTime)
                .build();
    }
}
