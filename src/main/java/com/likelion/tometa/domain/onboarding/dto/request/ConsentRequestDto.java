package com.likelion.tometa.domain.onboarding.dto.request;

import jakarta.validation.constraints.NotNull;

public record ConsentRequestDto(
        @NotNull(message = "서비스 이용약관 동의 여부는 필수입니다.")
        Boolean termsAgreed,

        @NotNull(message = "개인정보 수집 및 이용 동의 여부는 필수입니다.")
        Boolean privacyAgreed
) {
}