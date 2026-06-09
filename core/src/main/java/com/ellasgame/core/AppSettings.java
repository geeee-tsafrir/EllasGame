package com.ellasgame.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record AppSettings(
        String camera,
        List<String> vocabularyGroups,
        List<String> vocabularyPages,
        TranslationDirection translationDirection) implements Serializable {
    public static final String DEFAULT_CAMERA = "Default camera";

    public AppSettings {
        if (camera == null || camera.isBlank()) {
            camera = DEFAULT_CAMERA;
        }
        vocabularyGroups = vocabularyGroups == null ? List.of() : List.copyOf(cleanGroups(vocabularyGroups));
        vocabularyPages = vocabularyPages == null ? List.of() : List.copyOf(cleanGroups(vocabularyPages));
        translationDirection = translationDirection == null ? TranslationDirection.HEBREW_TO_ARABIC : translationDirection;
    }

    public static AppSettings defaults() {
        return new AppSettings(DEFAULT_CAMERA, List.of(), List.of(), TranslationDirection.HEBREW_TO_ARABIC);
    }

    public AppSettings withCamera(String newCamera) {
        return new AppSettings(newCamera, vocabularyGroups, vocabularyPages, translationDirection);
    }

    public AppSettings withVocabularyGroups(List<String> newVocabularyGroups) {
        return new AppSettings(camera, newVocabularyGroups, vocabularyPages, translationDirection);
    }

    public AppSettings withVocabularyPages(List<String> newVocabularyPages) {
        return new AppSettings(camera, vocabularyGroups, newVocabularyPages, translationDirection);
    }

    public AppSettings withTranslationDirection(TranslationDirection newTranslationDirection) {
        return new AppSettings(camera, vocabularyGroups, vocabularyPages, newTranslationDirection);
    }

    public boolean usesAllVocabularyGroups() {
        return vocabularyGroups.isEmpty();
    }

    public boolean usesAllVocabularyPages() {
        return vocabularyPages.isEmpty();
    }

    private static List<String> cleanGroups(List<String> groups) {
        List<String> cleanGroups = new ArrayList<>();
        for (String group : groups) {
            if (group != null && !group.isBlank() && !cleanGroups.contains(group.trim())) {
                cleanGroups.add(group.trim());
            }
        }
        return cleanGroups;
    }
}
