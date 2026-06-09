package com.ellasgame.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArabicGuessComparison {
    private ArabicGuessComparison() {
    }

    public static GuessComparison compare(String expected, String actual) {
        return compareExpected(expected, actual);
    }

    public static GuessComparison compare(VocabularyEntry expectedEntry, String actual) {
        Objects.requireNonNull(expectedEntry, "expectedEntry");
        GuessComparison best = null;
        for (String expected : expectedEntry.arabicAnswers()) {
            GuessComparison comparison = compareExpected(expected, actual);
            if (best == null || isBetter(comparison, best)) {
                best = comparison;
            }
        }
        return Objects.requireNonNull(best, "best");
    }

    private static boolean isBetter(GuessComparison candidate, GuessComparison current) {
        if (candidate.totalErrors() != current.totalErrors()) {
            return candidate.totalErrors() < current.totalErrors();
        }
        if (candidate.baseLetterErrors() != current.baseLetterErrors()) {
            return candidate.baseLetterErrors() < current.baseLetterErrors();
        }
        return candidate.signErrors() < current.signErrors();
    }

    private static GuessComparison compareExpected(String expected, String actual) {
        List<ArabicUnit> expectedUnits = ArabicUnit.parse(expected);
        List<ArabicUnit> actualUnits = ArabicUnit.parse(actual);
        List<AlignmentStep> alignment = alignBaseLetters(expectedUnits, actualUnits);

        List<UserCharacterFeedback> userFeedback = new ArrayList<>();
        int baseLetterErrors = 0;
        int signErrors = 0;

        for (AlignmentStep step : alignment) {
            if (step.expectedIndex >= 0 && step.actualIndex >= 0) {
                ArabicUnit expectedUnit = expectedUnits.get(step.expectedIndex);
                ArabicUnit actualUnit = actualUnits.get(step.actualIndex);
                boolean baseMatches = expectedUnit.baseCodePoint == actualUnit.baseCodePoint;

                if (!baseMatches) {
                    baseLetterErrors++;
                    signErrors += expectedUnit.signs.size();
                    userFeedback.add(new UserCharacterFeedback(
                            actualUnit.baseText(),
                            false,
                            mismatchedBaseSignFeedback(expectedUnit.signs, actualUnit.signs)));
                    continue;
                }

                signErrors += signDifferenceCount(expectedUnit.signs, actualUnit.signs);
                userFeedback.add(new UserCharacterFeedback(
                        actualUnit.baseText(),
                        true,
                        signFeedback(expectedUnit.signs, actualUnit.signs)));
            } else if (step.expectedIndex >= 0) {
                ArabicUnit expectedUnit = expectedUnits.get(step.expectedIndex);
                baseLetterErrors++;
                signErrors += expectedUnit.signs.size();
            } else {
                ArabicUnit actualUnit = actualUnits.get(step.actualIndex);
                baseLetterErrors++;
                userFeedback.add(new UserCharacterFeedback(
                        actualUnit.baseText(),
                        false,
                        actualUnit.signs.stream()
                                .map(sign -> new UserSignFeedback(codePointText(sign), false))
                                .toList()));
            }
        }

        return new GuessComparison(
                expected,
                baseLetterErrors == 0 && signErrors == 0,
                baseLetterErrors,
                signErrors,
                expectedUnits.stream().map(ArabicUnit::text).toList(),
                userFeedback);
    }

    private static List<UserSignFeedback> mismatchedBaseSignFeedback(List<Integer> expectedSigns, List<Integer> actualSigns) {
        if (!actualSigns.isEmpty()) {
            return actualSigns.stream()
                    .map(sign -> new UserSignFeedback(codePointText(sign), false))
                    .toList();
        }
        return expectedSigns.stream()
                .map(sign -> new UserSignFeedback(codePointText(sign), false))
                .toList();
    }

    private static List<UserSignFeedback> signFeedback(List<Integer> expectedSigns, List<Integer> actualSigns) {
        List<UserSignFeedback> feedback = new ArrayList<>();
        boolean[] usedActual = new boolean[actualSigns.size()];
        for (Integer expectedSign : expectedSigns) {
            int matchingIndex = firstUnusedIndexOf(actualSigns, usedActual, expectedSign);
            if (matchingIndex >= 0) {
                usedActual[matchingIndex] = true;
                feedback.add(new UserSignFeedback(codePointText(actualSigns.get(matchingIndex)), true));
            } else {
                int replacementIndex = firstUnusedIndex(usedActual);
                if (replacementIndex >= 0) {
                    usedActual[replacementIndex] = true;
                    feedback.add(new UserSignFeedback(codePointText(actualSigns.get(replacementIndex)), false));
                } else {
                    feedback.add(new UserSignFeedback(codePointText(expectedSign), false));
                }
            }
        }
        for (int index = 0; index < actualSigns.size(); index++) {
            if (!usedActual[index]) {
                feedback.add(new UserSignFeedback(codePointText(actualSigns.get(index)), false));
            }
        }
        return feedback;
    }

    private static int firstUnusedIndex(boolean[] used) {
        for (int index = 0; index < used.length; index++) {
            if (!used[index]) {
                return index;
            }
        }
        return -1;
    }

    private static String codePointText(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static int signDifferenceCount(List<Integer> expectedSigns, List<Integer> actualSigns) {
        int missingSigns = 0;
        int extraSigns = 0;
        boolean[] usedActual = new boolean[actualSigns.size()];
        for (Integer expectedSign : expectedSigns) {
            int matchingIndex = firstUnusedIndexOf(actualSigns, usedActual, expectedSign);
            if (matchingIndex >= 0) {
                usedActual[matchingIndex] = true;
            } else {
                missingSigns++;
            }
        }
        for (boolean used : usedActual) {
            if (!used) {
                extraSigns++;
            }
        }
        return Math.max(missingSigns, extraSigns);
    }

    private static int firstUnusedIndexOf(List<Integer> signs, boolean[] used, Integer sign) {
        for (int index = 0; index < signs.size(); index++) {
            if (!used[index] && Objects.equals(signs.get(index), sign)) {
                return index;
            }
        }
        return -1;
    }

    private static List<AlignmentStep> alignBaseLetters(List<ArabicUnit> expected, List<ArabicUnit> actual) {
        int[][] costs = new int[expected.size() + 1][actual.size() + 1];
        for (int expectedIndex = 0; expectedIndex <= expected.size(); expectedIndex++) {
            costs[expectedIndex][0] = expectedIndex;
        }
        for (int actualIndex = 0; actualIndex <= actual.size(); actualIndex++) {
            costs[0][actualIndex] = actualIndex;
        }

        for (int expectedIndex = 1; expectedIndex <= expected.size(); expectedIndex++) {
            for (int actualIndex = 1; actualIndex <= actual.size(); actualIndex++) {
                int substitutionCost = expected.get(expectedIndex - 1).baseCodePoint == actual.get(actualIndex - 1).baseCodePoint ? 0 : 1;
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
                int substitutionCost = expected.get(expectedIndex - 1).baseCodePoint == actual.get(actualIndex - 1).baseCodePoint ? 0 : 1;
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

    private record ArabicUnit(int baseCodePoint, String text, List<Integer> signs) {
        String baseText() {
            return codePointText(baseCodePoint);
        }

        static List<ArabicUnit> parse(String rawText) {
            String normalized = Normalizer.normalize(rawText == null ? "" : rawText.trim(), Normalizer.Form.NFC);
            boolean previousWasWhitespace = false;
            List<ArabicUnitBuilder> builders = new ArrayList<>();
            ArabicUnitBuilder current = null;

            for (int offset = 0; offset < normalized.length(); ) {
                int codePoint = normalized.codePointAt(offset);
                offset += Character.charCount(codePoint);

                if (Character.isWhitespace(codePoint)) {
                    if (!previousWasWhitespace && !builders.isEmpty()) {
                        current = new ArabicUnitBuilder(' ');
                        builders.add(current);
                    }
                    previousWasWhitespace = true;
                    continue;
                }
                previousWasWhitespace = false;

                if (codePoint == 0x0640) {
                    continue;
                }
                if (isArabicSign(codePoint)) {
                    if (current != null) {
                        current.addSign(codePoint);
                    }
                    continue;
                }

                current = new ArabicUnitBuilder(codePoint);
                builders.add(current);
            }

            List<ArabicUnit> units = new ArrayList<>(builders.size());
            for (ArabicUnitBuilder builder : builders) {
                units.add(builder.build());
            }
            return units;
        }

        private static boolean isArabicSign(int codePoint) {
            return (codePoint >= 0x0610 && codePoint <= 0x061A)
                    || (codePoint >= 0x064B && codePoint <= 0x065F)
                    || codePoint == 0x0670
                    || (codePoint >= 0x06D6 && codePoint <= 0x06ED);
        }
    }

    private static final class ArabicUnitBuilder {
        private final int baseCodePoint;
        private final StringBuilder text = new StringBuilder();
        private final List<Integer> signs = new ArrayList<>();

        ArabicUnitBuilder(int baseCodePoint) {
            this.baseCodePoint = baseCodePoint;
            text.appendCodePoint(baseCodePoint);
        }

        void addSign(int codePoint) {
            signs.add(codePoint);
            text.appendCodePoint(codePoint);
        }

        ArabicUnit build() {
            return new ArabicUnit(baseCodePoint, text.toString(), List.copyOf(signs));
        }
    }

    public record GuessComparison(
            String expectedText,
            boolean correct,
            int baseLetterErrors,
            int signErrors,
            List<String> expectedCharacters,
            List<UserCharacterFeedback> userCharacters) {
        public int totalErrors() {
            return baseLetterErrors + signErrors;
        }

        public String resultText() {
            if (correct) {
                return "Result: Success";
            }
            return "Result: " + totalErrors() + " errors";
        }
    }

    public record UserCharacterFeedback(String baseText, boolean baseCorrect, List<UserSignFeedback> signs) {
        public String text() {
            StringBuilder text = new StringBuilder(baseText);
            for (UserSignFeedback sign : signs) {
                text.append(sign.text());
            }
            return text.toString();
        }
    }

    public record UserSignFeedback(String text, boolean correct) {
    }
}
