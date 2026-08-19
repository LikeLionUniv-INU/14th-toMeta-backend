package com.likelion.tometa.domain.record.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DailyRecordDetailResponseDto(
        Long recordId,
        LocalDate date,
        String skinStatus,
        List<Selection> morningSelections,
        List<Selection> nightSelections,
        String foodMemo,
        List<Image> images,
        String memo
) {
    public sealed interface Selection permits SetSelection, CosmeticSelection {
    }

    public record SetSelection(
            String selectionType,
            Long cosmeticSetId,
            String name,
            List<String> ingredientTags
    ) implements Selection {

        public SetSelection(Long cosmeticSetId, String name, List<String> ingredientTags) {
            this("SET", cosmeticSetId, name, ingredientTags);
        }
    }

    public record CosmeticSelection(
            String selectionType,
            Long userCosmeticId,
            String name,
            List<String> ingredientTags
    ) implements Selection {

        public CosmeticSelection(Long userCosmeticId, String name, List<String> ingredientTags) {
            this("COSMETIC", userCosmeticId, name, ingredientTags);
        }
    }

    public record Image(
            String imageKey,
            String imageUrl
    ) {
    }
}
