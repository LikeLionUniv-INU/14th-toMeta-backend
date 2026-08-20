package com.likelion.tometa.domain.cosmetic.support;

import java.util.List;

public record CosmeticSearchCandidate(
        String productName,
        String brandName,
        String productType,
        String imageUrl,
        String benefit,
        List<String> mainIngredients
) {
    public CosmeticSearchCandidate {
        mainIngredients = List.copyOf(mainIngredients);
    }
}
