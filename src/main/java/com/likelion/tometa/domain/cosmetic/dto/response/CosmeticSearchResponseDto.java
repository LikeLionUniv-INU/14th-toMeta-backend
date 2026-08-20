package com.likelion.tometa.domain.cosmetic.dto.response;

import java.util.List;

public record CosmeticSearchResponseDto(String searchId, List<Item> items) {
    public record Item(
            int itemId,
            String productName,
            String brandName,
            String productType,
            String imageUrl
    ) {
    }
}
