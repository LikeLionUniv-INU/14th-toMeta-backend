package com.likelion.tometa.domain.health.dto.request;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record HealthRawRecordRequestDto(

        @NotBlank(message = "Health Connect 레코드 ID는 필수입니다.")
        @Size(max = 255, message = "Health Connect 레코드 ID는 255자 이하여야 합니다.")
        String hcRecordId,

        @NotBlank(message = "Health Connect 데이터 타입은 필수입니다.")
        @Size(max = 50, message = "Health Connect 데이터 타입은 50자 이하여야 합니다.")
        String recordType,

        @NotNull(message = "시작 시간은 필수입니다.")
        Instant startTime,

        Instant endTime,

        @NotNull(message = "Health Connect 원본 데이터는 필수입니다.")
        JsonNode payload
) {

    @AssertTrue(message = "종료 시간은 시작 시간보다 빠를 수 없습니다.")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || !endTime.isBefore(startTime);
    }

    @AssertTrue(message = "Health Connect 원본 데이터는 JSON Object 형식이어야 합니다.")
    public boolean isPayloadObject() {
        return payload == null || payload.isObject();
    }
}
