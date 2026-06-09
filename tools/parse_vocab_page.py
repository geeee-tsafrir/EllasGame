#!/usr/bin/env python3
# /// script
# requires-python = ">=3.12"
# dependencies = [
#   "pandas",
#   "pdfplumber",
# ]
# ///
"""Parse one page of the Arabic/Hebrew vocabulary PDF into a draft table.

This parser is layout-based.  It reads word coordinates, splits the page into
right/left columns, detects Hebrew group headings, accumulates numbered items,
and emits columns usable by the game dictionary:

    group, number, gender, Word hebrew, Word arabic

The output is a draft. Arabic signs in this PDF are fragile, so each page should
still be reviewed before writing the final lang_dict/lang_page_N.csv file.
"""

from __future__ import annotations

import argparse
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
ARABIC_DIACRITIC_RE = re.compile(r"^[\u064b-\u065f\u0670]+$")
ARABIC_DIACRITIC_CHARS = set(chr(codepoint) for codepoint in range(0x064B, 0x0660)) | {"\u0670"}
INDEX_RE = re.compile(r"^\.\d+$|^\d+\.$")
DASHES = ("–", "-")
PLURAL_MARKERS = {"(ר)", ")ר(", "(ج)", ")ج("}
KNOWN_GROUPS = {
    "כינויי גוף",
    "שמות עצם",
    "שמות עצם ותואר",
    "מיליות",
    "שמות תואר",
    "תארי פועל",
    "תוארי פועל",
    "מילות יחס",
    "תיאורים",
    "ביטויים",
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
    """Parse one 1-based PDF page number into a DataFrame."""
    pdf_path = Path(pdf_path)
    if page_number < 1:
        raise ValueError("page_number must be 1-based.")

    with pdfplumber.open(pdf_path) as pdf:
        if page_number > len(pdf.pages):
            raise ValueError(f"PDF has {len(pdf.pages)} pages, not {page_number}.")

        page = pdf.pages[page_number - 1]
        words = page.extract_words(keep_blank_chars=False, use_text_flow=False)
        words = discard_header_and_footer(words, page.height)
        words = discard_note_rows(words)

        items: list[ParsedItem] = []
        current_group: str | None = None
        for column_words in split_columns(words, page.width):
            column_items, current_group = parse_column(column_words, current_group)
            items.extend(column_items)

    items = collapse_arabic_aliases(items)
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
            alias)
    return collapsed


def discard_header_and_footer(words: list[dict], page_height: float) -> list[dict]:
    # Header is the bold title at the top. Footnotes/page number are at bottom.
    return [word for word in words if 62 <= word["top"] <= page_height - 80]


def discard_note_rows(words: list[dict]) -> list[dict]:
    clean_words: list[dict] = []
    for row in group_words_by_row(words):
        texts = [word["text"] for word in row]
        if not is_note_row(texts):
            clean_words.extend(row)
    return clean_words


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
            current_group = clean_hebrew(texts)
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


def is_group_row(texts: list[str]) -> bool:
    joined = " ".join(texts)
    if not (
        bool(HEBREW_RE.search(joined))
        and not ARABIC_RE.search(joined)
        and not any(is_index(text) for text in texts)
        and not any(has_dash(text) for text in texts)
    ):
        return False
    return clean_hebrew(texts) in KNOWN_GROUPS


def starts_numbered_item(texts: list[str]) -> bool:
    return any(is_index(text) for text in texts)


def is_note_row(texts: list[str]) -> bool:
    return any("הרעה" in text or "הערה" in fix_hebrew_token(text) for text in texts)


def is_index(text: str) -> bool:
    return bool(INDEX_RE.match(text.strip()))


def has_dash(text: str) -> bool:
    return any(dash in text for dash in DASHES)


def parse_item_words(words: list[dict], group: str | None) -> list[ParsedItem]:
    if not words or group is None:
        return []

    # `words` is already accumulated row-by-row in right-to-left visual order.
    # Re-sorting individual glyph chunks by exact `top` values breaks Arabic
    # diacritics because their top often differs from the base letters.
    tokens = [word["text"] for word in words]
    tokens = [token for token in tokens if not is_index(token)]
    tokens = remove_pattern_marker_tokens(tokens)
    arabic_tokens, hebrew_tokens = split_item_tokens(tokens)

    if not arabic_tokens or not hebrew_tokens:
        return []

    hebrew = clean_hebrew([token for token in hebrew_tokens if HEBREW_RE.search(token)])
    if not hebrew:
        return []

    single_arabic, plural_arabic = split_arabic_number_forms(arabic_tokens)
    single_arabic, single_arabic_alias = split_arabic_alias_forms(single_arabic)
    gender_items = split_gender_forms(group, hebrew, clean_arabic(single_arabic))
    if gender_items:
        return gender_items

    if plural_arabic:
        plural_arabic, plural_arabic_alias = split_arabic_alias_forms(plural_arabic)
        clean_single = clean_arabic(single_arabic)
        clean_plural = expand_plural_suffix(clean_single, clean_arabic(plural_arabic))
        single_hebrew, plural_hebrew = split_hebrew_number_forms(hebrew)
        return [
            ParsedItem(group, "single", "N/A", single_hebrew, clean_single, clean_arabic(single_arabic_alias)),
            ParsedItem(group, "plural", "N/A", plural_hebrew, clean_plural, clean_arabic(plural_arabic_alias)),
        ]

    return [ParsedItem(group, "single", "N/A", hebrew, clean_arabic(single_arabic), clean_arabic(single_arabic_alias))]


def split_hebrew_number_forms(hebrew: str) -> tuple[str, str]:
    if hebrew == "כמות (של כמויות גדולות של נפט":
        return "כמות (של)", "כמויות גדולות של נפט"
    return hebrew, hebrew


def expand_plural_suffix(single: str, plural: str) -> str:
    clean_plural = plural.replace(RTL_MARK, "")
    if clean_plural in {"وَنَََ", "ون", "ونَ"}:
        clean_single = single.replace(RTL_MARK, "")
        return RTL_MARK + normalize_arabic_text(clean_single + "ون")

    if clean_plural not in {"أََتَ", "أَت", "ات", "أت"}:
        return plural

    clean_single = single.replace(RTL_MARK, "")
    if clean_single.endswith("ة"):
        return RTL_MARK + normalize_arabic_text(clean_single[:-1] + "ات")
    if clean_single.endswith("ةَ"):
        return RTL_MARK + normalize_arabic_text(clean_single[:-2] + "ات")
    if clean_single == "حَيَوَان":
        return RTL_MARK + "حَيَوَانَات"
    return plural


def split_gender_forms(group: str, hebrew: str, arabic: str) -> list[ParsedItem]:
    if "ממין נקבה" not in hebrew or "ממין זכר" not in hebrew or "/" not in arabic:
        return []

    hebrew_forms = gender_hebrew_forms(hebrew)
    arabic_forms = [normalize_arabic_text(part.strip()) for part in arabic.replace(RTL_MARK, "").split("/", 1)]
    if len(hebrew_forms) != 2 or len(arabic_forms) != 2:
        return []

    return [
        ParsedItem(group, "single", "female", hebrew_forms[0], RTL_MARK + arabic_forms[0]),
        ParsedItem(group, "single", "male", hebrew_forms[1], RTL_MARK + arabic_forms[1]),
    ]


def gender_hebrew_forms(hebrew: str) -> list[str]:
    match = re.match(r"(.+?\))\s+(.+?\))$", hebrew)
    if not match:
        return []
    return [match.group(1).strip(), match.group(2).strip()]


def split_item_tokens(tokens: list[str]) -> tuple[list[str], list[str]]:
    arabic_tokens: list[str] = []
    hebrew_tokens: list[str] = []
    on_hebrew_side = False

    for token in tokens:
        before_dash, dash, after_dash = partition_dash(token)
        if dash:
            if before_dash and ARABIC_RE.search(before_dash):
                arabic_tokens.append(before_dash)
            if after_dash and ARABIC_RE.search(after_dash):
                arabic_tokens.append(after_dash)
            on_hebrew_side = True
            continue

        if on_hebrew_side:
            hebrew_tokens.append(token)
        elif token == "=" or is_plural_marker(token) or ARABIC_RE.search(token):
            arabic_tokens.append(token)

    return arabic_tokens, hebrew_tokens


def remove_pattern_marker_tokens(tokens: list[str]) -> list[str]:
    cleaned: list[str] = []
    index = 0
    while index < len(tokens):
        token = tokens[index]
        stripped = token.strip()
        if (
                stripped == "("
                and index + 2 < len(tokens)
                and is_arabic_marker_letter(tokens[index + 1])
                and tokens[index + 2].strip() == ")"):
            cleaned.append("(ج)")
            index += 3
            continue
        if stripped in {"(", ")", "ـ", "َـ", "ـَ", "ُ", "َ"}:
            index += 1
            continue
        cleaned.append(token)
        index += 1
    return cleaned


def partition_dash(token: str) -> tuple[str, str, str]:
    for dash in DASHES:
        if dash in token:
            before, after = token.split(dash, 1)
            return before, dash, after
    return token, "", ""


def split_arabic_number_forms(tokens: list[str]) -> tuple[list[str], list[str]]:
    single: list[str] = []
    plural: list[str] = []
    target = single

    for token in tokens:
        if is_plural_marker(token):
            target = plural
            continue
        target.append(token)

    return single, plural


def split_arabic_alias_forms(tokens: list[str]) -> tuple[list[str], list[str]]:
    if "=" not in tokens:
        return tokens, []

    marker_index = tokens.index("=")
    primary = [token for token in tokens[:marker_index] if token != "="]
    alias = [token for token in tokens[marker_index + 1:] if token != "="]
    return primary, alias


def is_plural_marker(token: str) -> bool:
    normalized = "".join(
        character for character in token.strip()
        if character not in ARABIC_DIACRITIC_CHARS
    )
    normalized = normalized.replace(")ج(", "(ج)")
    return normalized in PLURAL_MARKERS


def is_arabic_marker_letter(token: str) -> bool:
    return "".join(
        character for character in token.strip()
        if character not in ARABIC_DIACRITIC_CHARS
    ) == "ج"


def clean_hebrew(tokens: Iterable[str]) -> str:
    fixed = [fix_hebrew_token(token) for token in tokens if HEBREW_RE.search(token)]
    text = clean_spaces(" ".join(fixed))
    replacements = {
        "עצ ם": "עצם",
        "את ה": "אתה",
        "א ת": "את",
        "בי ת": "בית",
        "ב ן": "בן",
        "א ב": "אב",
        "ה דלי": "ילדה",
        "ילד ה": "ילדה",
        "מכת ב": "מכתב",
        "תלמי ד": "תלמיד",
        "מנה ל": "מנהל",
        "לשכ ה": "לשכה",
        "סופ ר": "סופר",
        "ש ר": "שר",
        "עצ ר": "עצר",
        "נדי ב": "נדיב",
        "השאי ר": "השאיר",
        "הקליט ה": "הקליטה",
        "סוכנו ת": "סוכנות",
        "היהודי ת": "היהודית",
        "מא ה": "מאה",
        "ֶא ֶל ף": "אֶלֶף",
        "ֶד ֶל ק": "דֶלֶק",
        "ַצ ַעד": "צַעַד",
        "התפתחו ת": "התפתחות",
        "עצמי ת": "עצמית",
        "עצר ת": "עצרת",
        "צד ק": "צדק",
        "מאו ד": "מאוד",
        "פתאו ם": "פתאום",
        "ִח ֵ לק": "חִלֵּק",
        "השתנ ה": "השתנה",
        "סו ס": "סוס",
        "כוח סו ס": "כוח סוס",
        "ֶש ֶמ ש": "שֶמֶש",
        "תקצי ב": "תקציב",
        "התבצ ע": "התבצע",
        "במה ש..": "במה ש...",
        "יש ל (לעשות": "יש ל (לעשות)",
        "מוסלמ י": "מוסלמי",
        "הסכ ם": "הסכם",
        "נמצ א": "נמצא",
        "ָכ ֵבד": "כָבֵד",
        "ע ל": "על",
        "הוכפ ל": "הוכפל",
        "פנ ט": "נפט",
        "נפ ט": "נפט",
        "כל סומך רבים)": "כל (+ סומך רבים)",
        "שמר, למד": "שמר, למד בעל-פה",
        "החליט ל…": "החליט ל.",
        "ניסה ל…": "ניסה ל.",
        "ה ם": "הם",
        "ה ן": "הן",
    }
    for wrong, right in replacements.items():
        text = text.replace(wrong, right)
    if text == "ל...":
        return "...ל"
    return text


def fix_hebrew_token(token: str) -> str:
    return token[::-1] if HEBREW_RE.search(token) else token


def clean_arabic(tokens: Iterable[str]) -> str:
    fixed: list[str] = []
    pending_marks = ""

    for token in tokens:
        if token == "=":
            continue
        if not ARABIC_RE.search(token):
            continue
        if ARABIC_DIACRITIC_RE.match(token):
            pending_marks += token
            continue

        fixed_token = reverse_arabic_token(token)
        if pending_marks:
            fixed_token = add_marks_to_first_character(fixed_token, pending_marks)
            pending_marks = ""
        fixed.append(fixed_token)

    cleaned = normalize_arabic_text(clean_spaces("".join(fixed)))
    return RTL_MARK + cleaned if cleaned else "N/A"


def normalize_arabic_text(text: str) -> str:
    replacements = {
        "أوََلدََْ": "أَوْلَاد",
        "ثَلَثَ": "ثَلَاث",
        "َثََلََثَْةََ": "ثَلَاثَة",
        "مديَنَةَْ": "مَدِينَة",
        "مُدََنُ": "مُدُن",
        "يَوَم": "يَوْم",
        "أياََم": "أَيَّام",
        "ألْيوََم": "أَلْيَوْم",
        "مَكتوُبْ": "مَكْتُوب",
        "مَكاتَيََب": "مَكَاتِيب",
        "تلْمَيذ": "تِلْمِيذ",
        "تَلََمََيذ": "تَلَامِيذ",
        "مَديرُ": "مُدِير",
        "مُدَرََأء": "مُدَرَاء",
        "مَركزَ": "مَرْكَز",
        "مَرََأكز": "مَرَاكِز",
        "قَرََيبَمن": "قَرِيب مِن",
        "أماَمَََ": "أَمَامَ",
        "لََـ": "لِـ",
        "أكَلََ": "أَكَلَ",
        "فَهَم": "فَهِمَ",
        "كَبرَ": "كَبُرَ",
        "وقََفَ": "وَقَفَ",
        "مئَةْ": "مِئَة",
        "أنْسان": "إِنْسَان",
        "ناسَ": "نَاس",
        "أنْسانية": "إِنْسَانِيَّة",
        "إِنْسَانية": "إِنْسَانِيَّة",
        "وقتْ": "وَقْت",
        "أوْقاتَ": "أَوْقَات",
        "خُطْوة": "خُطْوَة",
        "خُطُوأت": "خُطُوَات",
        "نُموَ": "نُمُوّ",
        "حَقَتَقْرَيَرٱلْمصَير": "حَقّ تَقْرِير ٱلْمَصِير",
        "حُقوقُٱْلَنْسان": "حُقُوق ٱلْإِنْسَان",
        "جَمْعيَة": "جَمْعِيَّة",
        "جَمْعيَات": "جَمْعِيَّات",
        "ظُلَم": "ظُلْم",
        "شرعيةَ": "شَرْعِيَّة",
        "فيما": "فِيمَا",
        "جدأًّ": "جِدًّا",
        "فَجاْةَ": "فَجْأَةً",
        "مسكين": "مِسْكِين",
        "شَديد": "شَدِيد",
        "ثَقيل": "ثَقِيل",
        "تَعْبان": "تَعْبَان",
        "أنْسَانيَ": "إِنْسَانِيّ",
        "عَظيم": "عَظِيم",
        "يَجبَعَلىَأنَ": "يَجِبُ عَلَى أَنْ",
        "لَشَكَ": "لَا شَكَّ",
        "لَبُدَمنَ": "لَا بُدَّ مِنْ",
        "شَردَََعَنََ": "شَرَّدَ عَنْ",
        "قَسمََألىَ": "قَسَّمَ إِلَى",
        "تَطَورََ": "تَطَوَّرَ",
        "حَيوأن": "حَيَوَان",
        "ظَهرْ": "ظَهْر",
        "حمار": "حِمَار",
        "حميرَ": "حَمِير",
        "حصانَ": "حِصَان",
        "حُصنُ": "حُصُن",
        "قُوةحصانَ": "قُوَّة حِصَان",
        "قُوةحِصَان": "قُوَّة حِصَان",
        "صَيف": "صَيْف",
        "حَرأرة": "حَرَارَة",
        "شَمسْ": "شَمْس",
        "وزأَرةٱْلَسكانَ": "وِزَارَة ٱلْإِسْكَان",
        "ميزأنية": "مِيزَانِيَّة",
        "تَمَ": "تَمَّ",
        "بَثَ": "بَثَّ",
        "حَلَ": "حَلَّ",
        "أعَدَ": "أَعَدَّ",
        "أنْضَمَألىَ": "اِنْضَمَّ إِلَى",
        "أحْتَلَ": "إِحْتَلَّ",
        "أضْطَرَألىَ": "إِضْطَرَّ إِلَى",
        "أضْطُرَألىَ": "أُضْطُرَّ إِلَى",
        "أستَعَدَ": "إِسْتَعَدَّ",
        "أستَقَلَ": "إِسْتَقَلَّ",
        "ألَف": "أَلْف",
        "أَلَف": "آلَاف",
        "مَلْيَون": "مِلْيُون",
        "مَليََيَن": "مَلَايِين",
        "وقوُد": "وَقُود",
        "مَحْروقاَت": "مَحْرُوقَات",
        "تجاَرة": "تِجَارَة",
        "مُسلم": "مُسْلِم",
        "مُسْلِمون": "مُسْلِمُون",
        "مُسلمون": "مُسْلِمُون",
        "أَتِّفاقَية": "إِتِّفَاقِيَّة",
        "أَتِّفاقَ": "إِتِّفَاق",
        "اِتِّفَاقية": "إِتِّفَاقِيَّة",
        "مَوجَود": "مَوْجُود",
        "ألرأهنَ": "أَلرَّاهِن",
        "مُستحيلَ": "مُسْتَحِيل",
        "منَٱلْمستَحيلأنَ": "مِنَ ٱلْمُسْتَحِيل أَنْ",
        "تجاَريَ": "تِجَارِيّ",
        "بَلَديَ": "بَلَدِيّ",
        "هَربَ": "هَرَّبَ",
        "قَررَ+َأنَيَفْعَلَ": "قَرَّرَ",
        "حاَولَ+َأنَيَفْعَلََ": "حَاوَلَ",
        "أعْلَنَعَنَ": "أَعْلَنَ",
        "أرْسلأََلَىَ/َلَ": "أَرْسَلَ إِلَى",
        "أبْلَغَبَ": "أَبْلَغَ",
        "أنْتَظَرَ": "اِنْتَظَرَ",
        "أعْتَقَلَ": "اِعْتَقَلَ",
        "أحْبطَ": "أَحْبَطَ",
        "اِنْتَظَرَ": "إِنْتَظَرَ",
        "اِعْتَقَلَ": "إِعْتَقَلَ",
        "وزأَرةَٱلَستيعََابَ": "وِزَارَة ٱلِٱسْتِيعَاب",
        "وكَاَلةَ": "وِكَالَة",
        "وكَاَلات": "وِكَالات",
        "ألَْ": "أَلْ",
        "وكَاَلَةٱَ": "وِكَالَة ا",
        "اَلْيهوُديةََْ": "اُلْيَهُودِيَّة",
        "الْيهوُديةََْ": "اُلْيَهُودِيَّة",
        "قاَدموُنَٱَ": "قَادِمُونَ ا",
        "لْجُدََدُ": "لْجُدُد",
        "ألْقَادِمُونَ": "أَلْقَادِمُونَ",
        "قَادِمُونَ اَلْ": "قَادِمُونَ اُلْ",
        "جَميعَ": "جَمِيع",
        "فيَ": "فِي ",
        "جَميَع": "جَمِيع ",
        "أنْحَاَءٱَ": "أَنْحَاء ا",
        "لبْلََدََ": "لْبِلَاد",
        "أَنْحَاءِ اَلْ": "أَنْحَاءِ اُلْ",
        "أَنْحَاء اَلْ": "أَنْحَاء اُلْ",
        "مَجلْسٱَْلَمََنْ": "مَجْلِس اُلْأَمْن",
        "خلَيَجَٱ": "خَلِيج ا",
        "لْعَربيََ": "لْعَرَبِيّ",
        "ألْخَلِيج": "أَلْخَلِيج",
        "خَلِيج اَلْ": "خَلِيج اُلْ",
        "خَلِيج الْ": "خَلِيج اُلْ",
        "حمايَةَْ": "حِمَايَة",
        "جهَةَ": "جِهَة",
        "جهَاتَسياسيََََة": "جِهَات سِيَاسِيَّة",
        "جِهَاتَسياسيََََة": "جِهَات سِيَاسِيَّة",
        "جهَات": "جِهَات",
        "جهَاَتٱَ": "جِهَات ا",
        "لْمعنْيََة": "لْمَعْنِيَّة",
        "لْمخْتَصََة": "لْمُخْتَصَّة",
        "ألْجِهَات": "أَلْجِهَات",
        "جِهَات اَلْ": "جِهَات اُلْ",
        "جِهَات الْ": "جِهَات اُلْ",
        "كَمِّيَة": "كَمِّيَّة",
        "كَمِّيات": "كَمِّيَّات",
        "أَتَمنَفَََََطْ": "كَمِّيَّات كَبِيرَة مِنَ النِّفْط",
        "جهَازَ": "جِهَاز",
        "أَجْهزََة": "أَجْهِزَة",
        "أجْهزةَْٱَلمََنْ": "أَجْهِزَة اُلْأَمْن",
        "مُوأطَن": "مُوَاطِن",
        "مُواطِنون": "مُواطِنُون",
        "مَدنَيَ": "مَدَنِيّ",
        "مَدَنِيّون": "مَدَنِيُّون",
        "وقعََعَلىَ": "وَقَّعَ عَلَى",
        "صَورَ": "صَوَّرَ",
        "نَْبعََعَنَ": "نَبَعَ عَنْ",
        "نَْبعََ عَنَ": "نَبَعَ عَنْ",
        "حَفظََ": "حَفِظَ",
        "قَدَرَعَلىَ": "قَدَرَ عَلَى",
        "هَددََ": "هَدَّدَ",
        "خطَطََ": "خَطَّطَ",
        "تَعلَقَبَ": "تَعَلَّقَ بِ",
        "تَضاَعَفَ": "تَضَاعَفَ",
    }
    for wrong, right in replacements.items():
        text = text.replace(wrong, right)
    return text


def reverse_arabic_token(token: str) -> str:
    """Reverse visual Arabic extraction without moving leading marks to the end.

    pdfplumber often returns Arabic base letters in visual order while marks are
    interspersed before the letters they visually sit near. A raw `token[::-1]`
    turns a leading sign into a final sign. Instead, reverse only base letters
    and reapply the extracted mark groups from left to right.
    """
    letters: list[str] = []
    mark_groups: list[str] = []
    pending_marks = ""

    for character in token:
        if character in ARABIC_DIACRITIC_CHARS:
            pending_marks += character
        else:
            letters.append(character)
            mark_groups.append(pending_marks)
            pending_marks = ""

    if not letters:
        return pending_marks

    reversed_letters = list(reversed(letters))
    rebuilt: list[str] = []
    for index, letter in enumerate(reversed_letters):
        marks = mark_groups[index] if index < len(mark_groups) else ""
        rebuilt.append(letter + marks)
    if pending_marks and token[0] not in ARABIC_DIACRITIC_CHARS:
        rebuilt[-1] += pending_marks
    return "".join(rebuilt)


def add_marks_to_first_character(token: str, marks: str) -> str:
    if not token:
        return marks
    existing_marks = ""
    index = 1
    while index < len(token) and token[index] in ARABIC_DIACRITIC_CHARS:
        existing_marks += token[index]
        index += 1

    new_marks = "".join(mark for mark in marks if mark not in existing_marks)
    return token[0] + existing_marks + new_marks + token[index:]


def clean_spaces(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    parser.add_argument("--page", type=int, required=True, help="1-based page number to parse.")
    parser.add_argument("--csv", type=Path, help="Optional CSV output path.")
    args = parser.parse_args()

    dataframe = parse_page(args.pdf, page_number=args.page)
    if args.csv:
        args.csv.parent.mkdir(parents=True, exist_ok=True)
        dataframe.to_csv(args.csv, index=False, encoding="utf-8-sig")
    else:
        print(dataframe.to_string(index=False))


if __name__ == "__main__":
    main()
