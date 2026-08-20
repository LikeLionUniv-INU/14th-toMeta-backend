package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.SearchedCosmeticCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticProductRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticTagRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCosmeticServiceTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;

    @Mock
    private CosmeticProductRepository cosmeticProductRepository;

    @Mock
    private CosmeticIngredientRepository cosmeticIngredientRepository;

    @Mock
    private CosmeticTagRepository cosmeticTagRepository;

    @Mock
    private UserCosmeticRepository userCosmeticRepository;

    @Mock
    private CosmeticSetRepository cosmeticSetRepository;

    @Mock
    private CosmeticSetItemRepository cosmeticSetItemRepository;

    @InjectMocks
    private UserCosmeticService userCosmeticService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
    }

    @Test
    void createManualCosmetic_savesProductIngredientsAndUserCosmetic() {
        ManualCosmeticCreateRequestDto request = new ManualCosmeticCreateRequestDto(
                "내가 쓰는 진정 세럼",
                "serum",
                List.of("히알루론산", "나이아신아마이드", "판테놀")
        );

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticProductRepository.save(any(CosmeticProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userCosmeticService.createManualCosmetic(request, SESSION_TOKEN);

        ArgumentCaptor<CosmeticProduct> productCaptor =
                ArgumentCaptor.forClass(CosmeticProduct.class);
        verify(cosmeticProductRepository).save(productCaptor.capture());

        CosmeticProduct savedProduct = productCaptor.getValue();
        assertSame(user, savedProduct.getCreatedByUser());
        assertEquals("manual", savedProduct.getSourceType());
        assertEquals("내가 쓰는 진정 세럼", savedProduct.getProductName());
        assertEquals("serum", savedProduct.getProductType());

        verify(cosmeticIngredientRepository).saveAll(argThat(savedIngredients -> {
            List<CosmeticIngredient> ingredients = StreamSupport
                    .stream(savedIngredients.spliterator(), false)
                    .toList();

            assertEquals(3, ingredients.size());
            assertSame(savedProduct, ingredients.get(0).getCosmeticProduct());
            assertNull(ingredients.get(0).getIngredient());
            assertEquals("히알루론산", ingredients.get(0).getIngredientName());
            assertEquals(1, ingredients.get(0).getIngredientOrder());
            assertEquals(true, ingredients.get(0).isMain());
            assertEquals("나이아신아마이드", ingredients.get(1).getIngredientName());
            assertEquals(2, ingredients.get(1).getIngredientOrder());
            assertEquals("판테놀", ingredients.get(2).getIngredientName());
            assertEquals(3, ingredients.get(2).getIngredientOrder());
            return true;
        }));

        verify(cosmeticTagRepository).saveAll(argThat(savedTags -> {
            List<CosmeticTag> tags = StreamSupport
                    .stream(savedTags.spliterator(), false)
                    .toList();

            assertEquals(3, tags.size());
            assertSame(savedProduct, tags.get(0).getCosmeticProduct());
            assertSame(CosmeticTagType.INGREDIENT, tags.get(0).getTagType());
            assertEquals("히알루론산", tags.get(0).getName());
            assertEquals(1, tags.get(0).getTagOrder());
            assertEquals("나이아신아마이드", tags.get(1).getName());
            assertEquals(2, tags.get(1).getTagOrder());
            assertEquals("판테놀", tags.get(2).getName());
            assertEquals(3, tags.get(2).getTagOrder());
            return true;
        }));

        ArgumentCaptor<UserCosmetic> userCosmeticCaptor =
                ArgumentCaptor.forClass(UserCosmetic.class);
        verify(userCosmeticRepository).save(userCosmeticCaptor.capture());

        UserCosmetic capturedUserCosmetic = userCosmeticCaptor.getValue();
        assertSame(user, capturedUserCosmetic.getUser());
        assertSame(savedProduct, capturedUserCosmetic.getCosmeticProduct());
        assertNull(capturedUserCosmetic.getCustomName());
    }

    @Test
    void createSearchedCosmetic_storesBrandSeparatelyAndReturnsDisplayName() {
        CosmeticSearchCandidate candidate = searchedCandidate("다이브인 세럼", "토리든");
        when(cosmeticProductRepository.save(any(CosmeticProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userCosmeticRepository.save(any(UserCosmetic.class)))
                .thenAnswer(invocation -> {
                    UserCosmetic saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 11L);
                    return saved;
                });

        SearchedCosmeticCreateResponseDto result = userCosmeticService
                .createSearchedCosmetic(user, candidate);

        ArgumentCaptor<CosmeticProduct> productCaptor =
                ArgumentCaptor.forClass(CosmeticProduct.class);
        verify(cosmeticProductRepository).save(productCaptor.capture());
        CosmeticProduct savedProduct = productCaptor.getValue();
        assertEquals("다이브인 세럼", savedProduct.getProductName());
        assertEquals("토리든", savedProduct.getBrandName());
        assertEquals("토리든 다이브인 세럼", result.productName());
    }

    @Test
    void createSearchedCosmetic_returnsOriginalProductNameForMissingBrand() {
        when(cosmeticProductRepository.save(any(CosmeticProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userCosmeticRepository.save(any(UserCosmetic.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        for (String brandName : new String[]{null, "", "   "}) {
            CosmeticSearchCandidate candidate = searchedCandidate("진정 크림", brandName);
            SearchedCosmeticCreateResponseDto result = userCosmeticService
                    .createSearchedCosmetic(user, candidate);

            assertEquals("진정 크림", result.productName());
        }
    }

    @Test
    void createManualCosmetic_rejectsMoreThanThreeMainIngredients() {
        ManualCosmeticCreateRequestDto request = new ManualCosmeticCreateRequestDto(
                "제품명",
                "serum",
                List.of("1", "2", "3", "4")
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCosmeticService.createManualCosmetic(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.MAIN_INGREDIENTS_LIMIT_EXCEEDED, exception.getErrorCode());
        verifyNoInteractions(
                cosmeticProductRepository,
                cosmeticIngredientRepository,
                cosmeticTagRepository,
                userCosmeticRepository
        );
    }

    @Test
    void createManualCosmetic_rejectsUnsupportedProductType() {
        ManualCosmeticCreateRequestDto request = new ManualCosmeticCreateRequestDto(
                "제품명",
                "cleanser",
                List.of()
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCosmeticService.createManualCosmetic(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(
                cosmeticProductRepository,
                cosmeticIngredientRepository,
                cosmeticTagRepository,
                userCosmeticRepository
        );
    }

    @Test
    void deleteUserCosmetic_softDeletesOwnedActiveCosmeticNotIncludedInSet() {
        UserCosmetic userCosmetic = userCosmetic("진정 세럼");

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findByIdAndUserAndDeletedAtIsNull(1L, user))
                .thenReturn(Optional.of(userCosmetic));
        when(cosmeticSetRepository.findAllContainingUserCosmeticForUpdate(
                userCosmetic,
                user
        )).thenReturn(List.of());

        userCosmeticService.deleteUserCosmetic(1L, SESSION_TOKEN);

        assertTrue(userCosmetic.isDeleted());
        assertNotNull(userCosmetic.getDeletedAt());
        verifyNoInteractions(cosmeticSetItemRepository);
    }

    @Test
    void deleteUserCosmetic_deletesSetWhenOneItemWouldRemain() {
        UserCosmetic deletedCosmetic = userCosmetic("삭제할 토너");
        CosmeticSet cosmeticSet = cosmeticSet("두 단계 루틴");
        CosmeticSetItemRepository.CosmeticSetItemCount itemCount =
                cosmeticSetItemCount(cosmeticSet, 2L);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findByIdAndUserAndDeletedAtIsNull(1L, user))
                .thenReturn(Optional.of(deletedCosmetic));
        when(cosmeticSetRepository.findAllContainingUserCosmeticForUpdate(
                deletedCosmetic,
                user
        )).thenReturn(List.of(cosmeticSet));
        when(cosmeticSetItemRepository.countItemsByCosmeticSetIn(List.of(cosmeticSet)))
                .thenReturn(List.of(itemCount));

        userCosmeticService.deleteUserCosmetic(1L, SESSION_TOKEN);

        verify(cosmeticSetItemRepository)
                .deleteAllByUserCosmeticAndCosmeticSetIn(
                        deletedCosmetic,
                        List.of(cosmeticSet)
                );
        verify(cosmeticSetItemRepository).deleteAllByCosmeticSetIn(List.of(cosmeticSet));
        verify(cosmeticSetRepository).deleteAll(List.of(cosmeticSet));
        assertTrue(deletedCosmetic.isDeleted());
    }

    @Test
    void deleteUserCosmetic_keepsSetWhenTwoItemsWouldRemain() {
        UserCosmetic deletedCosmetic = userCosmetic("삭제할 토너");
        CosmeticSet cosmeticSet = cosmeticSet("세 단계 루틴");
        CosmeticSetItemRepository.CosmeticSetItemCount itemCount =
                cosmeticSetItemCount(cosmeticSet, 3L);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findByIdAndUserAndDeletedAtIsNull(1L, user))
                .thenReturn(Optional.of(deletedCosmetic));
        when(cosmeticSetRepository.findAllContainingUserCosmeticForUpdate(
                deletedCosmetic,
                user
        )).thenReturn(List.of(cosmeticSet));
        when(cosmeticSetItemRepository.countItemsByCosmeticSetIn(List.of(cosmeticSet)))
                .thenReturn(List.of(itemCount));

        userCosmeticService.deleteUserCosmetic(1L, SESSION_TOKEN);

        verify(cosmeticSetItemRepository)
                .deleteAllByUserCosmeticAndCosmeticSetIn(
                        deletedCosmetic,
                        List.of(cosmeticSet)
                );
        verify(cosmeticSetItemRepository, never()).deleteAllByCosmeticSetIn(any());
        verify(cosmeticSetRepository, never()).deleteAll(any());
        assertTrue(deletedCosmetic.isDeleted());
    }

    @Test
    void deleteUserCosmetic_rejectsMissingOwnedActiveCosmetic() {
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findByIdAndUserAndDeletedAtIsNull(1L, user))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> userCosmeticService.deleteUserCosmetic(1L, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cosmeticSetRepository, cosmeticSetItemRepository);
    }

    private UserCosmetic userCosmetic(String productName) {
        CosmeticProduct cosmeticProduct = CosmeticProduct.builder()
                .createdByUser(user)
                .sourceType("manual")
                .productName(productName)
                .productType("serum")
                .build();

        return UserCosmetic.builder()
                .user(user)
                .cosmeticProduct(cosmeticProduct)
                .build();
    }

    private CosmeticSearchCandidate searchedCandidate(
            String productName,
            String brandName
    ) {
        return new CosmeticSearchCandidate(
                productName,
                brandName,
                "serum",
                "https://example.com/image.jpg",
                "진정",
                List.of("판테놀")
        );
    }

    private CosmeticSet cosmeticSet(String name) {
        return CosmeticSet.builder()
                .user(user)
                .name(name)
                .usageTime(CosmeticSetUsageTime.MORNING)
                .build();
    }

    private CosmeticSetItemRepository.CosmeticSetItemCount cosmeticSetItemCount(
            CosmeticSet cosmeticSet,
            long itemCount
    ) {
        CosmeticSetItemRepository.CosmeticSetItemCount count = mock(
                CosmeticSetItemRepository.CosmeticSetItemCount.class
        );
        when(count.getCosmeticSet()).thenReturn(cosmeticSet);
        when(count.getItemCount()).thenReturn(itemCount);
        return count;
    }
}
