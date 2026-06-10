package com.ellasgame.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class HebrewGuessComparison {
    private HebrewGuessComparison() {
    }

    public static GuessComparison compare(String expected, String actual) {
        String expectedText = sanitizeAnswerText(expected);
        String actualText = sanitizeAnswerText(actual);
        List<Integer> expectedCharacters = codePoints(expectedText);
        List<Integer> actualCharacters = codePoints(actualText);
        List<AlignmentStep> alignment = alignCharacters(expectedCharacters, actualCharacters);

        List<UserCharacterFeedback> userFeedback = new ArrayList<>();
        int characterErrors = 0;
        for (AlignmentStep step : alignment) {
            if (step.expectedIndex >= 0 && step.actualIndex >= 0) {
                int expectedCodePoint = expectedCharacters.get(step.expectedIndex);
                int actualCodePoint = actualCharacters.get(step.actualIndex);
                boolean correct = expectedCodePoint == actualCodePoint;
                if (!correct) {
                    characterErrors++;
                }
                userFeedback.add(new UserCharacterFeedback(codePointText(actualCodePoint), correct));
            } else if (step.expectedIndex >= 0) {
                characterErrors++;
            } else {
                int actualCodePoint = actualCharacters.get(step.actualIndex);
                characterErrors++;
                userFeedback.add(new UserCharacterFeedback(codePointText(actualCodePoint), false));
            }
        }

        return new GuessComparison(
                expectedText,
                characterErrors == 0,
                characterErrors,
                expectedCharacters.stream().map(HebrewGuessComparison::codePointText).toList(),
                userFeedback);
    }

    public static String sanitizeAnswerText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\u200e", "")
                .replace("\u200f", "")
                .replace("\u202a", "")
                .replace("\u202b", "")
                .replace("\u202c", "")
                .replace("\u202d", "")
                .replace("\u202e", "")
                .replace("…", "...")
                .replaceAll("[\\u0591-\\u05C7]", "");
        normalized = normalized.replaceAll("\\s*\\.\\.\\.\\s*", " ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    private static List<Integer> codePoints(String text) {
        List<Integer> codePoints = new ArrayList<>();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            codePoints.add(codePoint);
        }
        return codePoints;
    }

    private static String codePointText(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static List<AlignmentStep> alignCharacters(List<Integer> expected, List<Integer> actual) {
        int[][] costs = new int[expected.size() + 1][actual.size() + 1];
        for (int expectedIndex = 0; expectedIndex <= expected.size(); expectedIndex++) {
            costs[expectedIndex][0] = expectedIndex;
        }
        for (int actualIndex = 0; actualIndex <= actual.size(); actualIndex++) {
            costs[0][actualIndex] = actualIndex;
        }

        for (int expectedIndex = 1; expectedIndex <= expected.size(); expectedIndex++) {
            for (int actualIndex = 1; actualIndex <= actual.size(); actualIndex++) {
                int substitutionCost = Objects.equals(expected.get(expectedIndex - 1), actual.get(actualIndex - 1)) ? 0 : 1;
                costs[expectedIndex][actualIndex] = Math.min(
                        costs[expectedIndex - 1][actualIndex - 1] + substitutionCost,
                        Math.min(
                                costs[expectedIndex - 1][actualIndex] + 1,
                                costs[expectedIndex][actualIndex - 1] + 1));
            }
        }

        List<AlignmentStep> reversed = new ArrayList<>();
        int expectedIndex = expected.size();
        int actualIndex = actual.size();
        while (expectedIndex > 0 || actualIndex > 0) {
            if (expectedIndex > 0 && actualIndex > 0) {
                int substitutionCost = Objects.equals(expected.get(expectedIndex - 1), actual.get(actualIndex - 1)) ? 0 : 1;
                if (costs[expectedIndex][actualIndex] == costs[expectedIndex - 1][actualIndex - 1] + substitutionCost) {
                    reversed.add(new AlignmentStep(expectedIndex - 1, actualIndex - 1));
                    expectedIndex--;
                    actualIndex--;
                    continue;
                }
            }
            if (expectedIndex > 0 && costs[expectedIndex][actualIndex] == costs[expectedIndex - 1][actualIndex] + 1) {
                reversed.add(new AlignmentStep(expectedIndex - 1, -1));
                expectedIndex--;
            } else {
                reversed.add(new AlignmentStep(-1, actualIndex - 1));
                actualIndex--;
            }
        }

        List<AlignmentStep> alignment = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            alignment.add(reversed.get(index));
        }
        return alignment;
    }

    private record AlignmentStep(int expectedIndex, int actualIndex) {
    }

    public record GuessComparison(
            String expectedText,
            boolean correct,
            int characterErrors,
            List<String> expectedCharacters,
            List<UserCharacterFeedback> userCharacters) {
        public String resultText() {
            if (correct) {
                return "Result: Success";
            }
            return "Result: " + characterErrors + " errors";
        }
    }

    public record UserCharacterFeedback(String text, boolean correct) {
    }
}
