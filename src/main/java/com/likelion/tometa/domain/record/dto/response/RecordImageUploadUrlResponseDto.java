package com.likelion.tometa.domain.record.dto.response;

import java.time.Instant;
import java.util.List;

public record RecordImageUploadUrlResponseDto(
        List<UploadInfo> uploads
) {
    public record UploadInfo(
            String uploadUrl,
            String objectKey,
            String httpMethod,
            String contentType,
            Instant expiresAt
    ) {
    }
}
