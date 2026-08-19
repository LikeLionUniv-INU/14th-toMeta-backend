package com.likelion.tometa.domain.cosmetic.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCacheEntry;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CosmeticSearchCacheService {

    private final Cache<String, CosmeticSearchCacheEntry> cosmeticSearchCache;

    public String save(Long userId, List<CosmeticSearchCandidate> items) {
        String searchId = UUID.randomUUID().toString();
        cosmeticSearchCache.put(searchId, new CosmeticSearchCacheEntry(userId, items));
        return searchId;
    }

    public CosmeticSearchCandidate getSelectedItem(Long userId, String searchId, int itemId) {
        CosmeticSearchCacheEntry entry = cosmeticSearchCache.getIfPresent(searchId);

        if (entry == null || !entry.userId().equals(userId)) {
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_RESULT_NOT_FOUND);
        }

        int index = itemId - 1;

        if (index < 0 || index >= entry.items().size()) {
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_RESULT_NOT_FOUND);
        }

        return entry.items().get(index);
    }

    public void evict(String searchId) {
        cosmeticSearchCache.invalidate(searchId);
    }
}
