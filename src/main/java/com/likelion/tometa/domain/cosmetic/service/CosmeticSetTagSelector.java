package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticTagType;
import com.likelion.tometa.domain.cosmetic.enums.ProductType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CosmeticSetTagSelector {

    private static final int MAX_SET_TAG_COUNT = 3;
    private static final Set<SetTagType> FALLBACK_TAG_TYPES = Set.of(
            SetTagType.BENEFIT,
            SetTagType.INGREDIENT
    );

    private CosmeticSetTagSelector() {
    }

    public static List<String> select(
            CosmeticSet cosmeticSet,
            List<CosmeticSetItem> setItems,
            Map<Long, List<CosmeticTag>> tagsByProductId
    ) {
        List<ComponentTags> componentTags = new ArrayList<>();
        Map<TagKey, TagAggregate> aggregateByKey = new HashMap<>();

        for (CosmeticSetItem item : setItems) {
            CosmeticProduct product = item.getUserCosmetic().getCosmeticProduct();
            Map<TagKey, Integer> minimumOrderByKey = new HashMap<>();
            minimumOrderByKey.put(
                    new TagKey(
                            SetTagType.PRODUCT_TYPE,
                            ProductType.displayNameOf(product.getProductType())
                    ),
                    0
            );

            for (CosmeticTag tag : tagsByProductId.getOrDefault(product.getId(), List.of())) {
                TagKey key = new TagKey(toSetTagType(tag.getTagType()), tag.getName());
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

    private static void fillWithStableFallback(
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

    private static FallbackComponent toFallbackComponent(
            long setSeed,
            ComponentTags component
    ) {
        CosmeticSetItem item = component.item();
        long itemId = nullableId(item.getUserCosmetic().getId());
        List<TagCandidate> shuffledTags = component.tags().stream()
                .filter(candidate -> FALLBACK_TAG_TYPES.contains(candidate.key().type()))
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

    private static long stableScore(long setSeed, long itemId, TagKey key) {
        long value = setSeed * 0x9E3779B97F4A7C15L + itemId;
        if (key != null) {
            value = value * 31 + key.type().name().hashCode();
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

    private static SetTagType toSetTagType(CosmeticTagType tagType) {
        return switch (tagType) {
            case BENEFIT -> SetTagType.BENEFIT;
            case INGREDIENT -> SetTagType.INGREDIENT;
        };
    }

    private enum SetTagType {
        PRODUCT_TYPE,
        INGREDIENT,
        BENEFIT
    }

    private record TagKey(SetTagType type, String name) {
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
