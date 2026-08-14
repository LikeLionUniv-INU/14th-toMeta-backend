package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequest;
import com.likelion.tometa.domain.cosmetic.dto.response.ManualCosmeticCreateResponse;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.ProductType;
import com.likelion.tometa.domain.cosmetic.enums.UsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticProductRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCosmeticService {

    private static final int MAX_MAIN_INGREDIENT_COUNT = 5;
    private static final String MANUAL_SOURCE_TYPE = "manual";

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final CosmeticProductRepository cosmeticProductRepository;
    private final CosmeticIngredientRepository cosmeticIngredientRepository;
    private final UserCosmeticRepository userCosmeticRepository;

    @Transactional
    public ManualCosmeticCreateResponse createManualCosmetic(
            ManualCosmeticCreateRequest request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        validateMainIngredientCount(request.mainIngredients());
        validateUsageTime(request.usageTime());
        validateProductType(request.productType());

        CosmeticProduct cosmeticProduct = cosmeticProductRepository.save(
                CosmeticProduct.builder()
                        .createdByUser(user)
                        .sourceType(MANUAL_SOURCE_TYPE)
                        .productName(request.productName())
                        .productType(request.productType())
                        .build()
        );

        cosmeticIngredientRepository.saveAll(
                createMainIngredients(cosmeticProduct, request.mainIngredients())
        );

        UserCosmetic userCosmetic = userCosmeticRepository.save(
                UserCosmetic.builder()
                        .user(user)
                        .cosmeticProduct(cosmeticProduct)
                        .usageTime(request.usageTime())
                        .build()
        );

        return new ManualCosmeticCreateResponse(
                userCosmetic.getId(),
                cosmeticProduct.getProductName()
        );
    }

    private void validateMainIngredientCount(List<String> mainIngredients) {
        if (mainIngredients.size() > MAX_MAIN_INGREDIENT_COUNT) {
            throw new GeneralException(CosmeticErrorCode.MAIN_INGREDIENTS_LIMIT_EXCEEDED);
        }
    }

    private void validateUsageTime(String usageTime) {
        if (!UsageTime.supports(usageTime)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private void validateProductType(String productType) {
        if (!ProductType.supports(productType)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private List<CosmeticIngredient> createMainIngredients(
            CosmeticProduct cosmeticProduct,
            List<String> ingredientNames
    ) {
        List<CosmeticIngredient> ingredients = new ArrayList<>(ingredientNames.size());

        for (int index = 0; index < ingredientNames.size(); index++) {
            ingredients.add(
                    CosmeticIngredient.builder()
                            .cosmeticProduct(cosmeticProduct)
                            .ingredientName(ingredientNames.get(index))
                            .ingredientOrder(index + 1)
                            .main(true)
                            .build()
            );
        }

        return ingredients;
    }
}
