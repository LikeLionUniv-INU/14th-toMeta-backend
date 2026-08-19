package com.likelion.tometa.domain.cosmetic.dto.response;

import java.util.List;

public record SearchedCosmeticCreateResponseDto(
        Long userCosmeticId,
        String productName,
        String productType,
        List<String> tags
) {
}
