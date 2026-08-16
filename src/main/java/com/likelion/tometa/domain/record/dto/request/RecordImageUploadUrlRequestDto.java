package com.likelion.tometa.domain.record.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

public record RecordImageUploadUrlRequestDto(

        @NotEmpty(message = "업로드할 이미지는 1장 이상이어야 합니다.")
        @Size(max = 3, message = "피부 사진은 최대 3장까지 등록할 수 있습니다.")
        List<ImageUploadRequest> images
) {
        public record ImageUploadRequest(
                @NotBlank(message = "contentType은 필수입니다.")
                String contentType,

                @NotNull(message = "fileSize는 필수입니다.")
                @Positive(message = "fileSize는 0보다 커야 합니다.")
                Long fileSize
        ) {
        }
}
