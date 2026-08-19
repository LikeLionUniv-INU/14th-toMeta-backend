package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticOptionResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CosmeticOptionService {

    private static final int MAX_SET_TAG_COUNT = 3;
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
        tags.add(product.getProductType());

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
                product.getProductName(),
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
                selectSetTags(cosmeticSet, setItems, tagsByProductId)
        );
    }

    private List<String> selectSetTags(
            CosmeticSet cosmeticSet,
            List<CosmeticSetItem> setItems,
            Map<Long, List<CosmeticTag>> tagsByProductId
    ) {
        List<ComponentTags> componentTags = new ArrayList<>();
        Map<TagKey, TagAggregate> aggregateByKey = new HashMap<>();

        for (CosmeticSetItem item : setItems) {
            Long productId = item.getUserCosmetic().getCosmeticProduct().getId();
            Map<TagKey, Integer> minimumOrderByKey = new HashMap<>();

            for (CosmeticTag tag : tagsByProductId.getOrDefault(productId, List.of())) {
                TagKey key = new TagKey(tag.getTagType(), tag.getName());
                minimumOrderByKey.merge(key, tag.getTagOrder(), Math::min);
            }

            minimumOrderByKey.forEach((key, minimumOrder) -> aggregateByKey
                    .computeIfAbsent(key, ignored -> new TagAggregate(key))
                    .addOccurrence(minimumOrder));

            List<TagCandidate> candidates = minimumOrderByKey.entrySet().stream()
                    .map(entry -> new TagCandidate(entry.getKey(), entry.getValue()))
                    .toList();
            componentTags.add(new ComponentTags(item, candidates));
        }

        List<TagAggregate> duplicateTags = aggregateByKey.values().stream()
                .filter(aggregate -> aggregate.occurrenceCount() >= 2)
                .sorted(Comparator
                        .comparingInt(TagAggregate::occurrenceCount).reversed()
                        .thenComparingInt(TagAggregate::minimumOrder)
                        .thenComparing(aggregate -> aggregate.key().type().name())
                        .thenComparing(aggregate -> aggregate.key().name()))
                .toList();

        List<TagKey> selected = new ArrayList<>(MAX_SET_TAG_COUNT);
        duplicateTags.stream()
                .limit(MAX_SET_TAG_COUNT)
                .map(TagAggregate::key)
                .forEach(selected::add);

        if (selected.size() < MAX_SET_TAG_COUNT) {
            fillWithStableFallback(cosmeticSet, componentTags, selected);
        }

        return selected.stream().map(TagKey::name).toList();
    }

    private void fillWithStableFallback(
            CosmeticSet cosmeticSet,
            List<ComponentTags> componentTags,
            List<TagKey> selected
    ) {
        long setSeed = cosmeticSet.getId() == null ? 0L : cosmeticSet.getId();
        Set<TagKey> selectedKeys = new HashSet<>(selected);

        List<FallbackComponent> fallbackComponents = componentTags.stream()
                .map(component -> toFallbackComponent(setSeed, component))
                .sorted(Comparator
                        .comparingLong(FallbackComponent::score)
                        .thenComparingInt(component -> component.item().getItemOrder())
                        .thenComparingLong(component -> nullableId(
                                component.item().getUserCosmetic().getId())))
                .toList();

        boolean added;
        do {
            added = false;
            for (FallbackComponent component : fallbackComponents) {
                TagKey next = component.nextUnselected(selectedKeys);
                if (next == null) {
                    continue;
                }
                selected.add(next);
                selectedKeys.add(next);
                added = true;
                if (selected.size() == MAX_SET_TAG_COUNT) {
                    return;
                }
            }
        } while (added);
    }

    private FallbackComponent toFallbackComponent(long setSeed, ComponentTags component) {
        CosmeticSetItem item = component.item();
        long itemId = nullableId(item.getUserCosmetic().getId());
        List<TagCandidate> shuffledTags = component.tags().stream()
                .sorted(Comparator
                        .comparingLong((TagCandidate candidate) -> stableScore(
                                setSeed,
                                itemId,
                                candidate.key()
                        ))
                        .thenComparingInt(TagCandidate::order)
                        .thenComparing(candidate -> candidate.key().type().name())
                        .thenComparing(candidate -> candidate.key().name()))
                .toList();

        return new FallbackComponent(
                item,
                shuffledTags,
                stableScore(setSeed, itemId, null)
        );
    }

    private long stableScore(long setSeed, long itemId, TagKey key) {
        long value = setSeed * 0x9E3779B97F4A7C15L + itemId;
        if (key != null) {
            value = value * 31 + key.type().ordinal();
            value = value * 31 + key.name().hashCode();
        }
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static long nullableId(Long id) {
        return id == null ? 0L : id;
    }

    private record TagKey(CosmeticTagType type, String name) {
    }

    private record TagCandidate(TagKey key, int order) {
    }

    private record ComponentTags(CosmeticSetItem item, List<TagCandidate> tags) {
    }

    private static final class TagAggregate {

        private final TagKey key;
        private int occurrenceCount;
        private int minimumOrder = Integer.MAX_VALUE;

        private TagAggregate(TagKey key) {
            this.key = key;
        }

        private void addOccurrence(int order) {
            occurrenceCount++;
            minimumOrder = Math.min(minimumOrder, order);
        }

        private TagKey key() {
            return key;
        }

        private int occurrenceCount() {
            return occurrenceCount;
        }

        private int minimumOrder() {
            return minimumOrder;
        }
    }

    private static final class FallbackComponent {

        private final CosmeticSetItem item;
        private final List<TagCandidate> tags;
        private final long score;
        private int cursor;

        private FallbackComponent(
                CosmeticSetItem item,
                List<TagCandidate> tags,
                long score
        ) {
            this.item = item;
            this.tags = tags;
            this.score = score;
        }

        private TagKey nextUnselected(Set<TagKey> selected) {
            while (cursor < tags.size()) {
                TagKey candidate = tags.get(cursor++).key();
                if (!selected.contains(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private CosmeticSetItem item() {
            return item;
        }

        private long score() {
            return score;
        }
    }
}
