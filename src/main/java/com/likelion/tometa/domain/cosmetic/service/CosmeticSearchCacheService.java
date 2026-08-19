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
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class CosmeticSearchCacheService {

    private final Cache<String, CosmeticSearchCacheEntry> cosmeticSearchCache;

    public String save(Long userId, List<CosmeticSearchCandidate> items) {
        String searchId = UUID.randomUUID().toString();
        cosmeticSearchCache.put(searchId, new CosmeticSearchCacheEntry(userId, items));
        return searchId;
    }

    public ConsumedSearchResult consumeSelectedItem(
            Long userId,
            String searchId,
            int itemId
    ) {
        AtomicReference<CosmeticSearchCacheEntry> consumedEntry = new AtomicReference<>();
        AtomicReference<CosmeticSearchCandidate> selectedCandidate = new AtomicReference<>();

        cosmeticSearchCache.asMap().compute(searchId, (key, entry) -> {
            if (entry == null || !entry.userId().equals(userId)) {
                throw new GeneralException(
                        CosmeticErrorCode.COSMETIC_SEARCH_RESULT_NOT_FOUND
                );
            }

            int index = itemId - 1;

            if (index < 0 || index >= entry.items().size()) {
                throw new GeneralException(
                        CosmeticErrorCode.COSMETIC_SEARCH_RESULT_NOT_FOUND
                );
            }

            consumedEntry.set(entry);
            selectedCandidate.set(entry.items().get(index));

            return null;
        });

        return new ConsumedSearchResult(
                consumedEntry.get(),
                selectedCandidate.get()
        );
    }

    public void restore(
            String searchId,
            CosmeticSearchCacheEntry entry
    ) {
        cosmeticSearchCache.asMap().putIfAbsent(searchId, entry);
    }

    public record ConsumedSearchResult(
            CosmeticSearchCacheEntry entry,
            CosmeticSearchCandidate candidate
    ) {
    }
}
