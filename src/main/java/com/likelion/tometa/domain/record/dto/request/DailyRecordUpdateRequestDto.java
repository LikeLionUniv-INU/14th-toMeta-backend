package com.likelion.tometa.domain.record.dto.request;

import java.util.List;

public final class DailyRecordUpdateRequestDto {

    private boolean skinStatusPresent;
    private String skinStatus;
    private boolean morningCosmeticIdsPresent;
    private List<Long> morningCosmeticIds;
    private boolean morningCosmeticSetIdsPresent;
    private List<Long> morningCosmeticSetIds;
    private boolean nightCosmeticIdsPresent;
    private List<Long> nightCosmeticIds;
    private boolean nightCosmeticSetIdsPresent;
    private List<Long> nightCosmeticSetIds;
    private boolean foodMemoPresent;
    private String foodMemo;
    private boolean imageKeysPresent;
    private List<String> imageKeys;
    private boolean memoPresent;
    private String memo;

    public boolean hasSkinStatus() {
        return skinStatusPresent;
    }

    public String skinStatus() {
        return skinStatus;
    }

    public void setSkinStatus(String skinStatus) {
        this.skinStatusPresent = true;
        this.skinStatus = strip(skinStatus);
    }

    public boolean hasMorningCosmeticIds() {
        return morningCosmeticIdsPresent;
    }

    public List<Long> morningCosmeticIds() {
        return morningCosmeticIds;
    }

    public void setMorningCosmeticIds(List<Long> morningCosmeticIds) {
        this.morningCosmeticIdsPresent = true;
        this.morningCosmeticIds = immutableOrNull(morningCosmeticIds);
    }

    public boolean hasMorningCosmeticSetIds() {
        return morningCosmeticSetIdsPresent;
    }

    public List<Long> morningCosmeticSetIds() {
        return morningCosmeticSetIds;
    }

    public void setMorningCosmeticSetIds(List<Long> morningCosmeticSetIds) {
        this.morningCosmeticSetIdsPresent = true;
        this.morningCosmeticSetIds = immutableOrNull(morningCosmeticSetIds);
    }

    public boolean hasNightCosmeticIds() {
        return nightCosmeticIdsPresent;
    }

    public List<Long> nightCosmeticIds() {
        return nightCosmeticIds;
    }

    public void setNightCosmeticIds(List<Long> nightCosmeticIds) {
        this.nightCosmeticIdsPresent = true;
        this.nightCosmeticIds = immutableOrNull(nightCosmeticIds);
    }

    public boolean hasNightCosmeticSetIds() {
        return nightCosmeticSetIdsPresent;
    }

    public List<Long> nightCosmeticSetIds() {
        return nightCosmeticSetIds;
    }

    public void setNightCosmeticSetIds(List<Long> nightCosmeticSetIds) {
        this.nightCosmeticSetIdsPresent = true;
        this.nightCosmeticSetIds = immutableOrNull(nightCosmeticSetIds);
    }

    public boolean hasFoodMemo() {
        return foodMemoPresent;
    }

    public String foodMemo() {
        return foodMemo;
    }

    public void setFoodMemo(String foodMemo) {
        this.foodMemoPresent = true;
        this.foodMemo = stripToNull(foodMemo);
    }

    public boolean hasImageKeys() {
        return imageKeysPresent;
    }

    public List<String> imageKeys() {
        return imageKeys;
    }

    public void setImageKeys(List<String> imageKeys) {
        this.imageKeysPresent = true;
        this.imageKeys = immutableOrNull(imageKeys);
    }

    public boolean hasMemo() {
        return memoPresent;
    }

    public String memo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memoPresent = true;
        this.memo = stripToNull(memo);
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

    private static <T> List<T> immutableOrNull(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
