package com.ellasgame.core;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public record VocabularyEntry(String group, String page, NumberForm number, Gender gender, String hebrew, String arabic, String arabicAlias, String arabicAlias2) {
    public static final String NO_ALIAS = "N/A";
    public static final String UNKNOWN_PAGE = "N/A";

    public VocabularyEntry(String group, NumberForm number, String hebrew, String arabic) {
        this(group, UNKNOWN_PAGE, number, Gender.NOT_APPLICABLE, hebrew, arabic, NO_ALIAS, NO_ALIAS);
    }

    public VocabularyEntry(String group, NumberForm number, Gender gender, String hebrew, String arabic) {
        this(group, UNKNOWN_PAGE, number, gender, hebrew, arabic, NO_ALIAS, NO_ALIAS);
    }

    public VocabularyEntry(String group, NumberForm number, Gender gender, String hebrew, String arabic, String arabicAlias) {
        this(group, UNKNOWN_PAGE, number, gender, hebrew, arabic, arabicAlias, NO_ALIAS);
    }

    public VocabularyEntry(String group, String page, NumberForm number, Gender gender, String hebrew, String arabic, String arabicAlias) {
        this(group, page, number, gender, hebrew, arabic, arabicAlias, NO_ALIAS);
    }

    public VocabularyEntry {
        group = requireText(group, "group");
        page = normalizePage(page);
        number = Objects.requireNonNull(number, "number");
        gender = Objects.requireNonNull(gender, "gender");
        hebrew = requireText(hebrew, "hebrew");
        arabic = requireText(arabic, "arabic");
        arabicAlias = normalizeAlias(arabicAlias);
        arabicAlias2 = normalizeAlias(arabicAlias2);
    }

    public boolean hasArabicAlias() {
        return !NO_ALIAS.equals(arabicAlias);
    }

    public boolean hasArabicAlias2() {
        return !NO_ALIAS.equals(arabicAlias2);
    }

    public List<String> arabicAnswers() {
        List<String> answers = new ArrayList<>();
        answers.add(arabic);
        if (hasArabicAlias()) {
            answers.add(arabicAlias);
        }
        if (hasArabicAlias2()) {
            answers.add(arabicAlias2);
        }
        return List.copyOf(answers);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value.trim();
    }

    private static String normalizeAlias(String value) {
        if (value == null || value.isBlank()) {
            return NO_ALIAS;
        }
        String trimmed = value.trim();
        return NO_ALIAS.equalsIgnoreCase(trimmed) ? NO_ALIAS : trimmed;
    }

    private static String normalizePage(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_PAGE;
        }
        return value.trim();
    }

    public enum NumberForm {
        SINGLE("single", "יחיד", "ביחיד"),
        PLURAL("plural", "רבים", "ברבים");

        private final String csvValue;
        private final String visibleHebrew;
        private final String spokenHebrew;

        NumberForm(String csvValue, String visibleHebrew, String spokenHebrew) {
            this.csvValue = csvValue;
            this.visibleHebrew = visibleHebrew;
            this.spokenHebrew = spokenHebrew;
        }

        public String csvValue() {
            return csvValue;
        }

        public String visibleHebrew() {
            return visibleHebrew;
        }

        public String spokenHebrew() {
            return spokenHebrew;
        }

        static NumberForm fromCsv(String value) {
            for (NumberForm form : values()) {
                if (form.csvValue.equalsIgnoreCase(value.trim())) {
                    return form;
                }
            }
            throw new IllegalArgumentException("Unknown number value: " + value);
        }
    }

    public enum Gender {
        NOT_APPLICABLE("N/A", "", ""),
        FEMALE("female", "נקבה", "נקבה"),
        MALE("male", "זכר", "זכר");

        private final String csvValue;
        private final String visibleHebrew;
        private final String spokenHebrew;

        Gender(String csvValue, String visibleHebrew, String spokenHebrew) {
            this.csvValue = csvValue;
            this.visibleHebrew = visibleHebrew;
            this.spokenHebrew = spokenHebrew;
        }

        public String csvValue() {
            return csvValue;
        }

        public boolean isApplicable() {
            return this != NOT_APPLICABLE;
        }

        public String visibleHebrew() {
            return visibleHebrew;
        }

        public String spokenHebrew() {
            return spokenHebrew;
        }

        static Gender fromCsv(String value) {
            for (Gender gender : values()) {
                if (gender.csvValue.equalsIgnoreCase(value.trim())) {
                    return gender;
                }
            }
            throw new IllegalArgumentException("Unknown gender value: " + value);
        }
    }
}
