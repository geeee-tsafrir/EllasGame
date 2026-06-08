package com.ellasgame.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SessionStatistics {
    private final List<Attempt> attempts = new ArrayList<>();

    public void clear() {
        attempts.clear();
    }

    public void record(String promptWord, String expectedWord, ArabicGuessComparison.GuessComparison comparison) {
        attempts.add(new Attempt(
                promptWord,
                expectedWord,
                comparison.baseLetterErrors(),
                comparison.signErrors()));
    }

    public Summary summary() {
        int perfectScores = 0;
        int wordsWithMistakes = 0;
        int totalErrors = 0;
        int characterErrors = 0;
        int signErrors = 0;
        Map<String, WordErrorSummary> wordSummaries = new LinkedHashMap<>();

        for (Attempt attempt : attempts) {
            int attemptErrors = attempt.totalErrors();
            if (attemptErrors == 0) {
                perfectScores++;
            } else {
                wordsWithMistakes++;
            }
            totalErrors += attemptErrors;
            characterErrors += attempt.characterErrors();
            signErrors += attempt.signErrors();
            String key = attempt.promptWord() + "\n" + attempt.expectedWord();
            wordSummaries.compute(
                    key,
                    (ignored, existing) -> existing == null
                            ? new WordErrorSummary(attempt.promptWord(), attempt.expectedWord(), 1, attemptErrors)
                            : existing.addAttempt(attemptErrors));
        }

        List<WordErrorSummary> wordsWithMostErrors = wordSummaries.values().stream()
                .filter(summary -> summary.totalErrors() > 0)
                .sorted(Comparator.comparingInt(WordErrorSummary::totalErrors).reversed()
                        .thenComparing(WordErrorSummary::promptWord))
                .limit(5)
                .toList();

        return new Summary(
                attempts.size(),
                perfectScores,
                wordsWithMistakes,
                attempts.isEmpty() ? 0.0 : (double) totalErrors / attempts.size(),
                characterErrors,
                signErrors,
                wordsWithMostErrors);
    }

    public record Attempt(String promptWord, String expectedWord, int characterErrors, int signErrors) {
        public int totalErrors() {
            return characterErrors + signErrors;
        }
    }

    public record WordErrorSummary(String promptWord, String expectedWord, int attempts, int totalErrors) {
        WordErrorSummary addAttempt(int errors) {
            return new WordErrorSummary(promptWord, expectedWord, attempts + 1, totalErrors + errors);
        }
    }

    public record Summary(
            int wordCount,
            int perfectScores,
            int wordsWithMistakes,
            double averageMistakeCount,
            int characterErrors,
            int signErrors,
            List<WordErrorSummary> wordsWithMostErrors) {
        public String leadingErrorType() {
            if (characterErrors == 0 && signErrors == 0) {
                return "None";
            }
            if (characterErrors > signErrors) {
                return "Characters";
            }
            if (signErrors > characterErrors) {
                return "Signs";
            }
            return "Tie";
        }
    }
}
