package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticProductRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private UserCosmeticRepository userCosmeticRepository;

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

        ArgumentCaptor<UserCosmetic> userCosmeticCaptor =
                ArgumentCaptor.forClass(UserCosmetic.class);
        verify(userCosmeticRepository).save(userCosmeticCaptor.capture());

        UserCosmetic capturedUserCosmetic = userCosmeticCaptor.getValue();
        assertSame(user, capturedUserCosmetic.getUser());
        assertSame(savedProduct, capturedUserCosmetic.getCosmeticProduct());
        assertNull(capturedUserCosmetic.getCustomName());
    }

    @Test
    void createManualCosmetic_rejectsMoreThanFiveMainIngredients() {
        ManualCosmeticCreateRequestDto request = new ManualCosmeticCreateRequestDto(
                "제품명",
                "serum",
                List.of("1", "2", "3", "4", "5", "6")
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
                userCosmeticRepository
        );
    }
}
