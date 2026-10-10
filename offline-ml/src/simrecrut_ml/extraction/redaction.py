"""Mask direct identifiers while preserving original character positions."""

import re
from dataclasses import dataclass, field

from .models import ExtractionError


REDACTION_VERSION = "identifier-mask-v2"
PATTERNS = (
    re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}"),
    re.compile(r"(?:https?://|www\.)[^\s<>]+", re.IGNORECASE),
    re.compile(r"\b(?:linkedin\.com|github\.com)/[^\s<>]+", re.IGNORECASE),
    re.compile(r"(?<!\w)(?:\+\d[\d ()-]{7,}\d|\(?\d{3}\)?[ .-]\d{3}[ .-]\d{4})(?!\w)"),
    re.compile(r"\b\d{3}-\d{2}-\d{4}\b"),
    re.compile(r"(?<!\w)\d{10,15}(?!\w)"),
    re.compile(r"(?<!\w)(?:\+?\d{1,3}[ .-])?(?:\d{2}[ .-]){4}\d{2}(?!\w)"),
    re.compile(
        r"(?im)^\s*(?:full name|candidate name|name|e-?mail|phone|mobile|"
        r"date of birth|dob|birth date|address|passport|ssn|national id|"
        r"license number|credential id|certification id)\s*[:=].*$"
    ),
    re.compile(r"(?im)^.*\b(?:city,?\s+state|street address|zip code)\b.*$"),
    re.compile(r"(?im)^\s*\d{1,5}\s+[A-Za-z][^\n]{0,70}\b"
               r"(?:street|avenue|road|lane|drive|st\.|ave\.|rd\.)\b[^\n]*$"),
)


@dataclass(frozen=True)
class RedactedText:
    """Retain local originals separately from masked provider fields."""

    original: dict[str, str] = field(repr=False)
    fields: dict[str, str] = field(repr=False)
    spans: dict[str, tuple[tuple[int, int], ...]] = field(repr=False)


def redact_fields(
    fields: dict[str, str], identifiers: tuple[str, ...] = (),
    identifier_spans: dict[str, list[tuple[int, int]]] | None = None,
    privacy_checked: bool = False,
) -> RedactedText:
    """Mask contact patterns and known identifier literals with spaces.

    Offsets and newlines are preserved. Keep originals only in ignored
    evidence. Known names must come from explicit local source metadata;
    they are never inferred as gender or ethnicity. Suspicious name-like
    headers block external extraction pending a local privacy check.
    """
    original, masked, locations = {}, {}, {}
    for key, text in fields.items():
        if not isinstance(text, str):
            raise ExtractionError("INVALID_TEXT_FIELD")
        original[key] = text
        spans = [match.span() for pattern in PATTERNS for match in pattern.finditer(text)]
        for start, end in (identifier_spans or {}).get(key, []):
            if not 0 <= start < end <= len(text):
                raise ExtractionError("INVALID_PRIVACY_SPAN")
            spans.append((start, end))
        labelled_names = re.findall(
            r"(?im)^\s*(?:full name|candidate name|name)\s*[:=]\s*([^\r\n]+)", text,
        )
        for identifier in (*identifiers, *labelled_names):
            if identifier.strip():
                pattern = re.compile(re.escape(identifier), re.IGNORECASE)
                spans.extend(match.span() for match in pattern.finditer(text))
        characters = list(text)
        for start, end in spans:
            for offset in range(start, end):
                if characters[offset] not in "\r\n":
                    characters[offset] = " "
        result = "".join(characters)
        if key.startswith("pdf_page_") and not privacy_checked:
            header = next((line.strip() for line in result.splitlines() if line.strip()), "")
            name_like = re.fullmatch(
                r"[A-Z][A-Za-z.]+(?: [A-Z][A-Za-z.]+){1,3}(?:\s*[-|].*)?", header,
            )
            role_words = {
                "engineer", "developer", "manager", "administrator", "specialist", "analyst",
                "architect", "consultant", "technician", "director", "information", "technology",
            }
            prefix = re.split(r"\s*[-|]\s*", header, maxsplit=1)[0]
            if name_like and not role_words.intersection(prefix.lower().split()):
                raise ExtractionError("PRIVACY_REVIEW_REQUIRED")
        masked[key] = result
        locations[key] = tuple(sorted(set(spans)))
    return RedactedText(original, masked, locations)
