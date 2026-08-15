package com.likelion.tometa.domain.cosmetic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ManualCosmeticCreateRequestDto(
        @NotBlank(message = "제품명은 필수입니다.")
        @Size(max = 255, message = "제품명은 255자 이하여야 합니다.")
        String productName,

        @NotBlank(message = "제품 유형은 필수입니다.")
        String productType,

        @NotNull(message = "주요 성분 목록은 필수입니다.")
        @Size(min = 1, message = "주요 성분은 최소 1개 이상 입력해야 합니다.")
        List<@NotBlank(message = "주요 성분명은 비어 있을 수 없습니다.")
                @Size(max = 100, message = "주요 성분명은 100자 이하여야 합니다.") String> mainIngredients
) {
}
