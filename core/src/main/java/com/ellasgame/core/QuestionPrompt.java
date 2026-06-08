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
}
