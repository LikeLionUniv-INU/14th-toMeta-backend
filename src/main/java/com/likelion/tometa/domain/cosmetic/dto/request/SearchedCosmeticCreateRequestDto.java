package com.likelion.tometa.domain.cosmetic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SearchedCosmeticCreateRequestDto(
        @NotBlank(message = "검색 ID는 필수입니다.")
        String searchId,

        @NotNull(message = "검색 결과 항목 ID는 필수입니다.")
        @Positive(message = "검색 결과 항목 ID는 1 이상이어야 합니다.")
        Integer itemId
) {
    public SearchedCosmeticCreateRequestDto {
        searchId = searchId == null ? null : searchId.trim();
    }
}
