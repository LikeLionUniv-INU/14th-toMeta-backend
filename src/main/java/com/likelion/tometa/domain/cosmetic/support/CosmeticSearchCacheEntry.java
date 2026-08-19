package com.likelion.tometa.domain.cosmetic.support;

import java.util.List;

public record CosmeticSearchCacheEntry(Long userId, List<CosmeticSearchCandidate> items) {
    public CosmeticSearchCacheEntry {
        items = List.copyOf(items);
    }
}
