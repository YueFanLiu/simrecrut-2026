"""Read, mask, extract and check offline CV and job facts.

Use load_pilot_records or iter_selected_records to select sources, then
clean_records to process them. prepare_images and run_image_trial reuse
that local evidence for the checked image comparison. Providers implement
FactExtractionProvider; the shared flow has no DeepSeek dependency.
"""

import csv
import hashlib
import json
import math
import re
import time
import unicodedata
from collections import Counter
from copy import deepcopy
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from itertools import islice
from pathlib import Path
from typing import Any, Iterator, Protocol


class PreparationError(ValueError):
    """Reject an invalid source, path, mapping, or review contract."""


@dataclass(frozen=True)
class SourceRecord:
    """Describe one publisher row before review or deduplication.

    ``values`` contains local source evidence, which may include personal
    data. It is never a professional feature vector or a training sample.
    The one-based row number locates the record without logging its text.
    """

    source_id: str
    kind: str
    source_record_id: str
    source_file: str
    row_number: int
    values: dict[str, Any]
    pdf_path: str | None = None


SOURCE_KINDS = {
    "djinni-jobs": "job",
    "djinni-profiles": "profile",
    "resume-dataset": "resume",
}


def safe_dataset_path(dataset_root: Path, relative_path: str) -> Path:
    """Resolve a relative file inside the datasets tree.

    Reject absolute paths, parent traversal, and escaping symlinks before
    reading publisher files or resolving a resume PDF index.
    """
    portable = relative_path.replace("\\", "/")
    relative = Path(portable)
    if (
        relative.is_absolute() or portable.startswith("/")
        or ":" in portable or ".." in relative.parts
    ):
        raise PreparationError("Dataset paths must stay inside the datasets tree.")
    root = dataset_root.resolve()
    result = (root / relative).resolve()
    if not result.is_relative_to(root):
        raise PreparationError("Dataset path resolves outside the datasets tree.")
    return result


def file_sha256(path: Path) -> str:
    """Hash a file in chunks without loading it all into memory."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_acquisition_manifest(path: Path) -> tuple[Path, list[dict[str, Any]]]:
    """Read a single-source or combined acquisition manifest.

    The manifest must live in ``datasets/manifests``. File paths are
    relative to ``datasets``. Source metadata records the publisher URL,
    declared license, actual revision, and acquisition time.
    """
    path = Path(path).resolve()
    if path.parent.name != "manifests":
        raise PreparationError("Acquisition manifests must be in datasets/manifests.")
    payload = json.loads(path.read_text(encoding="utf-8"))
    sources = payload.get("sources", [payload])
    if not isinstance(sources, list) or not sources:
        raise PreparationError("The acquisition manifest has no source entries.")
    seen = set()
    for source in sources:
        required = ("source_id", "source_url", "declared_license", "revision", "downloaded_at")
        if not isinstance(source, dict) or any(not source.get(key) for key in required):
            raise PreparationError("A source is missing acquisition provenance.")
        if source["source_id"] not in SOURCE_KINDS or source["source_id"] in seen:
            raise PreparationError("Unsupported or repeated dataset source.")
        seen.add(source["source_id"])
        if not isinstance(source.get("files"), list) or not source["files"]:
            raise PreparationError("A source has no acquired files.")
    return path.parent.parent, sources


def verify_source_files(dataset_root: Path, source: dict[str, Any]) -> None:
    """Check every acquired file against its recorded size and SHA-256."""
    entries = source["files"] + source.get("extracted_files", [])
    for entry in entries:
        path = safe_dataset_path(dataset_root, entry["path"])
        if not path.is_file():
            raise PreparationError("An acquired dataset file is missing.")
        if path.stat().st_size != entry["bytes"] or file_sha256(path) != entry["sha256"]:
            raise PreparationError("An acquired dataset file failed its integrity check.")


def _json_value(value: Any) -> Any:
    if value is None or isinstance(value, (str, bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, dict):
        return {str(key): _json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_value(item) for item in value]
    if hasattr(value, "isoformat"):
        return value.isoformat()
    raise PreparationError("A publisher field has an unsupported value type.")


def _rows(path: Path) -> Iterator[dict[str, Any]]:
    if path.suffix.lower() == ".csv":
        csv.field_size_limit(16 * 1024 * 1024)
        with path.open(encoding="utf-8-sig", newline="") as stream:
            yield from csv.DictReader(stream)
    elif path.suffix.lower() in {".jsonl", ".ndjson"}:
        with path.open(encoding="utf-8") as stream:
            for line in stream:
                if line.strip():
                    row = json.loads(line)
                    if not isinstance(row, dict):
                        raise PreparationError("Publisher rows must be JSON objects.")
                    yield row
    elif path.suffix.lower() == ".json":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(payload, list) or any(not isinstance(row, dict) for row in payload):
            raise PreparationError("A publisher JSON file must contain a row array.")
        yield from payload
    elif path.suffix.lower() == ".parquet":
        try:
            import pyarrow.parquet as parquet
        except ImportError as error:
            raise PreparationError("Parquet preparation requires pyarrow.") from error
        for batch in parquet.ParquetFile(path).iter_batches(batch_size=1024):
            for row in batch.to_pylist():
                yield {key: _json_value(value) for key, value in row.items()}
    else:
        raise PreparationError("Unsupported publisher data format.")


def iter_source_records(
    dataset_root: Path, source: dict[str, Any]
) -> Iterator[SourceRecord]:
    """Stream identified rows and link original resume PDFs when present.

    Djinni uses ``id``; Resume Dataset uses ``ID``. Missing identities are
    rejected rather than replaced by row positions. PDF availability and
    OCR need are separate: this adapter never claims that OCR is needed.
    """
    source_id = source["source_id"]
    kind = SOURCE_KINDS[source_id]
    entries = source.get("extracted_files", []) or source["files"]
    data_files = [
        entry for entry in entries
        if entry.get("role", "data") == "data"
        if Path(entry["path"]).suffix.lower() in {".csv", ".parquet", ".jsonl", ".ndjson", ".json"}
    ]
    if not data_files:
        raise PreparationError("The acquired source has no supported row file.")
    for entry in sorted(data_files, key=lambda item: item["path"]):
        path = safe_dataset_path(dataset_root, entry["path"])
        for number, row in enumerate(_rows(path), start=1):
            identity = row.get("ID" if kind == "resume" else "id")
            if identity is None or str(identity).strip() == "":
                raise PreparationError("A publisher record has no source identity.")
            pdf_path = None
            if kind == "resume":
                category = str(row.get("Category", ""))
                candidate = (
                    f"raw/resume-dataset/data/data/{category}/{identity}.pdf"
                )
                pdf = safe_dataset_path(dataset_root, candidate)
                if pdf.is_file():
                    pdf_path = candidate
            yield SourceRecord(
                source_id, kind, str(identity), entry["path"], number,
                {key: _json_value(value) for key, value in row.items()}, pdf_path,
            )


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


def load_selection(path: Path) -> dict[str, Any]:
    """Read explicit source category filters, which do not approve facts.

    Shape: ``{version, sources: {source_id: {category_field, categories}}}``.
    ``categories`` maps exact publisher labels to provisional IT families.
    The source category is retained; selection is never inferred from a
    title, school, name, photograph, or demographic attribute.
    """
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if not payload.get("version") or not isinstance(payload.get("sources"), dict):
        raise PreparationError("Selection needs a version and explicit source filters.")
    for selection in payload["sources"].values():
        if not isinstance(selection, dict) or not selection.get("category_field"):
            raise PreparationError("Each selection needs a source category field.")
        categories = selection.get("categories")
        if not isinstance(categories, dict) or not categories:
            raise PreparationError("Each selection needs explicit IT category mappings.")
        if any(not isinstance(key, str) or not isinstance(value, str) or not value.strip()
               for key, value in categories.items()):
            raise PreparationError("Selection categories and families must be nonempty text.")
    return payload


def write_json_line(stream: Any, payload: dict[str, Any]) -> None:
    """Write one UTF-8 record with stable JSON keys and finite numbers."""
    stream.write(json.dumps(payload, ensure_ascii=False, sort_keys=True, allow_nan=False) + "\n")


def clean_output_path(dataset_root: Path, output: Path) -> Path:
    """Restrict derived data to datasets/clean."""
    root = (dataset_root / "clean").resolve()
    result = Path(output).resolve()
    if not result.is_relative_to(root):
        raise PreparationError("Derived data must be written inside datasets/clean.")
    return result


def _selection_metadata(record: SourceRecord, family: str, version: str) -> dict:
    """Retain source identity and filter metadata without drafting facts."""
    fingerprint = content_fingerprint(record)
    return {
        "record_id": "draft_" + _digest([record.source_id, fingerprint])[:24],
        "kind": record.kind,
        "source_id": record.source_id,
        "source_record_id": record.source_record_id,
        "source_fingerprint": fingerprint,
        "source_locator": {"file": record.source_file, "row": record.row_number},
        "selection": {"family": family, "version": version, "reviewed": False},
    }


class ExtractionError(PreparationError):
    """Report a safe stage code without vendor bodies or personal values."""

    def __init__(self, code: str, retryable: bool = False) -> None:
        super().__init__(code)
        self.code = code
        self.retryable = retryable


@dataclass(frozen=True)
class PageImage:
    """Carry a visually checked PNG with its evidence page reference."""

    source_field: str
    png: bytes = field(repr=False)


@dataclass(frozen=True)
class ExtractionRequest:
    """Carry only redacted fields, kind and the versioned fact contract.

    Source identifiers, original paths, labels and job weights have no
    place in this request. Repair codes contain validation categories,
    never the rejected response or copied personal values.
    """

    kind: str
    fields: dict[str, str] = field(repr=False)
    schema: dict[str, Any] = field(repr=False)
    repair_codes: tuple[str, ...] = ()
    images: tuple[PageImage, ...] = field(default=(), repr=False)


@dataclass(frozen=True)
class ProviderReply:
    """Retain provider content locally and separate safe usage metadata."""

    content: str = field(repr=False)
    model: str
    usage: dict[str, int]


class FactExtractionProvider(Protocol):
    """Extract unconfirmed facts; callers own privacy, validation and review.

    Implementations receive redacted text or checked images and return a
    JSON string. A local rule algorithm can implement this same boundary
    without changing text evidence checks or local draft storage.
    Raise ExtractionError with a safe code on unavailable extraction.
    """

    @property
    def version(self) -> str:
        """Identify implementation and settings for cache invalidation."""
        ...

    def extract(self, request: ExtractionRequest) -> ProviderReply:
        """Return proposed facts without choosing scores or HR flags."""
        ...


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


def read_records(path: Path) -> list[dict]:
    """Read local JSONL records; reject repeated record identifiers."""
    if not path.is_file():
        return []
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]
    if len({row["record_id"] for row in rows}) != len(rows):
        raise ExtractionError("DUPLICATE_RECORD_ID")
    return rows


def load_pilot_records(
    manifest: Path, records_path: Path, selection_path: Path | None = None,
) -> list[tuple[SourceRecord, dict]]:
    """Resolve the selected pilot back to verified original publisher rows.

    Check fingerprints, original locators and PDF hashes. The compact
    record carries its earlier audit and source corrections, not approval.
    With no saved pilot, a selection file starts a new 20-CV/20-job sample.
    New records still need source and external-disclosure checks.
    """
    root, sources = load_acquisition_manifest(manifest)
    rows = read_records(records_path)
    if not rows and selection_path is not None:
        selection = load_selection(selection_path)
        selected = {"resume": [], "job": []}
        for source in sources:
            if source["source_id"] not in {"resume-dataset", "djinni-jobs"}:
                continue
            verify_source_files(root, source)
            policy = selection["sources"][source["source_id"]]
            seen = set()
            for record in iter_source_records(root, source):
                category = str(record.values.get(policy["category_field"], ""))
                family = policy["categories"].get(category)
                fingerprint = content_fingerprint(record)
                if family is None or fingerprint in seen:
                    continue
                if record.kind == "resume" and not record.pdf_path:
                    continue
                seen.add(fingerprint)
                prepared = _selection_metadata(record, family, selection["version"])
                row = {key: prepared[key] for key in (
                    "record_id", "kind", "source_id", "source_record_id", "source_fingerprint",
                    "source_locator", "selection",
                )}
                row.update({"pdf_path": record.pdf_path,
                            "audit_index": len(selected[record.kind]) + 1,
                            "source_provenance": {key: source[key] for key in (
                                "source_url", "declared_license", "revision", "downloaded_at",
                            )}})
                selected[record.kind].append((record, row))
                if len(selected[record.kind]) == 20:
                    break
        if any(len(items) != 20 for items in selected.values()):
            raise ExtractionError("INSUFFICIENT_PILOT_SOURCE_RECORDS")
        return selected["resume"] + selected["job"]
    wanted = {(row["source_id"], row["source_record_id"]): row for row in rows}
    if not wanted or len(wanted) != len(rows):
        raise ExtractionError("INVALID_PILOT_IDENTITIES")
    found = {}
    for source in sources:
        keys = {key for key in wanted if key[0] == source["source_id"]}
        if not keys:
            continue
        verify_source_files(root, source)
        for record in iter_source_records(root, source):
            identity = (record.source_id, record.source_record_id)
            if identity not in keys:
                continue
            saved = wanted[identity]
            if content_fingerprint(record) != saved["source_fingerprint"] or (
                saved["source_locator"] != {"file": record.source_file, "row": record.row_number}
            ):
                raise ExtractionError("SOURCE_RECORD_CHANGED")
            if record.pdf_path != saved.get("pdf_path"):
                raise ExtractionError("SOURCE_PDF_CHANGED")
            if record.pdf_path and file_sha256(safe_dataset_path(root, record.pdf_path)) != (
                saved["pdf_sha256"]
            ):
                raise ExtractionError("SOURCE_PDF_CHANGED")
            found[identity] = record
            if keys.issubset(found):
                break
    if set(found) != set(wanted):
        raise ExtractionError("SOURCE_RECORD_MISSING")
    return [(found[key], row) for key, row in wanted.items()]


def iter_selected_records(
    manifest: Path, selection_path: Path,
) -> Iterator[tuple[SourceRecord, dict]]:
    """Stream category-selected records while retaining duplicate origins.

    A duplicate or conflicting publisher identity is a recorded outcome,
    never a second person or silently approved fact. No intermediate
    prepared/excluded/duplicate JSONL files are required.
    """
    root, sources = load_acquisition_manifest(manifest)
    selection = load_selection(selection_path)
    for source in sources:
        verify_source_files(root, source)
        policy = selection["sources"][source["source_id"]]
        identities, fingerprints = {}, {}
        for record in iter_source_records(root, source):
            category = str(record.values.get(policy["category_field"], ""))
            family = policy["categories"].get(category)
            if family is None:
                continue
            prepared = _selection_metadata(record, family, selection["version"])
            fingerprint = content_fingerprint(record)
            identity = source_identity(record)
            previous = identities.get(identity)
            row = {key: prepared[key] for key in (
                "record_id", "kind", "source_id", "source_record_id", "source_fingerprint",
                "source_locator",
            )}
            row["record_id"] = "draft_" + hashlib.sha256(
                json.dumps([record.source_id, record.source_record_id, fingerprint]).encode()
            ).hexdigest()[:24]
            row.update({"pdf_path": record.pdf_path, "selection": prepared["selection"],
                        "source_provenance": {key: source[key] for key in (
                            "source_url", "declared_license", "revision", "downloaded_at",
                        )}})
            if previous and previous != fingerprint:
                row["source_issue"] = "SOURCE_ID_CONFLICT"
            elif fingerprint in fingerprints:
                row["source_issue"] = "DUPLICATE"
                row["duplicate_of"] = fingerprints[fingerprint]
            identities.setdefault(identity, fingerprint)
            fingerprints.setdefault(fingerprint, row["record_id"])
            yield record, row


def verify_privacy_review(fields: dict, record_id: str, review: dict) -> tuple:
    """Bind an external-disclosure check to exact local text bytes.

    This check concerns identities sent to the provider, not individual
    human approval of professional facts. Changed text must be checked
    again; OCR differences cannot inherit native-text mask offsets.
    """
    if review.get("record_id") != record_id or review.get("status") != "PRIVACY_CHECKED":
        raise ExtractionError("PRIVACY_REVIEW_REQUIRED")
    hashes = {key: hashlib.sha256(value.encode()).hexdigest() for key, value in fields.items()}
    if hashes != review.get("original_field_sha256"):
        raise ExtractionError("PRIVACY_REVIEW_TEXT_CHANGED")
    literals, spans = review.get("identifier_literals"), review.get("identifier_spans")
    if not isinstance(literals, list) or any(not isinstance(value, str) for value in literals):
        raise ExtractionError("INVALID_PRIVACY_REVIEW")
    if not isinstance(spans, dict) or set(spans) - set(fields):
        raise ExtractionError("INVALID_PRIVACY_REVIEW")
    locations = {}
    for key, items in spans.items():
        if not isinstance(items, list):
            raise ExtractionError("INVALID_PRIVACY_REVIEW")
        locations[key] = []
        for item in items:
            start, end = item.get("start"), item.get("end")
            if type(start) is not int or type(end) is not int or not (
                0 <= start < end <= len(fields[key])
            ):
                raise ExtractionError("INVALID_PRIVACY_SPAN")
            locations[key].append((start, end))
    return tuple(literals), locations


@dataclass(frozen=True)
class DocumentText:
    """Keep exact page strings so evidence offsets remain reproducible."""

    pages: tuple[str, ...]
    methods: tuple[str, ...]
    comparisons: tuple[dict, ...] = ()


class OcrAdapter(Protocol):
    """Read one image page locally; never send its PDF to the fact provider."""

    def read_page(self, path: Path, page_number: int) -> str:
        """Return page text, using a one-based original page number."""
        ...


def usable_text(text: str) -> bool:
    """Reject empty/image text and conspicuous broken character decoding.

    This gate is conservative and does not replace visual layout review.
    A short page can still contain valid qualifications; no fixed page
    length is used to discard its evidence.
    """
    letters = sum(character.isalpha() for character in text)
    broken = text.count("\ufffd") + text.count("\x00")
    return letters >= 12 and broken / max(len(text), 1) <= 0.01


def compare_page_words(words: list[dict], ocr_lines: list[dict]) -> list[dict]:
    """Compare text only at the same page location.

    Remove whitespace differences for comparison, preserving punctuation
    and spelling. Identical wording in another role or page is not a
    duplicate. Keep disagreements and image-only lines for source checks;
    never replace dates or silently append unverified OCR wording.
    """
    result = []
    for line in ocr_lines:
        left, top, right, bottom = line["box"]
        nearby = [word for word in words if
                  left - 0.005 <= (word["box"][0] + word["box"][2]) / 2 <= right + 0.005
                  and top - 0.003 <= (word["box"][1] + word["box"][3]) / 2 <= bottom + 0.003]
        nearby.sort(key=lambda word: (round(word["box"][1], 2), word["box"][0]))
        embedded = " ".join(word["text"] for word in nearby)
        status = ("DUPLICATE" if embedded and _compact(embedded) == _compact(line["text"])
                  else "DIFFERENCE" if embedded else "OCR_ONLY")
        result.append({**line, "embedded_text": embedded, "status": status})
    return result


def _positioned_words(path: Path, number: int) -> list[dict]:
    import pdfplumber

    with pdfplumber.open(path) as document:
        page = document.pages[number - 1]
        return [{"text": word["text"],
                 "box": [word["x0"] / page.width, word["top"] / page.height,
                         word["x1"] / page.width, word["bottom"] / page.height]}
                for word in page.extract_words()]


def read_pdf(
    path: Path, ocr: OcrAdapter | None = None, mode: str = "auto",
) -> DocumentText:
    """Read native text before requesting OCR for unusable pages only.

    Preserve pages separately. Reject encrypted, empty or unreadable PDFs.
    ``compare`` also recognizes readable pages and compares positioned
    lines. Native text stays authoritative for those pages. Differences
    are retained alongside both readings, rather than merged by guessing.
    """
    from pypdf import PdfReader

    if mode not in {"auto", "compare"}:
        raise ExtractionError("INVALID_TEXT_MODE")
    if mode == "compare" and ocr is None:
        raise ExtractionError("OCR_REQUIRED")
    try:
        reader = PdfReader(path)
        if reader.is_encrypted or not reader.pages:
            raise ExtractionError("PDF_UNREADABLE")
        pages, methods, comparisons = [], [], []
        for number, page in enumerate(reader.pages, 1):
            text = page.extract_text() or ""
            method = "EMBEDDED"
            comparison = {"page": number, "embedded_text": text, "ocr": None, "lines": []}
            if mode == "compare":
                details = ocr.read_page_details(path, number)
                comparison.update({"ocr": details, "lines": compare_page_words(
                                       _positioned_words(path, number), details["lines"],
                                   )})
            if not usable_text(text):
                if ocr is None:
                    raise ExtractionError("OCR_REQUIRED")
                text = (comparison["ocr"]["text"] if comparison["ocr"] is not None
                        else ocr.read_page(path, number))
                method = "OCR"
                if not usable_text(text):
                    raise ExtractionError("OCR_TEXT_UNUSABLE")
            pages.append(text)
            methods.append(method)
            comparisons.append(comparison)
        return DocumentText(tuple(pages), tuple(methods), tuple(comparisons))
    except ExtractionError:
        raise
    except Exception:
        raise ExtractionError("PDF_UNREADABLE") from None


class TesseractOcr:
    """Render at 300 DPI and recognize text without network requests.

    ``tessdata`` contains the separately downloaded language models.
    Boxes use fractions of page width and height, with a top-left origin.
    OCR confidence describes recognition, not qualification accuracy.
    """

    def __init__(self, tessdata: Path, language: str = "eng", dpi: int = 300) -> None:
        self.tessdata = Path(tessdata)
        self.language = language
        self.dpi = dpi
        if dpi < 150 or any(
            not (self.tessdata / f"{name}.traineddata").is_file()
            for name in language.split("+")
        ):
            raise ExtractionError("OCR_CONFIGURATION_INVALID")

    @property
    def version(self) -> str:
        """Bind cached readings to the engine, DPI and language bytes."""
        import tesserocr

        digest = hashlib.sha256()
        for name in self.language.split("+"):
            digest.update((self.tessdata / f"{name}.traineddata").read_bytes())
        return f"{tesserocr.tesseract_version().splitlines()[0]}:{self.dpi}:{digest.hexdigest()}"

    def read_page_details(self, path: Path, page_number: int) -> dict:
        """Return original OCR text and positioned lines for one PDF page.

        Page numbers are one-based. Close native resources even on errors;
        do not retain rendered images or print recognized source content.
        """
        import pypdfium2 as pdfium
        import tesserocr

        try:
            with pdfium.PdfDocument(path) as document:
                page = document[page_number - 1]
                bitmap = page.render(scale=self.dpi / 72)
                try:
                    image = bitmap.to_pil().convert("RGB")
                    with tesserocr.PyTessBaseAPI(
                        path=str(self.tessdata), lang=self.language,
                        psm=tesserocr.PSM.AUTO,
                    ) as api:
                        api.SetImage(image)
                        text = api.GetUTF8Text()
                        iterator = api.GetIterator()
                        lines = []
                        if iterator is not None:
                            for line in tesserocr.iterate_level(iterator, tesserocr.RIL.TEXTLINE):
                                wording = line.GetUTF8Text(tesserocr.RIL.TEXTLINE) or ""
                                box = line.BoundingBox(tesserocr.RIL.TEXTLINE)
                                if not wording.strip() or box is None:
                                    continue
                                left, top, right, bottom = box
                                lines.append({
                                    "text": wording,
                                    "box": [left / image.width, top / image.height,
                                            right / image.width, bottom / image.height],
                                    "confidence": round(
                                        line.Confidence(tesserocr.RIL.TEXTLINE), 2,
                                    ),
                                })
                    image.close()
                finally:
                    bitmap.close()
                    page.close()
            return {"text": text, "lines": lines,
                    "engine": tesserocr.tesseract_version().splitlines()[0],
                    "language": self.language, "dpi": self.dpi}
        except ExtractionError:
            raise
        except Exception:
            raise ExtractionError("OCR_FAILED") from None

    def read_page(self, path: Path, page_number: int) -> str:
        """Return text through the shared optional OCR interface."""
        return self.read_page_details(path, page_number)["text"]


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ExtractionError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def _invalid_number(value: str) -> None:
    raise ExtractionError("NONFINITE_JSON_NUMBER")


def _whitespace_index(text: str) -> tuple[str, list[tuple[int, int]]]:
    normalized, offsets = [], []
    for match in re.finditer(r"\s+|\S", text):
        normalized.append(" " if match.group().isspace() else match.group())
        offsets.append(match.span())
    return "".join(normalized), offsets


def parse_reply(content: str) -> dict[str, Any]:
    """Reject duplicate keys, nonfinite values and nonobject responses."""
    try:
        result = json.loads(
            content, object_pairs_hook=_unique_object, parse_constant=_invalid_number,
        )
    except ExtractionError:
        raise
    except (ValueError, TypeError):
        raise ExtractionError("INVALID_JSON") from None
    if not isinstance(result, dict):
        raise ExtractionError("INVALID_JSON_OBJECT")
    return result


def _resolve_field(value: dict[str, Any], text: RedactedText) -> None:
    state, observed = value["status"], value["value"]
    evidence = value["evidence"]
    if state == "UNKNOWN":
        if observed is not None or evidence:
            raise ExtractionError("UNKNOWN_FIELD_HAS_VALUE")
        return
    if not evidence or (state == "OBSERVED" and (observed is None or not observed.strip())):
        raise ExtractionError("FIELD_HAS_NO_EVIDENCE")
    if state == "CONFLICT" and (observed is not None or len(evidence) < 2):
        raise ExtractionError("CONFLICT_WAS_RESOLVED_WITHOUT_REVIEW")
    if observed is not None and not any(
        re.sub(r"\s+", " ", observed).strip()
        in re.sub(r"\s+", " ", item["excerpt"]).strip() for item in evidence
    ):
        raise ExtractionError("VALUE_IS_NOT_SOURCE_WORDING")
    for item in evidence:
        key, excerpt = item["source_field"], item["excerpt"]
        if key not in text.fields or not excerpt.strip():
            raise ExtractionError("UNKNOWN_EVIDENCE_SOURCE")
        normalized, offsets = _whitespace_index(text.fields[key])
        needle = re.sub(r"\s+", " ", excerpt).strip()
        positions, cursor = [], 0
        while (cursor := normalized.find(needle, cursor)) >= 0:
            start, end = offsets[cursor][0], offsets[cursor + len(needle) - 1][1]
            overlaps = any(start < right and end > left for left, right in text.spans[key])
            if not overlaps:
                positions.append({"start": start, "end": end})
            cursor += 1
        if not positions:
            raise ExtractionError("EVIDENCE_DOES_NOT_MATCH_SOURCE")
        item["locations"] = positions
        item["ambiguous_location"] = len(positions) > 1
        item["provider_excerpt"] = excerpt
        item["excerpt"] = text.original[key][positions[0]["start"]:positions[0]["end"]]
        item["alignment"] = "EXACT" if item["excerpt"] == excerpt else "WHITESPACE_ONLY"
        if key.startswith("pdf_page_"):
            item["page"] = int(key.removeprefix("pdf_page_"))


def _walk_fields(value: Any, text: RedactedText) -> None:
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            _resolve_field(value, text)
        else:
            for child in value.values():
                _walk_fields(child, text)
    elif isinstance(value, list):
        for child in value:
            _walk_fields(child, text)


def validate_evidence_field(value: dict, schema: dict, text: RedactedText) -> dict:
    """Validate a supplementary source observation without scoring it.

    Coursework, work duties and publisher labels can be retained beside
    professional facts without claiming a completed degree or project.
    Apply the same field shape, literal wording and privacy-mask checks.
    """
    from jsonschema import Draft202012Validator

    if not isinstance(value, dict) or set(value) != {"value", "status", "evidence"}:
        raise ExtractionError("SOURCE_OBSERVATION_SCHEMA_MISMATCH")
    contract = {"$defs": schema["$defs"], "$ref": "#/$defs/field"}
    payload = {key: value[key] for key in ("value", "status", "evidence")}
    attached = {"locations", "ambiguous_location", "provider_excerpt", "page", "alignment"}
    try:
        payload["evidence"] = [{key: child for key, child in item.items() if key not in attached}
                               for item in payload["evidence"]]
    except (TypeError, AttributeError):
        raise ExtractionError("SOURCE_OBSERVATION_SCHEMA_MISMATCH") from None
    if next(Draft202012Validator(contract).iter_errors(payload), None) is not None:
        raise ExtractionError("SOURCE_OBSERVATION_SCHEMA_MISMATCH")
    result = deepcopy(payload)
    _resolve_field(result, text)
    return result


def _has_observation(value: Any) -> bool:
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            return value["status"] in {"OBSERVED", "CONFLICT"} and bool(value["evidence"])
        return any(_has_observation(child) for child in value.values())
    if isinstance(value, list):
        return any(_has_observation(child) for child in value)
    return False


def _has_conflict(value: Any) -> bool:
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            return value["status"] == "CONFLICT"
        return any(_has_conflict(child) for child in value.values())
    if isinstance(value, list):
        return any(_has_conflict(child) for child in value)
    return False


def _validate_structure(
    content: str, schema: dict[str, Any], kind: str, text: RedactedText,
) -> dict[str, Any]:
    from jsonschema import Draft202012Validator

    payload = parse_reply(content)
    if next(Draft202012Validator(schema).iter_errors(payload), None) is not None:
        raise ExtractionError("FACT_SCHEMA_MISMATCH")
    if payload["kind"] != kind:
        raise ExtractionError("FACT_KIND_MISMATCH")
    if kind == "job" and any(payload["professional"].values()):
        raise ExtractionError("JOB_REQUIREMENTS_ARE_NOT_CANDIDATE_FACTS")
    if kind != "job" and payload["requirements"]:
        raise ExtractionError("CANDIDATE_FACTS_ARE_NOT_JOB_REQUIREMENTS")
    for area, state in payload["field_status"].items():
        items = (
            [item for item in payload["requirements"] if item["criterionType"] == area.upper()]
            if kind == "job" else payload["professional"][area]
        )
        if not items and state != "UNKNOWN":
            raise ExtractionError("EMPTY_AREA_IS_NOT_CONFIRMED_ABSENCE")
        if items and state == "UNKNOWN":
            raise ExtractionError("OBSERVED_AREA_MARKED_UNKNOWN")
        if any(not _has_observation(item) for item in items):
            raise ExtractionError("EMPTY_ITEM_HAS_NO_SOURCE_OBSERVATION")
    return deepcopy(payload)


def validate_reply(
    content: str, schema: dict[str, Any], kind: str, text: RedactedText,
) -> dict[str, Any]:
    """Check fact shape and attach exact, privacy-safe source offsets.

    Whitespace alone may differ. Validation does not establish factual
    accuracy, completeness or readiness for numeric training.
    """
    result = _validate_structure(content, schema, kind, text)
    _walk_fields(result, text)
    return result


def validate_image_reply(content: str, schema: dict, kind: str, text: RedactedText) -> dict:
    """Keep image proposals pending a source check, including new readings.

    A visual reading need not exist in the PDF text layer. Retain such
    excerpts rather than fabricate offsets or treat the reply as evidence
    of its own accuracy. Every excerpt still needs a checked page image.
    """
    result = _validate_structure(content, schema, kind, text)
    if kind != "resume":
        raise ExtractionError("IMAGE_TRIAL_REQUIRES_CV")

    def check(value):
        if isinstance(value, dict):
            if set(value) == {"value", "status", "evidence"}:
                try:
                    checked = deepcopy(value)
                    _resolve_field(checked, text)
                    value.update(checked)
                except ExtractionError as error:
                    if error.code != "EVIDENCE_DOES_NOT_MATCH_SOURCE":
                        raise
                for item in value["evidence"]:
                    key = item["source_field"]
                    if key not in text.fields or not re.fullmatch(r"pdf_page_[1-9]\d*", key):
                        raise ExtractionError("UNKNOWN_EVIDENCE_SOURCE")
                    item["page"] = int(key.removeprefix("pdf_page_"))
                    item["image_check"] = "PENDING_VISUAL_CHECK"
            else:
                for child in value.values():
                    check(child)
        elif isinstance(value, list):
            for child in value:
                check(child)

    check(result)
    return result


def supported_partial_reply(
    content: str, schema: dict[str, Any], kind: str, text: RedactedText,
) -> tuple[dict[str, Any] | None, list[dict[str, str]]]:
    """Retain supported proposals from a failed reply without approving it.

    Structural failures return no partial facts. Unsupported fields become
    UNKNOWN and their paths/reasons are retained. Empty items are removed,
    never treated as confirmed absence. The complete provider response
    remains local evidence, and the record's failure status stays intact.
    """
    from jsonschema import Draft202012Validator

    try:
        payload = parse_reply(content)
    except ExtractionError:
        return None, [{"path": "$", "code": "INVALID_JSON"}]
    if next(Draft202012Validator(schema).iter_errors(payload), None) is not None:
        return None, [{"path": "$", "code": "FACT_SCHEMA_MISMATCH"}]
    if payload["kind"] != kind:
        return None, [{"path": "$", "code": "FACT_KIND_MISMATCH"}]
    held = []

    def prune(value: Any, path: str) -> None:
        if isinstance(value, dict):
            if set(value) == {"value", "status", "evidence"}:
                try:
                    _resolve_field(deepcopy(value), text)
                except ExtractionError as error:
                    held.append({"path": path, "code": error.code})
                    value.update({"value": None, "status": "UNKNOWN", "evidence": []})
            else:
                for key, child in value.items():
                    prune(child, path + "." + key)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                prune(child, f"{path}[{index}]")

    prune(payload, "$")
    for area, items in payload["professional"].items():
        payload["professional"][area] = [item for item in items if _has_observation(item)]
    payload["requirements"] = [item for item in payload["requirements"]
                               if item["wording"]["status"] != "UNKNOWN"]
    for area in payload["field_status"]:
        items = (
            [item for item in payload["requirements"] if item["criterionType"] == area.upper()]
            if kind == "job" else payload["professional"][area]
        )
        payload["field_status"][area] = (
            "CONFLICT" if _has_conflict(items) else "OBSERVED" if items else "UNKNOWN"
        )
    try:
        result = validate_reply(json.dumps(payload), schema, kind, text)
    except ExtractionError as error:
        return None, held + [{"path": "$", "code": error.code}]
    return result, held


def _source_spans(value: Any, fields: dict[str, str]) -> list[dict[str, Any]]:
    result = []
    if isinstance(value, dict):
        if {"source_field", "start", "end"}.issubset(value):
            excerpt = value.get("original")
            if excerpt is None:
                field = fields.get(value["source_field"])
                if field is None:
                    raise ExtractionError("AUDIT_SOURCE_FIELD_MISSING")
                excerpt = field[value["start"]:value["end"]]
            if not isinstance(excerpt, str) or not excerpt.strip():
                raise ExtractionError("AUDIT_EVIDENCE_IS_EMPTY")
            result.append({
                "source_field": value["source_field"], "start": value["start"],
                "end": value["end"], "excerpt": excerpt,
                **({"pdf_pages": value["pdf_pages"]} if "pdf_pages" in value else {}),
            })
        for child in value.values():
            result.extend(_source_spans(child, fields))
    elif isinstance(value, list):
        for child in value:
            result.extend(_source_spans(child, fields))
    unique = {(item["source_field"], item["start"], item["end"]): item for item in result}
    return list(unique.values())


def _excerpts(value: Any) -> list[dict[str, Any]]:
    result = []
    if isinstance(value, dict):
        if {"source_field", "excerpt", "locations"}.issubset(value):
            result.append(value)
        for child in value.values():
            result.extend(_excerpts(child))
    elif isinstance(value, list):
        for child in value:
            result.extend(_excerpts(child))
    return result


def _space(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def _pdf_locations(span: dict[str, Any], fields: dict[str, str]) -> list[dict[str, Any]]:
    needle, locations = _space(span["excerpt"]), []
    for page in span.get("pdf_pages", []):
        key = f"pdf_page_{page}"
        text, offsets = _whitespace_index(fields.get(key, ""))
        cursor = 0
        while (cursor := text.find(needle, cursor)) >= 0:
            end = cursor + len(needle)
            left_word = needle[0].isalnum() and cursor > 0 and text[cursor - 1].isalnum()
            right_word = needle[-1].isalnum() and end < len(text) and text[end].isalnum()
            if not left_word and not right_word:
                locations.append({"source_field": key, "start": offsets[cursor][0],
                                  "end": offsets[end - 1][1]})
            cursor += 1
    return locations


def _represented(
    span: dict[str, Any], evidence: list[dict[str, Any]], fields: dict[str, str],
) -> bool:
    for expected in span["extraction_locations"]:
        for item in evidence:
            key = item["source_field"]
            if key != expected["source_field"] or key not in fields:
                continue
            for position in item["locations"]:
                start, end = position["start"], position["end"]
                if not 0 <= start < end <= len(fields[key]):
                    continue
                if _space(fields[key][start:end]) != _space(item["excerpt"]):
                    continue
                if start <= expected["start"] and end >= expected["end"]:
                    return True
    return False


def check_source_evidence(source_check: dict, facts: dict, fields: dict, kind: str) -> dict:
    """Compare earlier checked source wording with current fact evidence.

    Missing coverage is a check item, not an extraction error or proof of
    missing qualifications. Keep page positions and source issues visible.
    Return details for the same record; do not write a separate report.
    """
    evidence = _excerpts(facts)
    expected = _source_spans(source_check.get("fields", {}), {}) if kind == "resume" else (
        _source_spans(source_check, fields)
    )
    for item in expected:
        item["extraction_locations"] = (_pdf_locations(item, fields) if kind == "resume" else
                                        [{key: item[key] for key in (
                                            "source_field", "start", "end",
                                        )}])
    missing = [item for item in expected if not _represented(item, evidence, fields)]
    return {"audited_unique_spans": len(expected),
            "represented_spans": len(expected) - len(missing),
            "unrepresented_spans": missing,
            "unlocated_audit_spans": [item for item in expected
                                      if not item["extraction_locations"]],
            "accepted_evidence_fields": len(evidence),
            "existing_source_issues": source_check.get("issues", [])}


def extraction_key(
    provider: FactExtractionProvider, request: ExtractionRequest,
) -> str:
    """Hash redacted fields, schema and implementation for local reuse."""
    encoded = json.dumps({
        "provider": provider.version, "redaction": REDACTION_VERSION,
        "schema": request.schema, "kind": request.kind, "fields": request.fields,
        **({"images": [[page.source_field, hashlib.sha256(page.png).hexdigest()]
                       for page in request.images], "image_prompt": "page-images-v1"}
           if request.images else {}),
    }, sort_keys=True, ensure_ascii=False, allow_nan=False).encode()
    return hashlib.sha256(encoded).hexdigest()


def extract_validated(
    provider: FactExtractionProvider, request: ExtractionRequest, text: RedactedText,
    *, validator=None,
) -> dict[str, Any]:
    """Produce a local draft after at most one retry and one repair.

    The shared retry budget spans both the initial and repair call.
    Preserve rejected replies locally for later diagnosis; return only
    safe stage codes in metadata. A validated draft remains unconfirmed.
    Failure never creates an empty approved profile or training sample.
    """
    started = time.monotonic()
    attempts, responses, errors = [], [], []
    retries, repairs = 0, 0
    current = request
    validate = validator or validate_reply
    while True:
        try:
            reply = provider.extract(current)
            attempts.append({"outcome": "RESPONSE", "model": reply.model, "usage": reply.usage})
            responses.append(reply.content)
        except ExtractionError as error:
            attempts.append({"outcome": error.code})
            errors.append(error.code)
            if error.retryable and retries == 0:
                retries += 1
                continue
            return _result(
                "FAILED_RETRYABLE" if error.retryable else "FAILED_FINAL",
                None, attempts, responses, errors, retries, repairs, started,
            )
        try:
            facts = validate(reply.content, current.schema, current.kind, text)
        except ExtractionError as error:
            errors.append(error.code)
            if repairs == 0:
                repairs += 1
                current = replace(request, repair_codes=(error.code,))
                continue
            failure = _result(
                "FAILED_FINAL", None, attempts, responses, errors, retries, repairs, started,
            )
            partial, held = supported_partial_reply(reply.content, current.schema,
                                                    current.kind, text)
            failure.update({"supported_partial_facts": partial, "held_fields": held})
            return failure
        return _result(
            "REVIEW_REQUIRED", facts, attempts, responses, errors, retries, repairs, started,
        )


def _result(status, facts, attempts, responses, errors, retries, repairs, started):
    return {
        "status": status, "facts": facts, "attempts": attempts,
        "provider_responses": responses, "validation_codes": errors,
        "transport_retries": retries, "schema_repairs": repairs,
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "human_confirmed": False, "training_ready": False,
    }


def fact_payload(value):
    """Remove locally attached locations before validating effective facts.

    Recheck current corrections rather than replaying a rejected provider
    reply. Original replies remain unchanged in the response store.
    """
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            return {key: fact_payload(value[key]) for key in ("value", "status", "evidence")}
        if {"source_field", "excerpt"}.issubset(value):
            attached = {"locations", "ambiguous_location", "provider_excerpt", "page", "alignment"}
            return {key: child for key, child in value.items() if key not in attached}
        return {key: fact_payload(child) for key, child in value.items()}
    if isinstance(value, list):
        return [fact_payload(child) for child in value]
    return value


def _field_states(value):
    states = Counter()
    if isinstance(value, dict):
        if {"value", "status", "evidence"}.issubset(value):
            states[value["status"]] += 1
        else:
            for child in value.values():
                states.update(_field_states(child))
    elif isinstance(value, list):
        for child in value:
            states.update(_field_states(child))
    return states


def _read_text(record, root, ocr, mode, previous):
    if record.kind == "resume":
        if not record.pdf_path:
            raise ExtractionError("ORIGINAL_PDF_MISSING")
        path = safe_dataset_path(root, record.pdf_path)
        pdf_hash = file_sha256(path)
        reading = previous.get("text_reading", {})
        if reading.get("pdf_sha256") == pdf_hash and reading.get("mode") == mode and (
            reading.get("ocr_language") == getattr(ocr, "language", None)
            and reading.get("ocr_version") == getattr(ocr, "version", None)
        ):
            return previous["original_text"], reading
        document = read_pdf(path, ocr, mode)
        fields = {f"pdf_page_{index}": value for index, value in enumerate(document.pages, 1)}
        return fields, {"mode": mode, "pdf_sha256": pdf_hash,
                        "ocr_language": getattr(ocr, "language", None),
                        "ocr_version": getattr(ocr, "version", None),
                        "page_methods": list(document.methods),
                        "comparisons": list(document.comparisons)}
    return {key: str(value) for key in EVIDENCE_COLUMNS[record.kind]
            if (value := record.values.get(key)) is not None and str(value).strip()}, {
                "page_methods": [], "comparisons": [], "mode": "SOURCE_FIELDS",
            }


def _load_responses(path):
    if not path.is_file():
        return {}
    with path.open(encoding="utf-8") as stream:
        return {row["cache_key"]: row for line in stream if line.strip()
                for row in [json.loads(line)]}


def _clean_record(record, previous, root, schema, schema_hash, provider, responses,
                  response_path, ocr, mode, reference_month, allow_api_calls):
    row = {**previous, "schema_version": "offline-clean-record-v1",
           "kind": record.kind, "source_id": record.source_id,
           "source_record_id": record.source_record_id,
           "source_locator": {"file": record.source_file, "row": record.row_number},
           "source_fingerprint": content_fingerprint(record), "pdf_path": record.pdf_path,
           "assessmentReferenceMonth": reference_month, "training_ready": False}
    row["checks"] = {"new_provider_calls": 0, "reused_effective_facts": False}
    if previous.get("source_issue"):
        row["status"] = previous["source_issue"]
        return row
    fields, reading = _read_text(record, root, ocr, mode, previous)
    row.update({"original_text": fields, "text_reading": reading})
    if record.kind == "resume":
        row["pdf_sha256"] = reading["pdf_sha256"]
    try:
        identifiers, spans = verify_privacy_review(
            fields, row["record_id"], row.get("privacy", {}),
        )
    except ExtractionError as error:
        row.update({"status": "HELD", "validation_codes": [error.code]})
        return row
    text = redact_fields(fields, identifiers, spans, privacy_checked=True)
    if sum(map(len, text.fields.values())) > 48000:
        raise ExtractionError("SOURCE_TOO_LONG_REQUIRES_SEGMENT_CHECK")
    row.update({"redacted_text": text.fields, "redaction_spans": text.spans})
    if previous.get("facts") is not None and previous.get("original_text") == fields and (
        previous.get("facts_schema_sha256") == schema_hash
    ):
        facts = validate_reply(json.dumps(fact_payload(previous["facts"])), schema,
                               record.kind, text)
        row["checks"]["reused_effective_facts"] = True
    else:
        request = ExtractionRequest(record.kind, text.fields, schema)
        key = extraction_key(provider, request)
        cached = responses.get(key)
        if cached is not None and cached["result"].get("status") == "REVIEW_REQUIRED":
            result = cached["result"]
            facts = validate_reply(result["provider_responses"][-1], schema, record.kind, text)
        elif not allow_api_calls:
            row.update({"status": "HELD", "validation_codes": [
                "PROVIDER_CALL_DISABLED_NO_VALIDATED_CACHE",
            ]})
            return row
        else:
            result = extract_validated(provider, request, text)
            stored = {"cache_key": key, "provider_version": provider.version, "result": result}
            with response_path.open("a", encoding="utf-8", newline="\n") as stream:
                write_json_line(stream, stored)
            responses[key] = stored
            row["checks"]["new_provider_calls"] = len(result["attempts"])
            if result["status"] != "REVIEW_REQUIRED":
                row.update({"status": result["status"], "facts": None,
                            "validation_codes": result["validation_codes"],
                            "supported_partial_facts": result.get("supported_partial_facts"),
                            "held_fields": result.get("held_fields", [])})
                return row
            facts = result["facts"]
        row.update({"reply_key": key, "provider_version": provider.version})
    row.update({"facts": facts, "facts_schema_sha256": schema_hash,
                "status": "VALIDATED", "validation_codes": []})
    for note in row.get("source_notes", []):
        note["wording"] = validate_evidence_field(note["wording"], schema, text)
    line_counts = Counter(line["status"] for page in reading["comparisons"]
                          for line in page["lines"])
    row["checks"].update({"schema_and_evidence": "PASS",
                         "field_counts": dict(_field_states(facts)),
                         "ocr_line_counts": dict(line_counts)})
    if row.get("source_check"):
        row["coverage"] = check_source_evidence(
            row["source_check"], {"facts": facts, "notes": row.get("source_notes", [])},
            fields, record.kind,
        )
    return row


def clean_records(
    records, manifest: Path, schema_path: Path, output: Path,
    provider: FactExtractionProvider, *, ocr: OcrAdapter | None = None,
    text_mode: str = "auto", reference_month: str = "2026-10",
    allow_api_calls: bool = True, limit: int | None = None, progress=None,
) -> dict:
    """Clean pilot or full source records into two local JSONL files.

    Records are ``(SourceRecord, previous_or_selection_metadata)`` pairs.
    Save current results in records.jsonl and original replies in a shared
    responses.jsonl. Repeated runs revalidate effective corrected facts;
    they do not recreate per-record drafts, receipts or Markdown reports.
    Output must be below datasets/clean. Errors retain original evidence
    and safe codes. A validated draft is not a numeric training sample.
    Full batches checkpoint each result so an interrupted run can resume.
    """
    if not re.fullmatch(r"\d{4}-(?:0[1-9]|1[0-2])", reference_month):
        raise ExtractionError("INVALID_REFERENCE_MONTH")
    if limit is not None and limit <= 0:
        raise ExtractionError("INVALID_BATCH_LIMIT")
    root, _ = load_acquisition_manifest(manifest)
    output = clean_output_path(root, output)
    output.mkdir(parents=True, exist_ok=True)
    path, response_path = output / "records.jsonl", output / "responses.jsonl"
    checkpoint = output / "records.pending.jsonl"
    previous = {row["record_id"]: row for row in read_records(path)}
    if checkpoint.is_file():
        previous.update({row["record_id"]: row for row in read_records(checkpoint)})
    responses = _load_responses(response_path)
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    schema_hash, counts = file_sha256(schema_path), Counter()
    selected = islice(records, limit) if limit is not None else records
    seen, conflicts = set(), set()
    with checkpoint.open("w", encoding="utf-8", newline="\n") as stream:
        for record, metadata in selected:
            record_id = metadata["record_id"]
            if record_id in seen:
                raise ExtractionError("DUPLICATE_RECORD_ID")
            seen.add(record_id)
            saved = previous.get(record_id, metadata)
            if metadata.get("source_issue"):
                saved = {**saved, "source_issue": metadata["source_issue"]}
                if metadata["source_issue"] == "SOURCE_ID_CONFLICT":
                    conflicts.add((record.source_id, record.source_record_id))
            if saved.get("source_fingerprint") != content_fingerprint(record):
                raise ExtractionError("SOURCE_RECORD_CHANGED")
            if saved.get("assessmentReferenceMonth", reference_month) != reference_month:
                raise ExtractionError("REFERENCE_MONTH_CHANGED")
            try:
                row = _clean_record(record, saved, root, schema, schema_hash, provider, responses,
                                    response_path, ocr, text_mode, reference_month,
                                    allow_api_calls)
            except ExtractionError as error:
                row = {**saved, "status": "FAILED_RETRYABLE" if error.retryable else "HELD",
                       "validation_codes": [error.code], "training_ready": False,
                       "checks": {"new_provider_calls": 0}}
            write_json_line(stream, row)
            stream.flush()
            counts[record.kind] += 1
            counts[row["status"]] += 1
            counts["new_provider_calls"] += row.get("checks", {}).get("new_provider_calls", 0)
            counts["reused_effective_facts"] += row.get("checks", {}).get(
                "reused_effective_facts", False,
            )
            if progress is not None:
                progress({"completed": len(seen), "kind": record.kind, "status": row["status"]})
            fatal = {"DEEPSEEK_HTTP_400", "DEEPSEEK_HTTP_401", "DEEPSEEK_HTTP_402",
                     "DEEPSEEK_HTTP_403", "DEEPSEEK_HTTP_404"}
            if fatal.intersection(row.get("validation_codes", [])):
                counts["stopped_on_provider_configuration"] = 1
                break
        for record_id, row in previous.items():
            if record_id not in seen:
                write_json_line(stream, row)
    if conflicts:
        rows = read_records(checkpoint)
        with checkpoint.open("w", encoding="utf-8", newline="\n") as stream:
            for row in rows:
                if (row["source_id"], row["source_record_id"]) in conflicts:
                    if row["status"] != "SOURCE_ID_CONFLICT" and row["record_id"] in seen:
                        counts[row["status"]] -= 1
                        counts["SOURCE_ID_CONFLICT"] += 1
                    row.update({"status": "SOURCE_ID_CONFLICT", "facts": None,
                                "validation_codes": ["SOURCE_ID_CONFLICT"]})
                write_json_line(stream, row)
    checkpoint.replace(path)
    return {"counts": dict(counts), "records": len(seen), "training_samples": 0}


TRIAL_VERSION = "page-images-v1"


IMAGE_CONTACT_PATTERNS = (*PATTERNS, re.compile(r"(?i)\bDSN\s*\d{3}[ .-]\d{4}\b"))


PRICE_SOURCE = "https://api-docs.deepseek.com/quick_start/pricing/"


USD_PER_MILLION = {
    "off_peak": {"hit": 0.003, "miss": 0.15, "output": 0.6},
    "peak": {"hit": 0.006, "miss": 0.3, "output": 1.2},
}


def _save(rows, path):
    checkpoint = path.with_name("records.pending.jsonl")
    if checkpoint.exists():
        raise ExtractionError("EXISTING_CLEANING_CHECKPOINT")
    with checkpoint.open("w", encoding="utf-8", newline="\n") as stream:
        for row in rows:
            write_json_line(stream, row)
    checkpoint.replace(path)


def _compact(value):
    return re.sub(r"\s+", "", value).casefold()


def _character_boxes(page, literal):
    chars, characters = [], []
    for char in page.chars:
        for letter in _compact(char["text"]):
            chars.append(char)
            characters.append(letter)
    text = "".join(characters)
    needle, cursor, boxes = _compact(literal), 0, []
    if not needle:
        return []
    while (cursor := text.find(needle, cursor)) >= 0:
        lines = {}
        for char in chars[cursor:cursor + len(needle)]:
            lines.setdefault(round(char["top"] / 3), []).append(char)
        for line in lines.values():
            boxes.append([min(char["x0"] for char in line) / page.width,
                          min(char["top"] for char in line) / page.height,
                          max(char["x1"] for char in line) / page.width,
                          max(char["bottom"] for char in line) / page.height])
        cursor += 1
    return boxes


def _page_masks(row, key, page, spans):
    masks, unmapped = [], []
    original = row["original_text"][key]
    number = int(key.removeprefix("pdf_page_"))
    ocr_lines = row["text_reading"]["comparisons"][number - 1]["ocr"]["lines"]
    for left, right in spans:
        literal = original[left:right].strip()
        if not literal:
            continue
        boxes = _character_boxes(page, literal)
        if not boxes:
            boxes = [line["box"] for line in ocr_lines
                     if _compact(literal) in _compact(line["text"])]
        if not boxes:
            unmapped.append({"start": left, "end": right})
        masks.extend({"box": box, "reason": "LOCAL_IDENTIFIER_CHECK"} for box in boxes)
    identifiers = row["privacy"]["identifier_literals"]
    for match in IMAGE_CONTACT_PATTERNS[-1].finditer(original):
        masks.extend({"box": box, "reason": "LOCAL_CONTACT_CHECK"}
                     for box in _character_boxes(page, match.group()))
    for line in ocr_lines:
        literals = [match.group() for pattern in IMAGE_CONTACT_PATTERNS
                    for match in pattern.finditer(line["text"])]
        literals.extend(value for value in identifiers
                        if _compact(value) and _compact(value) in _compact(line["text"]))
        for literal in literals:
            boxes = _character_boxes(page, literal) or [line["box"]]
            masks.extend({"box": box, "reason": "OCR_IDENTIFIER_CHECK"} for box in boxes)
    masks.extend(row.get("image_trial", {}).get("additional_masks", {}).get(key, []))
    return masks, unmapped


def prepare_images(records_path: Path, dataset_root: Path, *, dpi=200, progress=None) -> dict:
    """Render locally masked PNGs without making any provider requests.

    Keep masks and image hashes in existing records. Changed image bytes
    invalidate the visual check. Unlocated identifiers block extraction.
    Original PDFs, text, names and current facts remain untouched.
    """
    import pdfplumber
    import pypdfium2 as pdfium
    from PIL import ImageDraw

    rows = read_records(records_path)
    output = records_path.parent / "vision-pages"
    if not output.resolve().is_relative_to((dataset_root / "clean").resolve()):
        raise ExtractionError("IMAGE_OUTPUT_OUTSIDE_CLEAN_DATA")
    output.mkdir(exist_ok=True)
    count = 0
    for row in rows:
        if row["kind"] != "resume":
            continue
        started = time.monotonic()
        old = row.get("image_trial", {})
        original = row["original_text"]
        identifiers, spans = verify_privacy_review(original, row["record_id"], row["privacy"])
        redacted = redact_fields(original, identifiers, spans, privacy_checked=True)
        path = safe_dataset_path(dataset_root, row["pdf_path"])
        if file_sha256(path) != row["pdf_sha256"]:
            raise ExtractionError("SOURCE_PDF_CHANGED")
        pages = []
        with pdfplumber.open(path) as positioned, pdfium.PdfDocument(path) as document:
            if len(document) != len(original):
                raise ExtractionError("SOURCE_PAGE_COUNT_CHANGED")
            for number, page in enumerate(positioned.pages, 1):
                key = f"pdf_page_{number}"
                masks, unmapped = _page_masks(row, key, page, redacted.spans[key])
                native = document[number - 1]
                bitmap = native.render(scale=dpi / 72)
                image = bitmap.to_pil().convert("RGB")
                try:
                    draw = ImageDraw.Draw(image)
                    for mask in masks:
                        left, top, right, bottom = mask["box"]
                        padding = dpi / 36
                        draw.rectangle([max(0, int(left * image.width - padding)),
                                        max(0, int(top * image.height - padding)),
                                        min(image.width - 1, int(right * image.width + padding)),
                                        min(image.height - 1,
                                            int(bottom * image.height + padding))],
                                       fill="black")
                    destination = output / f"cv-{row['audit_index']:02}-page-{number}.png"
                    image.save(destination, format="PNG")
                    item = {"source_field": key, "path": destination.name,
                            "sha256": file_sha256(destination), "masks": masks,
                            "unmapped_identifier_spans": unmapped,
                            "width": image.width, "height": image.height}
                    previous = next((p for p in old.get("pages", [])
                                     if p["source_field"] == key), {})
                    if previous.get("sha256") == item["sha256"]:
                        item["visual_check"] = previous.get("visual_check")
                    pages.append(item)
                finally:
                    image.close()
                    bitmap.close()
                    native.close()
        row["image_trial"] = {**old, "version": TRIAL_VERSION, "dpi": dpi, "pages": pages,
                              "preparation_seconds": round(time.monotonic() - started, 3)}
        count += len(pages)
        if progress:
            progress({"prepared_cv": row["audit_index"], "pages": len(pages),
                      "unmapped_identifiers": sum(len(p["unmapped_identifier_spans"])
                                                  for p in pages)})
    _save(rows, records_path)
    return {"pages": count, "provider_calls": 0}


def checked_images(row: dict, directory: Path) -> tuple[PageImage, ...]:
    """Require a visual receipt for the exact bytes of every source page."""
    trial = row.get("image_trial", {})
    pages = trial.get("pages", [])
    if len(pages) != len(row["original_text"]) or trial.get("version") != TRIAL_VERSION:
        raise ExtractionError("IMAGE_PREPARATION_REQUIRED")
    if {page["source_field"] for page in pages} != set(row["original_text"]):
        raise ExtractionError("IMAGE_SOURCE_PAGES_DO_NOT_MATCH")
    images = []
    for page in pages:
        path = (directory / page["path"]).resolve()
        if not path.is_relative_to(directory.resolve()):
            raise ExtractionError("IMAGE_PATH_OUTSIDE_TRIAL")
        receipt = page.get("visual_check") or {}
        if page["unmapped_identifier_spans"] or receipt.get("status") != "PASS" or (
            receipt.get("sha256") != page["sha256"] or file_sha256(path) != page["sha256"]
        ):
            raise ExtractionError("IMAGE_VISUAL_PRIVACY_CHECK_REQUIRED")
        images.append(PageImage(page["source_field"], path.read_bytes()))
    return tuple(images)


def estimate_cost(result: dict) -> dict:
    """Estimate USD from reported usage; keep unknown charges explicit.

    Historical calls lack cache and billing-window details, so return a
    price range using current documented Flash rates. This is not an
    invoice. Transport failures can have unreported usage and charges.
    """
    lower, upper, missing, prompt, completion = 0.0, 0.0, 0, 0, 0
    for attempt in result.get("attempts", []):
        usage = attempt.get("usage", {})
        if "prompt_tokens" not in usage or "completion_tokens" not in usage:
            missing += 1
            continue
        inputs, outputs = usage["prompt_tokens"], usage["completion_tokens"]
        prompt += inputs
        completion += outputs
        hit = usage.get("prompt_cache_hit_tokens")
        miss = usage.get("prompt_cache_miss_tokens")
        if hit is None or miss is None:
            lower += inputs * USD_PER_MILLION["off_peak"]["hit"]
            upper += inputs * USD_PER_MILLION["peak"]["miss"]
        else:
            lower += hit * USD_PER_MILLION["off_peak"]["hit"]
            lower += miss * USD_PER_MILLION["off_peak"]["miss"]
            upper += hit * USD_PER_MILLION["peak"]["hit"]
            upper += miss * USD_PER_MILLION["peak"]["miss"]
        lower += outputs * USD_PER_MILLION["off_peak"]["output"]
        upper += outputs * USD_PER_MILLION["peak"]["output"]
    return {"usd_lower": round(lower / 1e6, 6), "usd_upper": round(upper / 1e6, 6),
            "prompt_tokens": prompt, "completion_tokens": completion,
            "unreported_usage_attempts": missing, "price_source": PRICE_SOURCE,
            "price_checked_at": "2026-10-14", "billing_receipt": False}


def observed_fields(facts: dict | None) -> list[dict]:
    """Flatten typed source observations without inferring absent values."""
    fields = []

    def visit(value, path):
        if isinstance(value, dict):
            if {"value", "status", "evidence"}.issubset(value):
                if value["status"] != "UNKNOWN":
                    fields.append({"path": path, **value})
            else:
                for key, child in value.items():
                    visit(child, f"{path}.{key}")
        elif isinstance(value, list):
            for index, child in enumerate(value):
                visit(child, f"{path}[{index}]")

    visit((facts or {}).get("professional", {}), "professional")
    return fields


def compare_fields(expected: dict, proposed: dict | None) -> dict:
    """List field differences for source review, not automatic accuracy.

    Array order is ignored. Matching wording is a review aid and does not
    establish that dates, employers or degrees are correctly associated.
    """
    gold, actual = observed_fields(expected), observed_fields(proposed)
    matched, missing, used = [], [], set()
    for field in gold:
        key = re.sub(r"\[\d+\]", "[]", field["path"])
        value = re.sub(r"\s+", " ", field["value"] or "").strip().casefold()
        index = next((i for i, item in enumerate(actual) if i not in used
                      and re.sub(r"\[\d+\]", "[]", item["path"]) == key
                      and item["status"] == field["status"]
                      and re.sub(r"\s+", " ", item["value"] or "").strip().casefold() == value),
                     None)
        if index is None:
            missing.append({key: field[key] for key in ("path", "value", "status")})
        else:
            used.add(index)
            matched.append({"expected": field["path"], "proposed": actual[index]["path"]})
    return {"expected_fields": len(gold), "proposed_fields": len(actual),
            "same_wording": matched, "expected_not_matched": missing,
            "additional_or_different": [
                {key: field[key] for key in ("path", "value", "status")}
                for i, field in enumerate(actual) if i not in used
            ],
            "review_status": "PENDING_SOURCE_CHECK"}


def run_image_trial(records_path: Path, schema_path: Path, provider, *, progress=None) -> dict:
    """Call checked images only; preserve the existing text baseline.

    Raw replies share responses.jsonl. Check all image receipts before
    the first request, cache by image bytes and checkpoint each CV.
    Save trial facts and field differences in the existing record.
    """
    rows = read_records(records_path)
    selected = [row for row in rows if row["kind"] == "resume"]
    if len(selected) != 20:
        raise ExtractionError("IMAGE_TRIAL_REQUIRES_20_CVS")
    images = {row["record_id"]: checked_images(row, records_path.parent / "vision-pages")
              for row in selected}
    response_path = records_path.with_name("responses.jsonl")
    replies = _load_responses(response_path)
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    calls = 0
    for row in selected:
        identifiers, spans = verify_privacy_review(row["original_text"], row["record_id"],
                                                  row["privacy"])
        text = redact_fields(row["original_text"], identifiers, spans, privacy_checked=True)
        request = ExtractionRequest("resume", {}, schema, images=images[row["record_id"]])
        key = extraction_key(provider, request)
        trial = row["image_trial"]
        reference_hash = hashlib.sha256(json.dumps(
            row["facts"], sort_keys=True, ensure_ascii=False,
        ).encode()).hexdigest()
        source_check = trial.get("source_check", {})
        if source_check.get("response_key") != key or source_check.get(
            "reference_facts_sha256"
        ) != reference_hash:
            trial.pop("source_check", None)
        stored = replies.get(key)
        if stored is None:
            started = datetime.now(timezone.utc).isoformat()
            result = extract_validated(provider, request, text, validator=validate_image_reply)
            result["started_utc"] = started
            stored = {"cache_key": key, "experiment": TRIAL_VERSION,
                      "provider_version": provider.version, "result": result}
            with response_path.open("a", encoding="utf-8", newline="\n") as stream:
                write_json_line(stream, stored)
            replies[key] = stored
            calls += len(result["attempts"])
        result = stored["result"]
        baseline = replies.get(row.get("reply_key"), {}).get("result", {})
        original_reply = None
        if baseline.get("provider_responses"):
            try:
                original_reply = parse_reply(baseline["provider_responses"][-1])
            except ExtractionError:
                pass
        image_reply = result.get("facts")
        if image_reply is None and result.get("provider_responses"):
            try:
                image_reply = parse_reply(result["provider_responses"][-1])
            except ExtractionError:
                pass
        trial.update({"response_key": key, "provider_version": provider.version,
                      "status": result["status"], "facts": result.get("facts"),
                      "validation_codes": result.get("validation_codes", []),
                      "measurement": {"elapsed_seconds": result["elapsed_seconds"],
                                      "attempts": len(result["attempts"]),
                                      "cost": estimate_cost(result)},
                      "text_baseline_measurement": {
                          "elapsed_seconds": baseline.get("elapsed_seconds"),
                          "attempts": len(baseline.get("attempts", [])),
                          "cost": estimate_cost(baseline), "historical_run": True},
                      "comparison": compare_fields(row["facts"], result.get("facts")),
                      "raw_image_comparison": compare_fields(row["facts"], image_reply),
                      "original_text_comparison": compare_fields(row["facts"], original_reply)})
        if trial.get("source_check"):
            for name in ("comparison", "raw_image_comparison", "original_text_comparison"):
                trial[name]["review_status"] = "SOURCE_COMPARED_WITH_NOTES"
        _save(rows, records_path)
        if progress:
            progress({"completed_cv": row["audit_index"], "status": trial["status"],
                      "elapsed_seconds": result["elapsed_seconds"]})
        if any(code in result["validation_codes"] for code in (
            "DEEPSEEK_HTTP_400", "DEEPSEEK_HTTP_401", "DEEPSEEK_HTTP_402", "DEEPSEEK_HTTP_403",
        )):
            break
    return {"cvs": len(selected), "new_provider_calls": calls}
