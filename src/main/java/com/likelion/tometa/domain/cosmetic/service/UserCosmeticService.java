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
import com.likelion.tometa.domain.cosmetic.enums.ProductType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.likelion.tometa.domain.cosmetic.constant.CosmeticSetPolicy.MIN_ITEM_COUNT;
import static com.likelion.tometa.domain.cosmetic.support.CosmeticProductNameFormatter.format;

@Service
@RequiredArgsConstructor
public class UserCosmeticService {

    private static final int MAX_MAIN_INGREDIENT_COUNT = 3;
    private static final int MAX_SEARCH_RESPONSE_INGREDIENT_COUNT = 2;
    private static final String MANUAL_SOURCE_TYPE = "manual";
    private static final String SEARCH_SOURCE_TYPE = "search";

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final CosmeticProductRepository cosmeticProductRepository;
    private final CosmeticIngredientRepository cosmeticIngredientRepository;
    private final CosmeticTagRepository cosmeticTagRepository;
    private final UserCosmeticRepository userCosmeticRepository;
    private final CosmeticSetRepository cosmeticSetRepository;
    private final CosmeticSetItemRepository cosmeticSetItemRepository;

    @Transactional
    public void createManualCosmetic(
            ManualCosmeticCreateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        validateMainIngredientCount(request.mainIngredients());
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
        cosmeticTagRepository.saveAll(
                createIngredientTags(cosmeticProduct, request.mainIngredients())
        );

        userCosmeticRepository.save(
                UserCosmetic.builder()
                        .user(user)
                        .cosmeticProduct(cosmeticProduct)
                        .build()
        );
    }

    @Transactional
    public SearchedCosmeticCreateResponseDto createSearchedCosmetic(
            User user,
            CosmeticSearchCandidate candidate
    ) {
        validateMainIngredientCount(candidate.mainIngredients());
        validateProductType(candidate.productType());

        CosmeticProduct cosmeticProduct = cosmeticProductRepository.save(
                CosmeticProduct.builder()
                        .createdByUser(user)
                        .sourceType(SEARCH_SOURCE_TYPE)
                        .productName(candidate.productName())
                        .brandName(candidate.brandName())
                        .productType(candidate.productType())
                        .imageUrl(candidate.imageUrl())
                        .build()
        );

        cosmeticIngredientRepository.saveAll(
                createMainIngredients(cosmeticProduct, candidate.mainIngredients())
        );
        cosmeticTagRepository.saveAll(
                createSearchTags(cosmeticProduct, candidate)
        );

        UserCosmetic userCosmetic = userCosmeticRepository.save(
                UserCosmetic.builder()
                        .user(user)
                        .cosmeticProduct(cosmeticProduct)
                        .build()
        );

        return new SearchedCosmeticCreateResponseDto(
                userCosmetic.getId(),
                format(
                        cosmeticProduct.getBrandName(),
                        cosmeticProduct.getProductName()
                ),
                cosmeticProduct.getProductType(),
                createSearchResponseTags(candidate)
        );
    }

    @Transactional
    public void deleteUserCosmetic(Long userCosmeticId, String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);

        UserCosmetic userCosmetic = userCosmeticRepository
                .findByIdAndUserAndDeletedAtIsNull(userCosmeticId, user)
                .orElseThrow(() -> new GeneralException(
                        CosmeticErrorCode.USER_COSMETIC_NOT_FOUND));

        removeUserCosmeticFromSets(userCosmetic, user);
        userCosmetic.softDelete();
    }

    private void removeUserCosmeticFromSets(UserCosmetic userCosmetic, User user) {
        List<CosmeticSet> affectedSets = cosmeticSetRepository
                .findAllContainingUserCosmeticForUpdate(userCosmetic, user);

        if (affectedSets.isEmpty()) {
            return;
        }

        Map<CosmeticSet, Long> itemCountBySet = cosmeticSetItemRepository
                .countItemsByCosmeticSetIn(affectedSets)
                .stream()
                .collect(Collectors.toMap(
                        CosmeticSetItemRepository.CosmeticSetItemCount::getCosmeticSet,
                        CosmeticSetItemRepository.CosmeticSetItemCount::getItemCount
                ));

        List<CosmeticSet> setsToDelete = affectedSets.stream()
                .filter(cosmeticSet -> itemCountBySet.getOrDefault(cosmeticSet, 0L) - 1
                        < MIN_ITEM_COUNT)
                .toList();

        cosmeticSetItemRepository.deleteAllByUserCosmeticAndCosmeticSetIn(
                userCosmetic,
                affectedSets
        );

        if (setsToDelete.isEmpty()) {
            return;
        }

        cosmeticSetItemRepository.deleteAllByCosmeticSetIn(setsToDelete);
        cosmeticSetRepository.deleteAll(setsToDelete);
    }

    private void validateMainIngredientCount(List<String> mainIngredients) {
        if (mainIngredients.size() > MAX_MAIN_INGREDIENT_COUNT) {
            throw new GeneralException(CosmeticErrorCode.MAIN_INGREDIENTS_LIMIT_EXCEEDED);
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

    private List<CosmeticTag> createIngredientTags(
            CosmeticProduct cosmeticProduct,
            List<String> ingredientNames
    ) {
        List<CosmeticTag> tags = new ArrayList<>(ingredientNames.size());

        for (int index = 0; index < ingredientNames.size(); index++) {
            tags.add(
                    CosmeticTag.builder()
                            .cosmeticProduct(cosmeticProduct)
                            .tagType(CosmeticTagType.INGREDIENT)
                            .name(ingredientNames.get(index))
                            .tagOrder(index + 1)
                            .build()
            );
        }

        return tags;
    }

    private List<CosmeticTag> createSearchTags(
            CosmeticProduct cosmeticProduct,
            CosmeticSearchCandidate candidate
    ) {
        List<CosmeticTag> tags = new ArrayList<>(candidate.mainIngredients().size() + 1);

        tags.add(
                CosmeticTag.builder()
                        .cosmeticProduct(cosmeticProduct)
                        .tagType(CosmeticTagType.BENEFIT)
                        .name(candidate.benefit())
                        .tagOrder(1)
                        .build()
        );

        for (int index = 0; index < candidate.mainIngredients().size(); index++) {
            tags.add(
                    CosmeticTag.builder()
                            .cosmeticProduct(cosmeticProduct)
                            .tagType(CosmeticTagType.INGREDIENT)
                            .name(candidate.mainIngredients().get(index))
                            .tagOrder(index + 1)
                            .build()
            );
        }

        return tags;
    }

    private List<String> createSearchResponseTags(CosmeticSearchCandidate candidate) {
        List<String> tags = new ArrayList<>(MAX_SEARCH_RESPONSE_INGREDIENT_COUNT + 2);

        tags.add(ProductType.displayNameOf(candidate.productType()));
        tags.add(candidate.benefit());

        candidate.mainIngredients().stream()
                .limit(MAX_SEARCH_RESPONSE_INGREDIENT_COUNT)
                .forEach(tags::add);

        return List.copyOf(tags);
    }
}
