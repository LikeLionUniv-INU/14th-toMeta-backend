package com.likelion.tometa.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfileRequestDto(

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 10, message = "닉네임은 10자 이하여야 합니다.")
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9]+$",
                message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
        )
        String nickname,

        @NotBlank(message = "성별은 필수입니다.")
        @Pattern(
                regexp = "^(male|female)$",
                message = "올바른 성별을 선택해주세요."
        )
        String gender,

        @NotBlank(message = "나이대는 필수입니다.")
        @Pattern(
                regexp = "^(10s|20s|30s|40s|etc)$",
                message = "올바른 나이대를 선택해주세요."
        )
        String ageGroup,

        @NotBlank(message = "피부 타입은 필수입니다.")
        @Pattern(
                regexp = "^(dry|oily|combination_dry|combination|sensitive|unknown)$",
                message = "올바른 피부 타입을 선택해주세요."
        )
        String skinType
) {
}
