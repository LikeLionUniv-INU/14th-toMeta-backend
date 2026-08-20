package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.client.OpenAiCosmeticSearchClient;
import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSearchResponseDto;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.likelion.tometa.domain.cosmetic.support.CosmeticProductNameFormatter.format;

@Service
@RequiredArgsConstructor
public class CosmeticSearchService {

    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final String UNKNOWN_BRAND_NAME = "-";

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final OpenAiCosmeticSearchClient cosmeticSearchClient;
    private final CosmeticSearchCacheService cosmeticSearchCacheService;

    public CosmeticSearchResponseDto search(String keyword, String sessionToken) {
        User user = sessionUserResolver.resolve(sessionToken);
        String normalizedKeyword = normalizeKeyword(keyword);

        List<CosmeticSearchCandidate> candidates = cosmeticSearchClient.search(normalizedKeyword);
        if (candidates.isEmpty()) {
            return new CosmeticSearchResponseDto(null, List.of());
        }

        String searchId = cosmeticSearchCacheService.save(user.getId(), candidates);
        return new CosmeticSearchResponseDto(searchId, createResponseItems(candidates));
    }

    private String normalizeKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();

        if (normalized.length() < MIN_KEYWORD_LENGTH || normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SEARCH_KEYWORD_INVALID);
        }

        return normalized;
    }

    private List<CosmeticSearchResponseDto.Item> createResponseItems(
            List<CosmeticSearchCandidate> candidates
    ) {
        List<CosmeticSearchResponseDto.Item> items = new ArrayList<>(candidates.size());

        for (int index = 0; index < candidates.size(); index++) {
            CosmeticSearchCandidate candidate = candidates.get(index);

            items.add(new CosmeticSearchResponseDto.Item(
                    index + 1,
                    format(candidate.brandName(), candidate.productName()),
                    resolveBrandName(candidate.brandName()),
                    candidate.productType(),
                    candidate.imageUrl()
            ));
        }

        return List.copyOf(items);
    }

    private String resolveBrandName(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            return UNKNOWN_BRAND_NAME;
        }

        return brandName.trim();
    }
}
