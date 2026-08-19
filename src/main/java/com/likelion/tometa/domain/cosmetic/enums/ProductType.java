package com.likelion.tometa.domain.cosmetic.enums;

import java.util.Arrays;
import java.util.List;

public enum ProductType {

    SKIN_TONER("skin_toner"),
    TONER_PAD("toner_pad"),
    MIST("mist"),
    AMPOULE("ampoule"),
    SERUM("serum"),
    ESSENCE("essence"),
    MOISTURE_CREAM("moisture_cream"),
    SOOTHING_CREAM("soothing_cream"),
    MOISTURIZING_CREAM("moisturizing_cream"),
    LOTION_EMULSION("lotion_emulsion"),
    EYE_CREAM("eye_cream"),
    ETC("etc");

    private final String value;

    ProductType(String value) {
        this.value = value;
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
}
