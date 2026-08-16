package com.likelion.tometa.domain.record.dto.response;

import java.time.Instant;

public record RecordImageUploadUrlResponseDto(
        String uploadUrl,
        String objectKey,
        String httpMethod,
        String contentType,
        Instant expiresAt
) {
}