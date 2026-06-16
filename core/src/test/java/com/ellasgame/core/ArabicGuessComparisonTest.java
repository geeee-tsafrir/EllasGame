package com.ellasgame.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ArabicGuessComparisonTest {
    @Test
    void acceptsSecondArabicAlias() {
        VocabularyEntry entry = new VocabularyEntry(
                "كَانَ ואחיותיה",
                "46",
                VocabularyEntry.NumberForm.SINGLE,
                VocabularyEntry.Gender.NOT_APPLICABLE,
                "עדיין, עודנו",
                "مَا زَالَ",
                "لَا يَزَالُ",
                "لَمْ يَزَلْ");

        ArabicGuessComparison.GuessComparison result = ArabicGuessComparison.compare(entry, "لَمْ يَزَلْ");

        assertTrue(result.correct());
    }
}
