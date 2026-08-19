package com.likelion.tometa.global.config.s3;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.storage.s3.orphan-cleanup")
public record S3OrphanCleanupProperties(
        @NotNull(message = "고아 이미지 보관 시간은 필수입니다.")
        Duration retention,

        @Min(value = 1, message = "고아 이미지 정리 배치 크기는 1 이상이어야 합니다.")
        @Max(value = 1000, message = "고아 이미지 정리 배치 크기는 1000 이하여야 합니다.")
        int batchSize,

        @NotBlank(message = "고아 이미지 정리 cron 표현식은 필수입니다.")
        String cron,

        @NotBlank(message = "고아 이미지 정리 시간대는 필수입니다.")
        String zone
) {

    @AssertTrue(message = "고아 이미지 보관 시간은 0보다 커야 합니다.")
    public boolean isRetentionPositive() {
        return retention == null || (!retention.isZero() && !retention.isNegative());
    }
}
