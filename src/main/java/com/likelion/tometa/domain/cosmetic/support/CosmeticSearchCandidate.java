package com.likelion.tometa.domain.cosmetic.support;

import java.util.List;

public record CosmeticSearchCandidate(
        String productName,
        String productType,
        String imageUrl,
        List<String> mainIngredients
) {
}
