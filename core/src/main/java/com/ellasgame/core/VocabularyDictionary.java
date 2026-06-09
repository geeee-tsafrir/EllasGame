package com.ellasgame.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VocabularyDictionary {
    private static final String INDEX_RESOURCE = "/lang_dict/index.txt";
    private static final Pattern PAGE_FILE_PATTERN = Pattern.compile("lang_page_(\\d+)\\.csv");

    private final List<VocabularyEntry> entries;
    private final List<String> groups;
    private final List<String> pages;

    public VocabularyDictionary(List<VocabularyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty.");
        }
        this.entries = List.copyOf(entries);
        LinkedHashSet<String> discoveredGroups = new LinkedHashSet<>();
        LinkedHashSet<String> discoveredPages = new LinkedHashSet<>();
        for (VocabularyEntry entry : entries) {
            discoveredGroups.add(entry.group());
            if (!VocabularyEntry.UNKNOWN_PAGE.equals(entry.page())) {
                discoveredPages.add(entry.page());
            }
        }
        this.groups = List.copyOf(discoveredGroups);
        this.pages = discoveredPages.stream()
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
    }

    public static Result<VocabularyDictionary, String> loadDefault() {
        try (InputStream indexStream = VocabularyDictionary.class.getResourceAsStream(INDEX_RESOURCE)) {
            if (indexStream == null) {
                return Result.failure("Missing vocabulary dictionary index resource: " + INDEX_RESOURCE);
            }

            List<VocabularyEntry> loadedEntries = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
                String fileName;
                while ((fileName = reader.readLine()) != null) {
                    fileName = fileName.trim();
                    if (fileName.isEmpty()) {
                        continue;
                    }
                    Result<List<VocabularyEntry>, String> fileEntries = loadCsvResource("/lang_dict/" + fileName, pageFromFileName(fileName));
                    if (fileEntries instanceof Result.Failure<List<VocabularyEntry>, String> failure) {
                        return Result.failure(failure.error());
                    }
                    loadedEntries.addAll(((Result.Success<List<VocabularyEntry>, String>) fileEntries).value());
                }
            }

            if (loadedEntries.isEmpty()) {
                return Result.failure("Vocabulary dictionary contains no entries.");
            }
            return Result.success(new VocabularyDictionary(loadedEntries));
        } catch (IOException exception) {
            return Result.failure("Could not read vocabulary dictionary index.");
        }
    }

    private static Result<List<VocabularyEntry>, String> loadCsvResource(String resourcePath, String page) {
        try (InputStream stream = VocabularyDictionary.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Result.failure("Missing vocabulary dictionary resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return Result.success(parseCsv(reader, resourcePath, page));
            }
        } catch (IOException exception) {
            return Result.failure("Could not read vocabulary dictionary resource: " + resourcePath);
        } catch (IllegalArgumentException exception) {
            return Result.failure(exception.getMessage());
        }
    }

    private static List<VocabularyEntry> parseCsv(BufferedReader reader, String sourceName, String page) throws IOException {
        List<VocabularyEntry> parsedEntries = new ArrayList<>();
        String header = reader.readLine();
        if (header == null) {
            return parsedEntries;
        }

        int lineNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            List<String> columns = parseCsvLine(line);
            if (columns.size() != 4 && columns.size() != 5 && columns.size() != 6) {
                throw new IllegalArgumentException(sourceName + ":" + lineNumber + " expected 4, 5, or 6 CSV columns.");
            }
            if (columns.size() == 6) {
                parsedEntries.add(new VocabularyEntry(
                        stripBom(columns.get(0)),
                        page,
                        VocabularyEntry.NumberForm.fromCsv(columns.get(1)),
                        VocabularyEntry.Gender.fromCsv(columns.get(2)),
                        columns.get(3),
                        stripDirectionControls(columns.get(4)),
                        stripDirectionControls(columns.get(5))));
                continue;
            }
            if (columns.size() == 5) {
                parsedEntries.add(new VocabularyEntry(
                        stripBom(columns.get(0)),
                        page,
                        VocabularyEntry.NumberForm.fromCsv(columns.get(1)),
                        VocabularyEntry.Gender.fromCsv(columns.get(2)),
                        columns.get(3),
                        stripDirectionControls(columns.get(4)),
                        VocabularyEntry.NO_ALIAS));
                continue;
            }
            parsedEntries.add(new VocabularyEntry(
                    stripBom(columns.get(0)),
                    page,
                    VocabularyEntry.NumberForm.fromCsv(columns.get(1)),
                    VocabularyEntry.Gender.NOT_APPLICABLE,
                    columns.get(2),
                    stripDirectionControls(columns.get(3)),
                    VocabularyEntry.NO_ALIAS));
        }
        return parsedEntries;
    }

    private static String pageFromFileName(String fileName) {
        Matcher matcher = PAGE_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return VocabularyEntry.UNKNOWN_PAGE;
        }
        return matcher.group(1);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        current.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    private static String stripBom(String value) {
        return value.replace("\ufeff", "").trim();
    }

    private static String stripDirectionControls(String value) {
        return value
                .replace("\u200e", "")
                .replace("\u200f", "")
                .trim();
    }

    public List<VocabularyEntry> entries() {
        return entries;
    }

    public List<String> groups() {
        return groups;
    }

    public List<String> pages() {
        return pages;
    }

    public VocabularyEntry randomEntry(Random random, Collection<String> selectedGroups) {
        return randomEntry(random, selectedGroups, List.of());
    }

    public VocabularyEntry randomEntry(Random random, Collection<String> selectedGroups, Collection<String> selectedPages) {
        Objects.requireNonNull(random, "random");
        List<VocabularyEntry> candidates = entriesFor(selectedGroups, selectedPages);
        return candidates.get(random.nextInt(candidates.size()));
    }

    public List<VocabularyEntry> entriesForGroups(Collection<String> selectedGroups) {
        return entriesFor(selectedGroups, List.of());
    }

    public List<VocabularyEntry> entriesFor(Collection<String> selectedGroups, Collection<String> selectedPages) {
        if (selectedGroups == null || selectedGroups.isEmpty()) {
            return entriesForPages(entries, selectedPages);
        }

        Set<String> selected = new LinkedHashSet<>(selectedGroups);
        List<VocabularyEntry> candidates = entries.stream()
                .filter(entry -> selected.contains(entry.group()))
                .toList();
        if (candidates.isEmpty()) {
            return entriesForPages(entries, selectedPages);
        }
        List<VocabularyEntry> pageCandidates = entriesForPages(candidates, selectedPages);
        return pageCandidates.isEmpty() ? candidates : pageCandidates;
    }

    private List<VocabularyEntry> entriesForPages(List<VocabularyEntry> candidates, Collection<String> selectedPages) {
        if (selectedPages == null || selectedPages.isEmpty()) {
            return candidates;
        }

        Set<String> selected = new LinkedHashSet<>(selectedPages);
        List<VocabularyEntry> pageCandidates = candidates.stream()
                .filter(entry -> selected.contains(entry.page()))
                .toList();
        return pageCandidates.isEmpty() ? candidates : pageCandidates;
    }
}
