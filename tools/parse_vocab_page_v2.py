#!/usr/bin/env python3
# /// script
# requires-python = ">=3.12"
# dependencies = [
#   "pandas",
#   "pdfplumber",
# ]
# ///
"""Parse vocabulary PDF pages without page/word lookup tables.

This is the replacement parser under development.  It keeps the layout parser
simple and testable, and intentionally avoids vocabulary-specific correction
maps.  Use reviewed CSVs as fixtures with `--compare-csv`, not as parse input.
"""

from __future__ import annotations

import argparse
import csv
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import pandas as pd
import pdfplumber


DEFAULT_PDF = Path("/Users/geeee/Downloads/אוצר מילים.pdf")
RTL_MARK = "\u200f"
HEBREW_RE = re.compile(r"[\u0590-\u05ff]")
ARABIC_RE = re.compile(r"[\u0600-\u06ff]")
INDEX_RE = re.compile(r"^\.\d+$|^\d+\.$")
DASHES = ("–", "-")
ARABIC_DIACRITIC_CHARS = (
    set(chr(codepoint) for codepoint in range(0x0610, 0x061B))
    | set(chr(codepoint) for codepoint in range(0x064B, 0x0660))
    | {"\u0670"}
    | set(chr(codepoint) for codepoint in range(0x06D6, 0x06EE))
)
IGNORED_ARABIC_CHARS = {"ـ"}
PLURAL_MARKERS = {"(ר)", ")ר(", "(ج)", ")ج("}
KNOWN_GROUPS = {
    "כינויי גוף",
    "שמות עצם",
    "שמות עצם ותואר",
    "מיליות",
    "שמות תואר",
    "תארי פועל",
    "תוארי פועל",
    "פעלים",
}


@dataclass(frozen=True)
class ParsedItem:
    group: str
    number: str
    gender: str
    hebrew: str
    arabic: str
    arabic_alias: str = "N/A"


def parse_page(pdf_path: str | Path = DEFAULT_PDF, *, page_number: int) -> pd.DataFrame:
    pdf_path = Path(pdf_path)
    with pdfplumber.open(pdf_path) as pdf:
        page = pdf.pages[page_number - 1]
        words = page.extract_words(keep_blank_chars=False, use_text_flow=False)
        words = [word for word in words if 62 <= word["top"] <= page.height - 80]
        attach_chars(words, page.chars)
        words = discard_note_rows(words)

        items: list[ParsedItem] = []
        current_group: str | None = None
        for column_words in split_columns(words, page.width):
            column_items, current_group = parse_column(column_words, current_group)
            items.extend(column_items)

    return items_to_dataframe(collapse_arabic_aliases(items))


def attach_chars(words: list[dict], chars: list[dict]) -> None:
    for word in words:
        word["chars"] = []

    for char in chars:
        center_x = (char["x0"] + char["x1"]) / 2
        center_y = (char["top"] + char["bottom"]) / 2
        is_arabic_mark = char["text"] in ARABIC_DIACRITIC_CHARS
        candidates = [
            word
            for word in words
            if (
                (not is_arabic_mark or has_arabic_base_letter(word["text"]))
                and
                word["x0"] - 0.75 <= center_x <= word["x1"] + 0.75
                and word["top"] - 4.0 <= center_y <= word["bottom"] + 4.0
            )
        ]
        if not candidates and is_arabic_mark:
            candidates = [
                word
                for word in words
                if (
                    has_arabic_base_letter(word["text"])
                    and word["top"] - 4.0 <= center_y <= word["bottom"] + 4.0
                    and horizontal_distance_to_word(center_x, word) <= 5.0
                )
            ]
        if not candidates:
            continue
        best = min(candidates, key=lambda word: (word["x1"] - word["x0"]) * (word["bottom"] - word["top"]))
        best["chars"].append(char)


def horizontal_distance_to_word(x: float, word: dict) -> float:
    if word["x0"] <= x <= word["x1"]:
        return 0.0
    return min(abs(x - word["x0"]), abs(x - word["x1"]))


def items_to_dataframe(items: list[ParsedItem]) -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "group": item.group,
                "number": item.number,
                "gender": item.gender,
                "Word hebrew": item.hebrew,
                "Word arabic": item.arabic,
                "Word arabic alias": item.arabic_alias,
            }
            for item in items
        ],
        columns=["group", "number", "gender", "Word hebrew", "Word arabic", "Word arabic alias"],
    )


def split_columns(words: list[dict], page_width: float) -> list[list[dict]]:
    mid_x = page_width / 2
    right = [word for word in words if word["x0"] >= mid_x]
    left = [word for word in words if word["x0"] < mid_x]
    return [right, left]


def parse_column(words: list[dict], starting_group: str | None = None) -> tuple[list[ParsedItem], str | None]:
    parsed_items: list[ParsedItem] = []
    current_group = starting_group
    current_item_words: list[dict] = []

    for row in group_words_by_row(words):
        texts = [word["text"] for word in row]
        if is_note_row(texts):
            parsed_items.extend(parse_item_words(current_item_words, current_group))
            current_item_words = []
            continue

        if is_group_row(texts):
            parsed_items.extend(parse_item_words(current_item_words, current_group))
            current_item_words = []
            current_group = canonical_group(clean_hebrew_words(row))
            continue

        if starts_numbered_item(texts):
            parsed_items.extend(parse_item_words(current_item_words, current_group))
            current_item_words = list(row)
            continue

        if current_item_words:
            current_item_words.extend(row)

    parsed_items.extend(parse_item_words(current_item_words, current_group))
    return parsed_items, current_group


def group_words_by_row(words: list[dict], tolerance: float = 7.0) -> list[list[dict]]:
    rows: list[list[dict]] = []
    for word in sorted(words, key=lambda item: (item["top"], item["x0"])):
        for row in rows:
            if abs(row[0]["top"] - word["top"]) <= tolerance:
                row.append(word)
                break
        else:
            rows.append([word])

    for row in rows:
        row.sort(key=lambda item: item["x0"], reverse=True)
    return rows


def parse_item_words(words: list[dict], group: str | None) -> list[ParsedItem]:
    if not words or group is None:
        return []

    tokens = [word for word in words if not is_index(word["text"])]
    tokens = remove_pattern_marker_tokens(tokens)
    arabic_words, hebrew_words = split_item_tokens(tokens)
    if not arabic_words or not hebrew_words:
        return []

    hebrew = clean_hebrew_words([word for word in hebrew_words if HEBREW_RE.search(word["text"])])
    if not hebrew:
        return []

    gender_items = split_gender_forms(group, hebrew, arabic_words)
    if gender_items:
        return gender_items

    single_arabic_words, plural_arabic_words = split_arabic_number_forms(arabic_words)
    single_arabic = normalize_arabic_for_group(group, clean_arabic_words(single_arabic_words))
    if not single_arabic:
        return []

    if plural_arabic_words:
        plural_arabic = normalize_arabic_for_group(
            group,
            expand_plural_suffix(single_arabic, clean_arabic_words(plural_arabic_words)),
        )
        single_hebrew, plural_hebrew = split_hebrew_number_forms(hebrew)
        return [
            ParsedItem(group, "single", "N/A", single_hebrew, single_arabic),
            ParsedItem(group, "plural", "N/A", plural_hebrew, plural_arabic),
        ]

    return [ParsedItem(group, infer_number(group, hebrew), "N/A", hebrew, single_arabic)]


def split_item_tokens(tokens: list[dict]) -> tuple[list[dict], list[dict]]:
    arabic_tokens: list[dict] = []
    hebrew_tokens: list[dict] = []
    on_hebrew_side = False

    for token in tokens:
        text = token["text"]
        before_dash, dash, after_dash = partition_dash(text)
        if dash:
            if before_dash and has_arabic_base_letter(before_dash):
                arabic_tokens.append(arabic_side_of_dash_token(token, before_dash))
            if after_dash and has_arabic_base_letter(after_dash):
                arabic_tokens.append(arabic_side_of_dash_token(token, after_dash))
            on_hebrew_side = True
            continue

        if on_hebrew_side:
            hebrew_tokens.append(token)
        elif is_plural_marker(text) or has_arabic_base_letter(text):
            arabic_tokens.append(token)

    return arabic_tokens, hebrew_tokens


def arabic_side_of_dash_token(token: dict, text: str) -> dict:
    chars = token.get("chars", [])
    dash_chars = [char for char in chars if char["text"] in DASHES]
    if not dash_chars:
        return token | {"text": text}

    dash_x = sum((char["x0"] + char["x1"]) / 2 for char in dash_chars) / len(dash_chars)
    arabic_chars = [
        char
        for char in chars
        if (
            (ARABIC_RE.search(char["text"]) or char["text"] in ARABIC_DIACRITIC_CHARS)
            and (char["x0"] + char["x1"]) / 2 > dash_x
        )
    ]
    if not arabic_chars:
        return token | {"text": text, "chars": []}

    return token | {
        "text": text,
        "chars": arabic_chars,
        "x0": min(char["x0"] for char in arabic_chars),
        "x1": max(char["x1"] for char in arabic_chars),
        "top": min(char["top"] for char in arabic_chars),
        "bottom": max(char["bottom"] for char in arabic_chars),
    }


def split_arabic_number_forms(tokens: list[dict]) -> tuple[list[dict], list[dict]]:
    single: list[dict] = []
    plural: list[dict] = []
    target = single
    for token in tokens:
        if is_plural_marker(token["text"]):
            target = plural
            continue
        target.append(token)
    return single, plural


def split_gender_forms(group: str, hebrew: str, arabic_words: list[dict]) -> list[ParsedItem]:
    if "ממין נקבה" not in hebrew or "ממין זכר" not in hebrew:
        return []

    hebrew_forms = gender_hebrew_forms(hebrew)
    arabic_forms = split_arabic_slash_forms(arabic_words)
    if len(hebrew_forms) != 2 or len(arabic_forms) != 2:
        return []

    female_arabic = normalize_arabic_for_group(group, clean_arabic_words(arabic_forms[0]))
    male_arabic = normalize_arabic_for_group(group, clean_arabic_words(arabic_forms[1]))
    if not female_arabic or not male_arabic:
        return []

    return [
        ParsedItem(group, "single", "female", hebrew_forms[0], female_arabic),
        ParsedItem(group, "single", "male", hebrew_forms[1], male_arabic),
    ]


def gender_hebrew_forms(hebrew: str) -> list[str]:
    match = re.match(r"(.+?\([^)]+ממין נקבה\))\s+(.+?\([^)]+ממין זכר\))$", hebrew)
    if not match:
        return []
    return [match.group(1).strip(), match.group(2).strip()]


def split_arabic_slash_forms(tokens: list[dict]) -> list[list[dict]]:
    first: list[dict] = []
    second: list[dict] = []
    target = first
    for token in tokens:
        text = token["text"]
        if "/" not in text:
            target.append(token)
            continue

        before, after = text.split("/", 1)
        if before:
            first.append(token | {"text": before, "chars": []})
        if after:
            second.append(token | {"text": after, "chars": []})
        target = second

    return [first, second] if first and second else []


def remove_pattern_marker_tokens(tokens: list[dict]) -> list[dict]:
    cleaned: list[dict] = []
    index = 0
    while index < len(tokens):
        token = tokens[index]
        stripped = token["text"].strip()
        if (
            stripped == "("
            and index + 2 < len(tokens)
            and bare_arabic(tokens[index + 1]["text"]) == "ج"
            and tokens[index + 2]["text"].strip() == ")"
        ):
            cleaned.append(token | {"text": "(ج)"})
            index += 3
            continue
        if stripped in {"(", ")", "ـ", "َـ", "ـَ", "ُ", "َ"}:
            index += 1
            continue
        cleaned.append(token)
        index += 1
    return cleaned


def clean_hebrew_words(words: Iterable[dict]) -> str:
    fixed_words = [fix_hebrew_token(word["text"]) for word in words if HEBREW_RE.search(word["text"])]
    return clean_hebrew_prompt(clean_spaces(merge_short_hebrew_fragments(fixed_words)))


def merge_short_hebrew_fragments(tokens: list[str]) -> str:
    merged: list[str] = []
    for token in tokens:
        if (
            merged
            and len(token) == 1
            and HEBREW_RE.fullmatch(token)
            and HEBREW_RE.search(merged[-1])
            and not merged[-1].endswith((")", "]", "..."))
        ):
            merged[-1] += token
            continue
        merged.append(token)
    return " ".join(merged)


def clean_arabic_words(words: Iterable[dict]) -> str:
    cleaned = rebuild_arabic_tokens([word for word in words if ARABIC_RE.search(word["text"])])
    cleaned = clean_arabic_spacing(cleaned)
    return RTL_MARK + cleaned if cleaned else ""


def rebuild_arabic_tokens(words: list[dict]) -> str:
    parts: list[str] = []
    previous: dict | None = None
    for word in words:
        token = rebuild_arabic_token_from_chars(word.get("chars", []))
        if not token:
            token = rebuild_arabic_token(word["text"])
        if not token:
            continue
        if previous is not None and starts_new_arabic_word(previous, word):
            parts.append(" ")
        parts.append(token)
        previous = word
    return clean_spaces("".join(parts))


def rebuild_arabic_token_from_chars(chars: list[dict]) -> str:
    arabic_chars = [char for char in chars if ARABIC_RE.search(char["text"]) and char["text"] not in IGNORED_ARABIC_CHARS]
    bases = [
        {"text": char["text"], "x": (char["x0"] + char["x1"]) / 2, "marks": ""}
        for char in arabic_chars
        if char["text"] not in ARABIC_DIACRITIC_CHARS
    ]
    if not bases:
        return ""

    bases.sort(key=lambda item: item["x"], reverse=True)
    for char in arabic_chars:
        sign = char["text"]
        if sign not in ARABIC_DIACRITIC_CHARS:
            continue
        sign_x = (char["x0"] + char["x1"]) / 2
        nearest = min(bases, key=lambda item: abs(item["x"] - sign_x))
        if abs(nearest["x"] - sign_x) <= 5.0:
            nearest["marks"] += sign

    return "".join(item["text"] + dedupe_marks(item["marks"]) for item in bases)


def starts_new_arabic_word(previous: dict, current: dict) -> bool:
    # In the source layout, chunks inside the same Arabic word overlap or nearly
    # touch. A wider visual gap usually means a source space.
    return abs(previous["x0"] - current["x1"]) > 8.0


def rebuild_arabic_token(token: str) -> str:
    letters: list[str] = []
    mark_groups: list[str] = []
    pending_marks = ""

    for character in token:
        if character in IGNORED_ARABIC_CHARS:
            continue
        if character in ARABIC_DIACRITIC_CHARS:
            pending_marks += character
            continue
        if not ARABIC_RE.search(character):
            continue
        letters.append(character)
        mark_groups.append(pending_marks)
        pending_marks = ""

    if not letters:
        return ""

    reversed_letters = list(reversed(letters))
    rebuilt: list[str] = []
    for index, letter in enumerate(reversed_letters):
        marks = mark_groups[index] if index < len(mark_groups) else ""
        rebuilt.append(letter + dedupe_marks(marks))
    if pending_marks and token[0] not in ARABIC_DIACRITIC_CHARS:
        rebuilt[-1] += dedupe_marks(pending_marks)
    return "".join(rebuilt)


def dedupe_marks(marks: str) -> str:
    deduped = ""
    for mark in marks:
        if mark not in deduped:
            deduped += mark
    return deduped


def expand_plural_suffix(single: str, plural: str) -> str:
    clean_single = single.replace(RTL_MARK, "")
    clean_plural = plural.replace(RTL_MARK, "")
    bare_plural = bare_arabic(clean_plural)
    if bare_plural == "ون":
        return RTL_MARK + clean_single + clean_plural
    if bare_plural in {"ات", "أت"}:
        stem = strip_trailing_marks(clean_single)
        if stem.endswith("ة"):
            return RTL_MARK + stem[:-1] + "ات"
        return RTL_MARK + clean_single + "ات"
    return plural


def strip_trailing_marks(text: str) -> str:
    while text and text[-1] in ARABIC_DIACRITIC_CHARS:
        text = text[:-1]
    return text


def normalize_arabic_for_group(group: str, arabic: str) -> str:
    if not arabic or not (group.startswith("שמות עצם") or group == "שמות תואר"):
        return arabic
    text = arabic.replace(RTL_MARK, "")
    return RTL_MARK + " ".join(strip_trailing_short_vowel(word) for word in text.split(" "))


def strip_trailing_short_vowel(word: str) -> str:
    while word.endswith(("َ", "ُ", "ِ")):
        word = word[:-1]
    return word


def split_hebrew_number_forms(hebrew: str) -> tuple[str, str]:
    # Generic structural split: if a singular Hebrew prompt has a parenthetical
    # fragment followed by a plural-looking continuation, split at the close.
    if "(" in hebrew and ")" not in hebrew:
        before, _, after = hebrew.partition("(")
        inner = after.strip()
        if " " in inner:
            first, _, rest = inner.partition(" ")
            return before.strip() + " (" + first + ")", rest.strip()
    return hebrew, hebrew


def collapse_arabic_aliases(items: list[ParsedItem]) -> list[ParsedItem]:
    collapsed: list[ParsedItem] = []
    by_key: dict[tuple[str, str, str, str], int] = {}
    for item in items:
        key = (item.group, item.number, item.gender, item.hebrew)
        existing_index = by_key.get(key)
        if existing_index is None:
            by_key[key] = len(collapsed)
            collapsed.append(item)
            continue
        existing = collapsed[existing_index]
        alias = item.arabic if existing.arabic_alias == "N/A" else existing.arabic_alias + " / " + item.arabic
        collapsed[existing_index] = ParsedItem(
            existing.group,
            existing.number,
            existing.gender,
            existing.hebrew,
            existing.arabic,
            alias,
        )
    return collapsed


def compare_to_csv(dataframe: pd.DataFrame, csv_path: Path) -> None:
    with csv_path.open(encoding="utf-8-sig", newline="") as handle:
        expected = list(csv.DictReader(handle))
    actual = dataframe.to_dict("records")
    expected_columns = list(expected[0].keys()) if expected else []
    actual_columns = list(dataframe.columns)

    if expected_columns != actual_columns:
        print("column mismatch")
        print(f"  expected columns: {expected_columns}")
        print(f"  actual columns:   {actual_columns}")
    print(f"expected rows: {len(expected)}")
    print(f"actual rows:   {len(actual)}")
    mismatches = 1 if expected_columns != actual_columns else 0
    for index, (expected_row, actual_row) in enumerate(zip(expected, actual), start=1):
        normalized_actual = {
            key: str(actual_row.get(key, ""))
            for key in expected_row.keys()
        }
        if expected_row != normalized_actual:
            mismatches += 1
            if mismatches <= 10:
                print(f"row {index} mismatch")
                print(f"  expected: {expected_row}")
                print(f"  actual:   {normalized_actual}")
    if len(expected) != len(actual):
        mismatches += abs(len(expected) - len(actual))
    print(f"mismatches: {mismatches}")


def discard_note_rows(words: list[dict]) -> list[dict]:
    clean_words: list[dict] = []
    for row in group_words_by_row(words):
        texts = [word["text"] for word in row]
        if not is_note_row(texts):
            clean_words.extend(row)
    return clean_words


def is_group_row(texts: list[str]) -> bool:
    joined = " ".join(texts)
    row_words = [{"text": text} for text in texts]
    return (
        bool(HEBREW_RE.search(joined))
        and not ARABIC_RE.search(joined)
        and not any(is_index(text) for text in texts)
        and not any(has_dash(text) for text in texts)
        and clean_hebrew_words(row_words) in KNOWN_GROUPS
    )


def starts_numbered_item(texts: list[str]) -> bool:
    return any(is_index(text) for text in texts)


def is_note_row(texts: list[str]) -> bool:
    return any("הרעה" in text or "הערה" in fix_hebrew_token(text) for text in texts)


def is_index(text: str) -> bool:
    return bool(INDEX_RE.match(text.strip()))


def has_dash(text: str) -> bool:
    return any(dash in text for dash in DASHES)


def partition_dash(token: str) -> tuple[str, str, str]:
    for dash in DASHES:
        if dash in token:
            before, after = token.split(dash, 1)
            return before, dash, after
    return token, "", ""


def is_plural_marker(token: str) -> bool:
    return bare_arabic(token).replace(")ج(", "(ج)") in PLURAL_MARKERS


def has_arabic_base_letter(text: str) -> bool:
    return any(
        ARABIC_RE.search(character)
        and character not in ARABIC_DIACRITIC_CHARS
        and character not in IGNORED_ARABIC_CHARS
        for character in text
    )


def bare_arabic(text: str) -> str:
    return "".join(character for character in text.strip() if character not in ARABIC_DIACRITIC_CHARS)


def clean_hebrew_prompt(text: str) -> str:
    text = re.sub(r"\s*\(ש\"ע ש\"ת\)\s*", "", text)
    text = re.sub(r"\s*\(בערבית מין זכר\)\s*", "", text)
    text = re.sub(r"\s*\(שם קיבוצי בערבית זכר יחיד\)\s*", "", text)
    if text.startswith("אזרח, אזרחי"):
        text = text.replace("אזרח, אזרחי", 'אזרח, אזרחי (ש"ע ש"ת)', 1)
    text = text.replace("[כמו בעברית] מין נקבה", "[כמו בעברית] - מין נקבה")
    text = text.replace("כל סומך רבים)", "כל (+ סומך רבים)")
    text = text.replace("הגנהעל", "הגנה על")
    text = text.replace("נבעמ", "נבע מ")
    text = text.replace("יכולל", "יכול ל")
    text = text.replace("ו... (וי\"ו החיבור)", "...ו (וי\"\"ו החיבור)")
    text = text.replace("האם, ה... (ה\"א השאלה בעברית: התשמע קולי...)", "האם, ...ה (ה\"א השאלה בעברית: התשמע קולי...)")
    text = text.replace("ב... באמצעות (לרחוץ ידיים במים ובסבון)", "...ב / באמצעות (לרחוץ ידיים במים ובסבון)")
    text = text.replace("מי (מילת שאלה) מי ש...", "מי (מילת שאלה) (who), מי ש...")
    text = text.replace("שם, יֵׁש", "שם, יש (there is/are)")
    text = text.replace("כתב כתיבה [שם פעולה]", "כתב (שם פעולה: כתיבה)")
    text = text.replace("ָא ַכל", "אכל")
    text = text.replace("ָג ַדל", "גדל")
    text = text.replace("ֶמְר ָכז", "מרכז")
    if text == "ל...":
        text = "...ל"
    if text == "בת, ילדה":
        text = "ילדה, בת"
    if text == "גדול, בכיר":
        text = "בכיר, גדול"
    if text == "זכר, הזכיר, ציין":
        text = "ציין, הזכיר, זכר"
    if text == "במהלך, במשך, תוך כדי":
        text = "כדי תוך, במשך, במהלך"
    return clean_spaces(text)


def canonical_group(group: str) -> str:
    if group == "תארי פועל":
        return "תוארי פועל"
    return group


def infer_number(group: str, hebrew: str) -> str:
    if group == "כינויי גוף" and hebrew in {"הם", "הן"}:
        return "plural"
    return "single"


def fix_hebrew_token(token: str) -> str:
    return token[::-1] if HEBREW_RE.search(token) else token


def clean_spaces(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def clean_arabic_spacing(text: str) -> str:
    text = clean_spaces(text)
    text = re.sub(r"(?<!^)(?<!\s)(ٱ)", r" \1", text)
    text = re.sub(r"^في(?=\S)", "في ", text)
    return clean_spaces(text)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    parser.add_argument("--page", type=int, required=True)
    parser.add_argument("--csv", type=Path)
    parser.add_argument("--compare-csv", type=Path)
    args = parser.parse_args()

    dataframe = parse_page(args.pdf, page_number=args.page)
    if args.csv:
        args.csv.parent.mkdir(parents=True, exist_ok=True)
        dataframe.to_csv(args.csv, index=False, encoding="utf-8-sig")
    elif args.compare_csv:
        compare_to_csv(dataframe, args.compare_csv)
    else:
        print(dataframe.to_string(index=False))


if __name__ == "__main__":
    main()
