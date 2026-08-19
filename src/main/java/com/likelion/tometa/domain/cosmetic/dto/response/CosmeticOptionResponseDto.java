package com.likelion.tometa.domain.cosmetic.dto.response;

import java.util.List;

public record CosmeticOptionResponseDto(
        List<SetOption> sets,
        List<CosmeticOption> cosmetics
) {
    public record SetOption(
            Long setId,
            String name,
            String usageTime,
            List<String> tags
    ) {
    }

    public record CosmeticOption(
            Long userCosmeticId,
            String productName,
            String brandName,
            String productType,
            List<String> tags
    ) {
    }
}
