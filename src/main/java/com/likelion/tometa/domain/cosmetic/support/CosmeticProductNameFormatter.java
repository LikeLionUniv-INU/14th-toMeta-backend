package com.likelion.tometa.domain.cosmetic.support;

public final class CosmeticProductNameFormatter {

    private CosmeticProductNameFormatter() {
    }

    public static String format(String brandName, String productName) {
        if (brandName == null || brandName.isBlank()) {
            return productName;
        }

        return brandName.trim() + " " + productName;
    }
}
