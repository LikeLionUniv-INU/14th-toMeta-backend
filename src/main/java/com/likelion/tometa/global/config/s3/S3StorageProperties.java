package com.likelion.tometa.global.config.s3;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3StorageProperties(
        @NotBlank(message = "S3 Bucket 이름은 필수입니다.")
        String bucket,

        @NotBlank(message = "S3 Region은 필수입니다.")
        String region,

        @Min(value = 1, message = "Presigned URL 만료 시간은 1분 이상이어야 합니다.")
        long presignedUploadExpirationMinutes,

        @Min(value = 1, message = "Presigned 조회 URL 만료 시간은 1분 이상이어야 합니다.")
        long presignedDownloadExpirationMinutes,

        @Min(value = 1, message = "최대 업로드 크기는 1byte 이상이어야 합니다.")
        long maxUploadSizeBytes
) {
}
