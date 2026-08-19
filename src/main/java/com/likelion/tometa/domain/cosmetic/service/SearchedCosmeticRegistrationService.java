package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.dto.request.SearchedCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.SearchedCosmeticCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
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

        CosmeticSearchCandidate candidate = cosmeticSearchCacheService.getSelectedItem(
                user.getId(),
                request.searchId(),
                request.itemId()
        );

        SearchedCosmeticCreateResponseDto result = userCosmeticService.createSearchedCosmetic(
                user,
                candidate
        );

        cosmeticSearchCacheService.evict(request.searchId());
        return result;
    }
}
