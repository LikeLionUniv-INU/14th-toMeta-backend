package com.likelion.tometa.domain.cosmetic.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCacheEntry;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
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
}
