package com.likelion.tometa.domain.record.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.likelion.tometa.domain.record.constant.RecordImagePolicy.MAX_IMAGE_COUNT;

public record RecordImageUploadUrlRequestDto(

        @Valid
        @NotEmpty(message = "업로드할 이미지는 1장 이상이어야 합니다.")
        @Size(max = MAX_IMAGE_COUNT, message = "피부 사진은 최대 5장까지 등록할 수 있습니다.")
        List<@NotNull(message = "이미지 정보는 null일 수 없습니다.") ImageUploadRequest> images

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
