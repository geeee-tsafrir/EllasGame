---
name: parse-arabic-hebrew-vocab-pdf
description: Parse the EllasGame Arabic-Hebrew vocabulary PDF page by page into reviewed CSV tables. Use when continuing the אוצר מילים.pdf extraction, creating lang_page_N.csv files, reviewing parsed Hebrew/Arabic vocabulary tables, preserving UTF-8 Arabic diacritics, splitting singular/plural forms, or following the user's established expectations for this vocabulary dictionary workflow.
---

# Parse Arabic-Hebrew Vocab PDF

Use this skill for the EllasGame vocabulary dictionary extraction from `/Users/geeee/Downloads/אוצר מילים.pdf`.

## User Expectations

- Work page by page.
- Start by parsing and showing the table for review.
- Do not write a page CSV until the user approves the reviewed table or explicitly asks to write it.
- Preserve UTF-8 Hebrew, Arabic, Arabic diacritics, and RTL display marks.
- Treat PDF parsing as layout-based, not plain `extract_text()` line parsing.
- Ignore the page header and footer/footnotes unless the user explicitly asks about them.
- Keep the user’s corrections authoritative. If the user corrects one cell, update that cell and show the full table again.
- After every parser or table fix, rerun the page and print the full table, not only the corrected row.
- When asked to write CSV, write under `lang_dict/lang_page_N.csv`.

## Output Schema

CSV/table columns:

```text
group,number,gender,Word hebrew,Word arabic,Word arabic alias
```

`number` values:

```text
single
plural
```

## Parsing Rules

- Each page starts with a bold header like `אוצר מילים - יום N`; discard it.
- Each page is visually divided into right and left sections.
- Each group has an underlined Hebrew group name, for example:
  - `כינויי גוף`
  - `שמות עצם`
  - `מיליות`
  - `שמות תואר`
  - `תוארי פועל`
  - `פעלים`
- Numbered vocabulary items follow this layout:

```text
index Arabic – Hebrew
```

- The numeric index is not data.
- Hebrew notes that continue on the next visual line belong in `Word hebrew`.
- Parenthetical Hebrew explanations are part of `Word hebrew`.
- `(ר)` marks plural on the Hebrew side.
- Arabic `ج` or `(ج)` can mark plural on the Arabic side.
- A singular/plural pair should become two rows with the same Hebrew term and different Arabic terms:

```text
שמות עצם,single,בית,‏بَيْت
שמות עצם,plural,בית,‏بُيُوت
```

- Do not keep `(ר)` inside `Word hebrew`; represent it with `number=plural`.
- If Hebrew has multiple alternatives, keep them in one field, comma-separated as shown in the source, for example `דלת, שער`.
- If the same Hebrew prompt has two valid Arabic answers with the same group, number, and gender, keep one row and put the second answer in `Word arabic alias`; otherwise use `N/A`.

## Quality Rules

- Arabic diacritics matter. Do not invent them when uncertain.
- Do not add grammatical case endings or normalize Arabic articles into textbook forms unless those signs are clearly present in the PDF extraction or confirmed by the user.
- If the extraction misses signs, prefer showing the table for user review rather than silently guessing.
- Preserve user-confirmed corrections in later outputs.
- Use quoted CSV fields for Hebrew text containing commas or quotes.
- Validate written CSVs with Python `csv.DictReader`.

## Known Confirmed Files

Already created in this project:

```text
lang_dict/lang_page_1.csv
lang_dict/lang_page_2.csv
```

## Page 2 Confirmed Corrections

The user corrected these:

- `יהודי` single Arabic is `‏يَهُودِيّ`.
- `אם` Arabic is `‏أُمّ`.
- `סופר` plural Arabic is `‏كُتّاب`.
- `יד` Hebrew must include the full note: `יד (בערבית [כמו בעברית] - מין נקבה)`.

## Recommended Workflow

1. Inspect page words by coordinates with `pdfplumber.extract_words(use_text_flow=False)`.
2. Group words by row using `top` tolerance.
3. Split into right/left page sections by `x`.
4. Detect group headings by underline and Hebrew-only text.
5. Accumulate numbered items, including continuation rows.
6. Convert singular/plural pairs into separate rows.
7. Present a Markdown table to the user.
8. Apply user corrections.
9. When approved, write `lang_dict/lang_page_N.csv`.
10. Validate the CSV with `csv.DictReader`.
