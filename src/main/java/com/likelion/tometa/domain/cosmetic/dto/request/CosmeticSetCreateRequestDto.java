package com.likelion.tometa.domain.cosmetic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CosmeticSetCreateRequestDto(
        @NotBlank(message = "세트 이름은 필수입니다.")
        @Size(max = 100, message = "세트 이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "사용 시간은 필수입니다.")
        String usageTime,

        List<@NotNull(message = "화장품 ID는 필수입니다.")
                @Positive(message = "화장품 ID는 양수여야 합니다.") Long> userCosmeticIds
) {
    public CosmeticSetCreateRequestDto {
        if (name != null) {
            name = name.strip();
        }
    }
}
