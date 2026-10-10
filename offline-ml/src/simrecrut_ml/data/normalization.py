"""Normalize source observations without confirming professional facts."""

import hashlib
import json
import re
import unicodedata
from decimal import Decimal, InvalidOperation
from typing import Any

from .models import SourceRecord


PROFESSIONAL_AREAS = ("skills", "experience", "education", "languages", "projects")
RESEARCH_AREAS = ("school", "referral", "gender", "ethnicity")
EVIDENCE_COLUMNS = {
    "job": (
        "Position", "Long Description", "Exp Years", "Primary Keyword", "English Level",
    ),
    "profile": (
        "Position", "Moreinfo", "Looking For", "Highlights", "Primary Keyword",
        "English Level", "Experience Years", "CV",
    ),
    "resume": ("Resume_str", "Category"),
}


def normalize_text(value: str) -> str:
    """Use NFC and whitespace cleanup while retaining case and punctuation.

    Accents, numbers, C++, C#, .NET, and source wording stay distinct.
    The operation is idempotent and does not implement a skill alias.
    """
    text = unicodedata.normalize("NFC", value).replace("\r\n", "\n").replace("\r", "\n")
    return "\n".join(re.sub(r"[^\S\n]+", " ", line).strip() for line in text.split("\n")).strip()


def _digest(value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def content_fingerprint(record: SourceRecord) -> str:
    """Group exact normalized professional evidence, not unrelated blanks.

    Source identifiers are excluded from content deduplication. A record
    with no descriptive evidence keeps its own source identity so blank
    profiles cannot become a fabricated shared person.
    """
    values = {
        key: normalize_text(value) if isinstance(value, str) else value
        for key in EVIDENCE_COLUMNS[record.kind]
        if (value := record.values.get(key)) is not None and value != ""
    }
    descriptive = (
        ("Long Description",) if record.kind == "job"
        else ("Resume_str",) if record.kind == "resume"
        else ("Moreinfo", "Looking For", "Highlights", "CV")
    )
    if not any(str(values.get(key, "")).strip() for key in descriptive):
        values["source_identity"] = [record.source_id, record.source_record_id]
    return _digest({"kind": record.kind, "evidence": values})


def source_identity(record: SourceRecord) -> str:
    """Return a stable identifier independent of file order and row number."""
    return "src_" + _digest([record.source_id, record.source_record_id])[:24]


def observation(original: Any, source_field: str, numeric: bool = False) -> dict[str, Any]:
    """Keep source and normalized values with an unresolved review state."""
    state = "MISSING"
    normalized = None
    if original is not None and str(original).strip():
        normalized = normalize_text(str(original))
        state = "OBSERVED"
        if numeric:
            try:
                number = Decimal(normalized)
                if not number.is_finite() or number < 0:
                    raise InvalidOperation
                normalized = str(number.normalize())
            except InvalidOperation:
                normalized = None
                state = "CONFLICTING"
    return {
        "original": original, "normalized": normalized, "state": state,
        "source_field": source_field, "confirmed": False,
    }


def prepare_record(record: SourceRecord, family: str, selection_version: str) -> dict[str, Any]:
    """Create a local review draft, preserving evidence outside features.

    All professional areas remain UNKNOWN until a recorded human review.
    Publisher English/experience labels are observations, not accepted
    language equivalences, relevant years, requirements, or hiring labels.
    Research values remain unknown and are never inferred from text.
    """
    fingerprint = content_fingerprint(record)
    values = record.values
    experience_field = "Experience Years" if record.kind == "profile" else "Exp Years"
    observed = {
        "title": observation(values.get("Position"), "Position"),
        "category": observation(values.get("Category", values.get("Primary Keyword")),
                                "Category" if record.kind == "resume" else "Primary Keyword"),
        "experience": observation(values.get(experience_field), experience_field, numeric=True),
        "english_level": observation(values.get("English Level"), "English Level"),
    }
    return {
        "schema_version": "prepared-source-v1",
        "record_id": "draft_" + _digest([record.source_id, fingerprint])[:24],
        "source_identity": source_identity(record),
        "source_id": record.source_id,
        "source_record_id": record.source_record_id,
        "kind": record.kind,
        "source_fingerprint": fingerprint,
        "base_profile_key": "profile_" + fingerprint if record.kind != "job" else None,
        "source_locator": {"file": record.source_file, "row": record.row_number},
        "source_values": values,
        "observations": observed,
        "selection": {"family": family, "version": selection_version, "reviewed": False},
        "professional": {area: [] for area in PROFESSIONAL_AREAS},
        "field_status": {area: "UNKNOWN" for area in PROFESSIONAL_AREAS},
        "research_attributes": {
            area: {"value": "unknown", "source": None, "state": "UNKNOWN"}
            for area in RESEARCH_AREAS
        },
        "review_status": "REVIEW_REQUIRED",
        "pdf": {"path": record.pdf_path, "ocr_needed": None, "inspected": False},
    }
