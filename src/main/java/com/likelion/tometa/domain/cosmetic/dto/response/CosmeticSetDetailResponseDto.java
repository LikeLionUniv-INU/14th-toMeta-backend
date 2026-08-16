package com.likelion.tometa.domain.cosmetic.dto.response;

import java.util.List;

public record CosmeticSetDetailResponseDto(
        Long setId,
        String name,
        String usageTime,
        List<Cosmetic> cosmetics
) {
    public record Cosmetic(
            Long userCosmeticId,
            String productName,
            String customName,
            String productType,
            List<String> mainIngredients
    ) {
    }
}
