package com.likelion.tometa.domain.health.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HealthConnectionRequestDto(

        @NotBlank(message = "기기 ID는 필수입니다.")
        @Size(max = 255, message = "기기 ID는 255자 이하여야 합니다.")
        String deviceId
) {
}
