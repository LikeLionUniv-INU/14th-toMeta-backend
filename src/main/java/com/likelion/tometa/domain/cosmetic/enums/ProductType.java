package com.likelion.tometa.domain.cosmetic.enums;

import java.util.Arrays;
import java.util.List;

public enum ProductType {

    SKIN_TONER("skin_toner", "스킨/토너"),
    TONER_PAD("toner_pad", "토너패드"),
    MIST("mist", "미스트"),
    AMPOULE("ampoule", "앰플"),
    SERUM("serum", "세럼"),
    ESSENCE("essence", "에센스"),
    MOISTURE_CREAM("moisture_cream", "수분크림"),
    SOOTHING_CREAM("soothing_cream", "진정크림"),
    MOISTURIZING_CREAM("moisturizing_cream", "보습크림"),
    LOTION_EMULSION("lotion_emulsion", "로션/에멀전"),
    EYE_CREAM("eye_cream", "아이크림"),
    ETC("etc", "기타");

    private final String value;
    private final String displayName;

    ProductType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public static boolean supports(String value) {
        return Arrays.stream(values())
                .anyMatch(productType -> productType.value.equals(value));
    }

    public static List<String> supportedValues() {
        return Arrays.stream(values())
                .map(productType -> productType.value)
                .toList();
    }

    public static String displayNameOf(String value) {
        return Arrays.stream(values())
                .filter(productType -> productType.value.equals(value))
                .findFirst()
                .map(productType -> productType.displayName)
                .orElse(value);
    }
}
