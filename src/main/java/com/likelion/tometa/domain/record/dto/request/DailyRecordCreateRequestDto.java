package com.likelion.tometa.domain.record.dto.request;

import com.likelion.tometa.domain.record.constant.RecordImagePolicy;
import com.likelion.tometa.domain.record.constant.DailyRecordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record DailyRecordCreateRequestDto(
        @NotNull(message = "기록 날짜는 필수입니다.")
        LocalDate date,

        @NotBlank(message = "피부 상태는 필수입니다.")
        String skinStatus,

        @NotNull(message = "아침 화장품 목록은 필수입니다.")
        List<@NotNull(message = "화장품 ID는 null일 수 없습니다.")
                @Positive(message = "화장품 ID는 양수여야 합니다.") Long> morningCosmeticIds,

        List<@NotNull(message = "화장품 세트 ID는 null일 수 없습니다.")
                @Positive(message = "화장품 세트 ID는 양수여야 합니다.") Long> morningCosmeticSetIds,

        @NotNull(message = "저녁 화장품 목록은 필수입니다.")
        List<@NotNull(message = "화장품 ID는 null일 수 없습니다.")
                @Positive(message = "화장품 ID는 양수여야 합니다.") Long> nightCosmeticIds,

        List<@NotNull(message = "화장품 세트 ID는 null일 수 없습니다.")
                @Positive(message = "화장품 세트 ID는 양수여야 합니다.") Long> nightCosmeticSetIds,

        @Size(max = DailyRecordPolicy.MAX_MEMO_LENGTH,
                message = "음식 메모는 300자 이하여야 합니다.")
        String foodMemo,

        @Size(max = RecordImagePolicy.MAX_IMAGE_COUNT,
                message = "피부 사진은 최대 5장까지 등록할 수 있습니다.")
        List<@NotBlank(message = "이미지 키는 비어 있을 수 없습니다.") String> imageKeys,

        @Size(max = DailyRecordPolicy.MAX_MEMO_LENGTH,
                message = "특이사항은 300자 이하여야 합니다.")
        String memo
) {
    public DailyRecordCreateRequestDto {
        skinStatus = strip(skinStatus);
        foodMemo = stripToNull(foodMemo);
        memo = stripToNull(memo);
        morningCosmeticSetIds = emptyIfNull(morningCosmeticSetIds);
        nightCosmeticSetIds = emptyIfNull(nightCosmeticSetIds);
        imageKeys = emptyIfNull(imageKeys);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static <T> List<T> emptyIfNull(List<T> values) {
        return values == null ? List.of() : values;
    }
}
