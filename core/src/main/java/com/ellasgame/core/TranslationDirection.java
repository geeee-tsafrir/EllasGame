package com.ellasgame.core;

public enum TranslationDirection {
    HEBREW_TO_ARABIC("hebrew_to_arabic", "Hebrew → Arabic"),
    ARABIC_TO_HEBREW("arabic_to_hebrew", "Arabic → Hebrew"),
    RANDOM_BOTH("random_both", "Random");

    private final String storageValue;
    private final String displayName;

    TranslationDirection(String storageValue, String displayName) {
        this.storageValue = storageValue;
        this.displayName = displayName;
    }

    public String storageValue() {
        return storageValue;
    }

    public String displayName() {
        return displayName;
    }

    public boolean asksHebrew() {
        return this == HEBREW_TO_ARABIC;
    }

    public static TranslationDirection fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return HEBREW_TO_ARABIC;
        }
        for (TranslationDirection direction : values()) {
            if (direction.storageValue.equalsIgnoreCase(value.trim())) {
                return direction;
            }
        }
        return HEBREW_TO_ARABIC;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
