package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticOptionResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
import com.likelion.tometa.domain.cosmetic.enums.ProductType;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticTagRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.likelion.tometa.domain.cosmetic.support.CosmeticProductNameFormatter.format;

@Service
@RequiredArgsConstructor
public class CosmeticOptionService {

    private static final int MAX_MANUAL_INGREDIENT_TAG_COUNT = 3;
    private static final int MAX_SEARCH_INGREDIENT_TAG_COUNT = 2;
    private static final String MANUAL_SOURCE_TYPE = "manual";

    private static final Comparator<CosmeticTag> TAG_ORDER = Comparator
            .comparingInt(CosmeticTag::getTagOrder)
            .thenComparing(tag -> tag.getTagType().name())
            .thenComparing(CosmeticTag::getName)
            .thenComparing(tag -> tag.getId() == null ? Long.MAX_VALUE : tag.getId());

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final UserCosmeticRepository userCosmeticRepository;
    private final CosmeticSetRepository cosmeticSetRepository;
    private final CosmeticSetItemRepository cosmeticSetItemRepository;
    private final CosmeticTagRepository cosmeticTagRepository;

    @Transactional
    public CosmeticOptionResponseDto getCosmeticOptions(String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);

        List<UserCosmetic> userCosmetics = userCosmeticRepository
                .findAllActiveByUserOrderByNewest(user);
        List<CosmeticSet> cosmeticSets = cosmeticSetRepository
                .findAllByUserOrderByCreatedAtDescIdDesc(user);
        List<CosmeticSetItem> setItems = cosmeticSets.isEmpty()
                ? List.of()
                : cosmeticSetItemRepository
                        .findAllActiveByCosmeticSetsOrderByItemOrder(cosmeticSets);

        Map<Long, List<CosmeticTag>> tagsByProductId = loadTagsByProductId(
                userCosmetics,
                setItems
        );
        Map<Long, List<CosmeticSetItem>> itemsBySetId = groupItemsBySetId(setItems);

        List<CosmeticOptionResponseDto.SetOption> setOptions = cosmeticSets.stream()
                .map(cosmeticSet -> toSetOption(
                        cosmeticSet,
                        itemsBySetId.getOrDefault(cosmeticSet.getId(), List.of()),
                        tagsByProductId
                ))
                .toList();
        List<CosmeticOptionResponseDto.CosmeticOption> cosmeticOptions = userCosmetics
                .stream()
                .map(userCosmetic -> toCosmeticOption(userCosmetic, tagsByProductId))
                .toList();

        return new CosmeticOptionResponseDto(setOptions, cosmeticOptions);
    }

    private Map<Long, List<CosmeticTag>> loadTagsByProductId(
            List<UserCosmetic> userCosmetics,
            List<CosmeticSetItem> setItems
    ) {
        Set<Long> productIds = new LinkedHashSet<>();
        userCosmetics.forEach(userCosmetic ->
                productIds.add(userCosmetic.getCosmeticProduct().getId()));
        setItems.forEach(item ->
                productIds.add(item.getUserCosmetic().getCosmeticProduct().getId()));

        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<CosmeticTag>> tagsByProductId = new HashMap<>();
        for (CosmeticTag tag : cosmeticTagRepository
                .findAllByCosmeticProductIds(productIds)) {
            tagsByProductId.computeIfAbsent(
                    tag.getCosmeticProduct().getId(),
                    ignored -> new ArrayList<>()
            ).add(tag);
        }
        tagsByProductId.values().forEach(tags -> tags.sort(TAG_ORDER));
        return tagsByProductId;
    }

    private Map<Long, List<CosmeticSetItem>> groupItemsBySetId(
            List<CosmeticSetItem> setItems
    ) {
        Map<Long, List<CosmeticSetItem>> itemsBySetId = new LinkedHashMap<>();
        for (CosmeticSetItem item : setItems) {
            itemsBySetId.computeIfAbsent(
                    item.getCosmeticSet().getId(),
                    ignored -> new ArrayList<>()
            ).add(item);
        }
        return itemsBySetId;
    }

    private CosmeticOptionResponseDto.CosmeticOption toCosmeticOption(
            UserCosmetic userCosmetic,
            Map<Long, List<CosmeticTag>> tagsByProductId
    ) {
        CosmeticProduct product = userCosmetic.getCosmeticProduct();
        List<CosmeticTag> productTags = tagsByProductId.getOrDefault(
                product.getId(),
                List.of()
        );
        List<String> tags = new ArrayList<>();
        tags.add(ProductType.displayNameOf(product.getProductType()));

        if (MANUAL_SOURCE_TYPE.equals(product.getSourceType())) {
            appendTags(
                    tags,
                    productTags,
                    CosmeticTagType.INGREDIENT,
                    MAX_MANUAL_INGREDIENT_TAG_COUNT
            );
        } else {
            appendTags(tags, productTags, CosmeticTagType.BENEFIT, 1);
            appendTags(
                    tags,
                    productTags,
                    CosmeticTagType.INGREDIENT,
                    MAX_SEARCH_INGREDIENT_TAG_COUNT
            );
        }

        return new CosmeticOptionResponseDto.CosmeticOption(
                userCosmetic.getId(),
                format(product.getBrandName(), product.getProductName()),
                product.getBrandName(),
                product.getProductType(),
                tags
        );
    }

    private void appendTags(
            List<String> result,
            List<CosmeticTag> productTags,
            CosmeticTagType tagType,
            int limit
    ) {
        productTags.stream()
                .filter(tag -> tag.getTagType() == tagType)
                .limit(limit)
                .map(CosmeticTag::getName)
                .forEach(result::add);
    }

    private CosmeticOptionResponseDto.SetOption toSetOption(
            CosmeticSet cosmeticSet,
            List<CosmeticSetItem> setItems,
            Map<Long, List<CosmeticTag>> tagsByProductId
    ) {
        return new CosmeticOptionResponseDto.SetOption(
                cosmeticSet.getId(),
                cosmeticSet.getName(),
                cosmeticSet.getUsageTime().getValue(),
                CosmeticSetTagSelector.select(cosmeticSet, setItems, tagsByProductId)
        );
    }
}
