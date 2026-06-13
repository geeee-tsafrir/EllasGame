package com.ellasgame.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class VocabularyDeckTest {
    @Test
    void returnsEverySelectedEntryBeforeRepeating() {
        VocabularyEntry first = entry("group-1", "1", "one", "واحد");
        VocabularyEntry second = entry("group-1", "1", "two", "اثنان");
        VocabularyEntry third = entry("group-1", "1", "three", "ثلاثة");
        VocabularyDeck deck = new VocabularyDeck(
                new VocabularyDictionary(List.of(first, second, third)),
                new Random(42));

        Set<VocabularyEntry> firstCycle = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            firstCycle.add(deck.next(List.of("group-1"), List.of("1")));
        }

        assertEquals(Set.of(first, second, third), firstCycle);
    }

    @Test
    void avoidsImmediateRepeatWhenDeckRefills() {
        VocabularyEntry first = entry("group-1", "1", "one", "واحد");
        VocabularyEntry second = entry("group-1", "1", "two", "اثنان");
        VocabularyDeck deck = new VocabularyDeck(
                new VocabularyDictionary(List.of(first, second)),
                new MaxRandom());

        VocabularyEntry previous = null;
        for (int index = 0; index < 4; index++) {
            VocabularyEntry next = deck.next(List.of("group-1"), List.of("1"));
            assertNotEquals(previous, next);
            previous = next;
        }
    }

    @Test
    void refillsWhenSelectionChanges() {
        VocabularyEntry first = entry("group-1", "1", "one", "واحد");
        VocabularyEntry second = entry("group-2", "2", "two", "اثنان");
        VocabularyDeck deck = new VocabularyDeck(
                new VocabularyDictionary(List.of(first, second)),
                new Random(42));

        assertEquals(first, deck.next(List.of("group-1"), List.of("1")));
        assertEquals(second, deck.next(List.of("group-2"), List.of("2")));
    }

    private static VocabularyEntry entry(String group, String page, String hebrew, String arabic) {
        return new VocabularyEntry(
                group,
                page,
                VocabularyEntry.NumberForm.SINGLE,
                VocabularyEntry.Gender.NOT_APPLICABLE,
                hebrew,
                arabic,
                VocabularyEntry.NO_ALIAS);
    }

    private static final class MaxRandom extends Random {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }
}
