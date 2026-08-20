package com.likelion.tometa.domain.mypage.dto.response;

public record UserProfileResponseDto(
        String nickname,
        String gender,
        String ageGroup,
        String skinType
) {
}
