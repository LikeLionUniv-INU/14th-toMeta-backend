package com.likelion.tometa.domain.record.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RecordImageUploadUrlRequestDto(

        @NotBlank(message = "contentType은 필수입니다.")
        String contentType,

        @NotNull(message = "fileSize는 필수입니다.")
        @Positive(message = "fileSize는 0보다 커야 합니다.")
        Long fileSize
) {
}