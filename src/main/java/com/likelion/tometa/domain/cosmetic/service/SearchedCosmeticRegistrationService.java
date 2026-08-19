package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.dto.request.SearchedCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.SearchedCosmeticCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSearchCacheService.ConsumedSearchResult;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchedCosmeticRegistrationService {

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final CosmeticSearchCacheService cosmeticSearchCacheService;
    private final UserCosmeticService userCosmeticService;

    public SearchedCosmeticCreateResponseDto create(
            SearchedCosmeticCreateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);

        ConsumedSearchResult consumedResult =
                cosmeticSearchCacheService.consumeSelectedItem(
                        user.getId(),
                        request.searchId(),
                        request.itemId()
                );

        try {
            return userCosmeticService.createSearchedCosmetic(
                    user,
                    consumedResult.candidate()
            );
        } catch (RuntimeException e) {
            cosmeticSearchCacheService.restore(
                    request.searchId(),
                    consumedResult.entry()
            );
            throw e;
        }
    }
}
