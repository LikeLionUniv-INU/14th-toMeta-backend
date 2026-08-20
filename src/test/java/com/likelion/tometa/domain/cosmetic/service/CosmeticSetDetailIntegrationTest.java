package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetDetailResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticProductRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
@Import(CosmeticSetService.class)
class CosmeticSetDetailIntegrationTest {

    private static final String SESSION_TOKEN = "session-token";

    @Autowired
    private CosmeticSetService cosmeticSetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CosmeticProductRepository cosmeticProductRepository;

    @Autowired
    private UserCosmeticRepository userCosmeticRepository;

    @Autowired
    private CosmeticSetRepository cosmeticSetRepository;

    @Autowired
    private CosmeticSetItemRepository cosmeticSetItemRepository;

    @Autowired
    private CosmeticIngredientRepository cosmeticIngredientRepository;

    @MockitoBean
    private AnonymousSessionUserResolver sessionUserResolver;

    private User user;
    private CosmeticSet cosmeticSet;
    private UserCosmetic tonerCosmetic;
    private UserCosmetic serumCosmetic;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().build());

        CosmeticProduct toner = cosmeticProductRepository.save(
                cosmeticProduct("진정 토너", "아누아", "skin_toner")
        );
        CosmeticProduct serum = cosmeticProductRepository.save(
                cosmeticProduct("수분 세럼", "serum")
        );
        tonerCosmetic = userCosmeticRepository.save(
                userCosmetic(toner, null)
        );
        serumCosmetic = userCosmeticRepository.save(
                userCosmetic(serum, "저녁 세럼")
        );
        cosmeticSet = cosmeticSetRepository.save(
                CosmeticSet.builder()
                        .user(user)
                        .name("진정 꿀조합")
                        .usageTime(CosmeticSetUsageTime.MORNING)
                        .build()
        );

        cosmeticSetItemRepository.saveAll(List.of(
                cosmeticSetItem(serumCosmetic, 2),
                cosmeticSetItem(tonerCosmetic, 1)
        ));
        cosmeticIngredientRepository.saveAll(List.of(
                cosmeticIngredient(toner, "판테놀", 2, true),
                cosmeticIngredient(toner, "어성초", 1, true),
                cosmeticIngredient(toner, "정제수", 3, false)
        ));

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
    }

    @Test
    void getCosmeticSetDetail_ordersItemsAndOnlyMainIngredients() {
        CosmeticSetDetailResponseDto result = cosmeticSetService
                .getCosmeticSetDetail(cosmeticSet.getId(), SESSION_TOKEN);

        assertEquals(cosmeticSet.getId(), result.setId());
        assertEquals("진정 꿀조합", result.name());
        assertEquals("morning", result.usageTime());
        assertEquals(2, result.cosmetics().size());
        assertEquals(tonerCosmetic.getId(), result.cosmetics().get(0).userCosmeticId());
        assertEquals("진정 토너", tonerCosmetic.getCosmeticProduct().getProductName());
        assertEquals("아누아 진정 토너", result.cosmetics().get(0).productName());
        assertNull(result.cosmetics().get(0).customName());
        assertEquals("skin_toner", result.cosmetics().get(0).productType());
        assertEquals(
                List.of("어성초", "판테놀"),
                result.cosmetics().get(0).mainIngredients()
        );
        assertEquals(serumCosmetic.getId(), result.cosmetics().get(1).userCosmeticId());
        assertEquals("저녁 세럼", result.cosmetics().get(1).customName());
        assertEquals(List.of(), result.cosmetics().get(1).mainIngredients());
    }

    @Test
    void getCosmeticSetDetail_rejectsOtherUsersSet() {
        User otherUser = userRepository.save(User.builder().build());
        CosmeticSet otherUsersSet = cosmeticSetRepository.save(
                CosmeticSet.builder()
                        .user(otherUser)
                        .name("다른 사용자의 세트")
                        .usageTime(CosmeticSetUsageTime.NIGHT)
                        .build()
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.getCosmeticSetDetail(
                        otherUsersSet.getId(),
                        SESSION_TOKEN
                )
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getCosmeticSetDetail_excludesDeletedUserCosmetic() {
        CosmeticProduct deletedProduct = cosmeticProductRepository.save(
                cosmeticProduct("삭제된 크림", "cream")
        );
        UserCosmetic deletedUserCosmetic = userCosmeticRepository.save(
                userCosmetic(deletedProduct, null)
        );
        deletedUserCosmetic.softDelete();
        cosmeticSetItemRepository.save(
                cosmeticSetItem(deletedUserCosmetic, 3)
        );

        CosmeticSetDetailResponseDto result = cosmeticSetService
                .getCosmeticSetDetail(cosmeticSet.getId(), SESSION_TOKEN);

        assertEquals(
                List.of(tonerCosmetic.getId(), serumCosmetic.getId()),
                result.cosmetics().stream()
                        .map(CosmeticSetDetailResponseDto.Cosmetic::userCosmeticId)
                        .toList()
        );
    }

    private CosmeticProduct cosmeticProduct(String productName, String productType) {
        return cosmeticProduct(productName, null, productType);
    }

    private CosmeticProduct cosmeticProduct(
            String productName,
            String brandName,
            String productType
    ) {
        return CosmeticProduct.builder()
                .createdByUser(user)
                .sourceType("manual")
                .productName(productName)
                .brandName(brandName)
                .productType(productType)
                .build();
    }

    private UserCosmetic userCosmetic(
            CosmeticProduct cosmeticProduct,
            String customName
    ) {
        return UserCosmetic.builder()
                .user(user)
                .cosmeticProduct(cosmeticProduct)
                .customName(customName)
                .build();
    }

    private CosmeticSetItem cosmeticSetItem(UserCosmetic userCosmetic, int itemOrder) {
        return CosmeticSetItem.builder()
                .cosmeticSet(cosmeticSet)
                .userCosmetic(userCosmetic)
                .itemOrder(itemOrder)
                .build();
    }

    private CosmeticIngredient cosmeticIngredient(
            CosmeticProduct cosmeticProduct,
            String ingredientName,
            int ingredientOrder,
            boolean main
    ) {
        return CosmeticIngredient.builder()
                .cosmeticProduct(cosmeticProduct)
                .ingredientName(ingredientName)
                .ingredientOrder(ingredientOrder)
                .main(main)
                .build();
    }
}
