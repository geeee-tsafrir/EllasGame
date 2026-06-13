package com.ellasgame.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class VocabularyDeck {
    private final VocabularyDictionary vocabularyDictionary;
    private final Random random;
    private final List<VocabularyEntry> remainingEntries = new ArrayList<>();
    private Set<String> selectedGroups = Set.of();
    private Set<String> selectedPages = Set.of();
    private VocabularyEntry lastEntry;

    public VocabularyDeck(VocabularyDictionary vocabularyDictionary, Random random) {
        this.vocabularyDictionary = Objects.requireNonNull(vocabularyDictionary, "vocabularyDictionary");
        this.random = Objects.requireNonNull(random, "random");
    }

    public VocabularyEntry next(Collection<String> selectedGroups, Collection<String> selectedPages) {
        Set<String> normalizedGroups = normalizedSelection(selectedGroups);
        Set<String> normalizedPages = normalizedSelection(selectedPages);
        if (!this.selectedGroups.equals(normalizedGroups) || !this.selectedPages.equals(normalizedPages)) {
            this.selectedGroups = normalizedGroups;
            this.selectedPages = normalizedPages;
            remainingEntries.clear();
        }

        if (remainingEntries.isEmpty()) {
            refill();
        }

        VocabularyEntry nextEntry = remainingEntries.remove(remainingEntries.size() - 1);
        lastEntry = nextEntry;
        return nextEntry;
    }

    private void refill() {
        remainingEntries.addAll(vocabularyDictionary.entriesFor(selectedGroups, selectedPages));
        Collections.shuffle(remainingEntries, random);
        avoidImmediateRepeatAcrossDecks();
    }

    private void avoidImmediateRepeatAcrossDecks() {
        if (lastEntry == null || remainingEntries.size() <= 1) {
            return;
        }

        int nextIndex = remainingEntries.size() - 1;
        if (!lastEntry.equals(remainingEntries.get(nextIndex))) {
            return;
        }

        Collections.swap(remainingEntries, nextIndex, nextIndex - 1);
    }

    private static Set<String> normalizedSelection(Collection<String> selectedValues) {
        if (selectedValues == null || selectedValues.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(selectedValues));
    }
}
