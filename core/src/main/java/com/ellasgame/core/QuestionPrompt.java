package com.ellasgame.core;

public final class QuestionPrompt {
    private QuestionPrompt() {
    }

    public static String prefix() {
        return "תתרגם בבקשה את המילה";
    }

    public static String spoken(String word) {
        return prefix() + " " + word;
    }

    public static String spoken(String word, VocabularyEntry.NumberForm number) {
        return prefix() + " " + word + " " + number.spokenHebrew();
    }

    public static String spoken(VocabularyEntry entry) {
        String prompt = spoken(entry.hebrew(), entry.number());
        if (entry.gender().isApplicable()) {
            return prompt + " " + entry.gender().spokenHebrew();
        }
        return prompt;
    }
}
