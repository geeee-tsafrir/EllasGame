package com.ellasgame.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VocabularyDictionaryTest {
    @Test
    void defaultDictionaryUsesVocabularyDaysInsteadOfSourcePages() {
        Result<VocabularyDictionary, String> loaded = VocabularyDictionary.loadDefault();
        assertTrue(loaded instanceof Result.Success<VocabularyDictionary, String>);
        VocabularyDictionary dictionary = ((Result.Success<VocabularyDictionary, String>) loaded).value();

        assertTrue(dictionary.pages().contains("81"));
        assertTrue(dictionary.pages().contains("67"));
        assertTrue(dictionary.pages().contains("46"));
        assertFalse(dictionary.pages().contains("30"));
        assertFalse(dictionary.pages().contains("38"));
        assertEquals(45, dictionary.entryCountForPage("67"));
    }
}
