package com.likelion.tometa.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenRegisterRequestDto(

        @NotBlank(message = "deviceId는 필수입니다.")
        @Size(max = 255, message = "deviceId는 255자 이하여야 합니다.")
        String deviceId,

        @NotBlank(message = "firebaseInstallationId는 필수입니다.")
        @Size(
                max = 512,
                message = "firebaseInstallationId는 512자 이하여야 합니다."
        )
        String firebaseInstallationId

) {
}
