package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmeticSetTagSelectorTest {

    @Test
    void select_prioritizesDuplicatesByCountThenMinimumOrderAndLimitsToThree() {
        CosmeticProduct first = product(101L);
        CosmeticProduct second = product(102L);
        CosmeticProduct third = product(103L);
        CosmeticSet cosmeticSet = cosmeticSet(21L);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, first), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, second), 2),
                setItem(33L, cosmeticSet, userCosmetic(13L, third), 3)
        );
        Map<Long, List<CosmeticTag>> tagsByProductId = tagsByProductId(List.of(
                tag(first, CosmeticTagType.INGREDIENT, "freq", 3),
                tag(first, CosmeticTagType.INGREDIENT, "alpha", 1),
                tag(first, CosmeticTagType.INGREDIENT, "beta", 2),
                tag(first, CosmeticTagType.BENEFIT, "fourth", 1),
                tag(second, CosmeticTagType.INGREDIENT, "freq", 2),
                tag(second, CosmeticTagType.INGREDIENT, "alpha", 4),
                tag(second, CosmeticTagType.BENEFIT, "fourth", 2),
                tag(third, CosmeticTagType.INGREDIENT, "freq", 5),
                tag(third, CosmeticTagType.INGREDIENT, "beta", 1),
                tag(third, CosmeticTagType.BENEFIT, "fourth", 3)
        ));

        List<String> result = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );

        assertEquals(List.of("fourth", "freq", "alpha"), result);
    }

    @Test
    void select_fillsMissingDuplicateTagsInStableOrder() {
        CosmeticProduct first = product(101L);
        CosmeticProduct second = product(102L);
        CosmeticSet cosmeticSet = cosmeticSet(21L);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, first), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, second), 2)
        );
        Map<Long, List<CosmeticTag>> tagsByProductId = tagsByProductId(List.of(
                tag(first, CosmeticTagType.INGREDIENT, "A1", 1),
                tag(first, CosmeticTagType.BENEFIT, "A2", 2),
                tag(second, CosmeticTagType.INGREDIENT, "B1", 1),
                tag(second, CosmeticTagType.BENEFIT, "B2", 2)
        ));

        List<String> firstResult = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );
        List<String> secondResult = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );

        assertEquals(List.of("B2", "A1", "B1"), firstResult);
        assertEquals(firstResult, secondResult);
    }

    @Test
    void select_returnsAllAvailableTagsWhenFewerThanThreeExist() {
        CosmeticProduct product = product(101L);
        CosmeticSet cosmeticSet = cosmeticSet(21L);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, product), 1)
        );
        Map<Long, List<CosmeticTag>> tagsByProductId = tagsByProductId(List.of(
                tag(product, CosmeticTagType.INGREDIENT, "only", 1)
        ));

        List<String> result = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );

        assertEquals(List.of("only"), result);
    }

    @Test
    void select_fallbackUsesOnlyBenefitAndIngredientTagsInsteadOfProductTypes() {
        CosmeticProduct productTypeOnly = product(101L, "product-type-only");
        CosmeticProduct allowedTags = product(102L, "allowed-tags-source");
        CosmeticSet cosmeticSet = cosmeticSet(21L);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, productTypeOnly), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, allowedTags), 2)
        );
        Map<Long, List<CosmeticTag>> tagsByProductId = tagsByProductId(List.of(
                tag(allowedTags, CosmeticTagType.INGREDIENT, "ingredient-a", 1),
                tag(allowedTags, CosmeticTagType.BENEFIT, "benefit-b", 1),
                tag(allowedTags, CosmeticTagType.INGREDIENT, "ingredient-c", 2)
        ));

        List<String> result = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );

        assertEquals(
                Set.of("ingredient-a", "benefit-b", "ingredient-c"),
                Set.copyOf(result)
        );
        assertFalse(result.stream().anyMatch(
                tag -> Set.of("product-type-only", "allowed-tags-source").contains(tag)
        ));
    }

    @Test
    void select_keepsCommonTagsFirstAndUsesAllowedTypesForMissingTags() {
        CosmeticProduct productTypeOnly = product(101L, "product-type-only");
        CosmeticProduct allowedTags = product(102L, "allowed-tags-source");
        CosmeticSet cosmeticSet = cosmeticSet(21L);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, productTypeOnly), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, allowedTags), 2)
        );
        Map<Long, List<CosmeticTag>> tagsByProductId = tagsByProductId(List.of(
                tag(productTypeOnly, CosmeticTagType.BENEFIT, "common", 1),
                tag(allowedTags, CosmeticTagType.BENEFIT, "common", 1),
                tag(allowedTags, CosmeticTagType.INGREDIENT, "ingredient-a", 1),
                tag(allowedTags, CosmeticTagType.INGREDIENT, "ingredient-b", 2)
        ));

        List<String> result = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );

        assertEquals("common", result.getFirst());
        assertEquals(3, result.size());
        assertTrue(Set.of("ingredient-a", "ingredient-b")
                .containsAll(result.subList(1, result.size())));
        assertFalse(result.stream().anyMatch(
                tag -> Set.of("product-type-only", "allowed-tags-source").contains(tag)
        ));
    }

    @Test
    void select_prioritizesCommonProductTypeBeforeFallbackTags() {
        CosmeticProduct first = product(101L, "shared-product-type");
        CosmeticProduct second = product(102L, "shared-product-type");
        CosmeticSet cosmeticSet = cosmeticSet(21L);
        List<CosmeticSetItem> items = List.of(
                setItem(31L, cosmeticSet, userCosmetic(11L, first), 1),
                setItem(32L, cosmeticSet, userCosmetic(12L, second), 2)
        );
        Map<Long, List<CosmeticTag>> tagsByProductId = tagsByProductId(List.of(
                tag(first, CosmeticTagType.INGREDIENT, "ingredient-a", 1),
                tag(second, CosmeticTagType.BENEFIT, "benefit-b", 1)
        ));

        List<String> result = CosmeticSetTagSelector.select(
                cosmeticSet,
                items,
                tagsByProductId
        );

        assertEquals("shared-product-type", result.getFirst());
        assertEquals(
                Set.of("shared-product-type", "ingredient-a", "benefit-b"),
                Set.copyOf(result)
        );
    }

    private Map<Long, List<CosmeticTag>> tagsByProductId(List<CosmeticTag> tags) {
        Map<Long, List<CosmeticTag>> result = new HashMap<>();
        for (CosmeticTag tag : tags) {
            result.computeIfAbsent(
                    tag.getCosmeticProduct().getId(),
                    ignored -> new java.util.ArrayList<>()
            ).add(tag);
        }
        return result;
    }

    private CosmeticProduct product(Long id) {
        return product(id, "product-type-" + id);
    }

    private CosmeticProduct product(Long id, String productType) {
        CosmeticProduct product = CosmeticProduct.builder()
                .sourceType("search")
                .productName("product-" + id)
                .productType(productType)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private UserCosmetic userCosmetic(Long id, CosmeticProduct product) {
        UserCosmetic userCosmetic = UserCosmetic.builder()
                .cosmeticProduct(product)
                .build();
        ReflectionTestUtils.setField(userCosmetic, "id", id);
        return userCosmetic;
    }

    private CosmeticSet cosmeticSet(Long id) {
        CosmeticSet cosmeticSet = CosmeticSet.builder()
                .name("set-" + id)
                .usageTime(CosmeticSetUsageTime.BOTH)
                .build();
        ReflectionTestUtils.setField(cosmeticSet, "id", id);
        return cosmeticSet;
    }

    private CosmeticSetItem setItem(
            Long id,
            CosmeticSet cosmeticSet,
            UserCosmetic userCosmetic,
            int itemOrder
    ) {
        CosmeticSetItem item = CosmeticSetItem.builder()
                .cosmeticSet(cosmeticSet)
                .userCosmetic(userCosmetic)
                .itemOrder(itemOrder)
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
