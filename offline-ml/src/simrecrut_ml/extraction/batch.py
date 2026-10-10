"""Extract the audited pilot into ignored, resumable evidence drafts."""

import json
import hashlib
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterator

from ..data.adapters import (
    file_sha256, iter_source_records, load_acquisition_manifest, safe_dataset_path,
    verify_source_files,
)
from ..data.models import SourceRecord
from ..data.normalization import EVIDENCE_COLUMNS, content_fingerprint
from ..data.pipeline import clean_output_path
from .models import ExtractionError, ExtractionRequest, FactExtractionProvider
from .pipeline import extract_validated, extraction_key
from .redaction import REDACTION_VERSION, redact_fields
from .text import read_pdf
from .validation import validate_reply


def _rows(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def _write(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(text + "\n")


def _cached_reply(path: Path) -> dict[str, Any]:
    stored = json.loads(path.read_text(encoding="utf-8"))
    keys = {
        "status", "facts", "attempts", "provider_responses", "validation_codes",
        "transport_retries", "schema_repairs", "elapsed_seconds", "human_confirmed",
        "training_ready",
    }
    # A content cache may be shared by records; source identity stays local.
    return {**{key: value for key, value in stored.items() if key in keys},
            "human_confirmed": False, "training_ready": False}


def audited_records(
    manifest_path: Path, audit_dir: Path,
) -> Iterator[tuple[SourceRecord, dict[str, Any]]]:
    """Read original rows for accepted pilot replacements and all 20 jobs.

    Verify acquisition bytes, publisher IDs and audit fingerprints before
    any external request. Acceptance of pilot scope does not grant fact
    approval. Yield selected CVs first, then jobs, in stable audit order.
    """
    root, sources = load_acquisition_manifest(manifest_path)
    audit = clean_output_path(root, audit_dir)
    cvs = [row for row in _rows(audit / "evidence/cv-review.jsonl")
           if row["selection"]["provisionally_usable_it_cv"]]
    jobs = _rows(audit / "evidence/job-review.jsonl")
    wanted = {}
    for kind, rows in (("resume", cvs), ("job", jobs)):
        for row in rows:
            source = row["source"] if kind == "resume" else row
            identity = (source["source_id"], str(source["source_record_id"]))
            if identity in wanted:
                raise ExtractionError("DUPLICATE_AUDIT_IDENTITY")
            wanted[identity] = row
    found = {}
    for source in sources:
        selected = {key for key in wanted if key[0] == source["source_id"]}
        if not selected:
            continue
        verify_source_files(root, source)
        for record in iter_source_records(root, source):
            key = (record.source_id, record.source_record_id)
            if key not in selected:
                continue
            audit_row = wanted[key]
            if content_fingerprint(record) != audit_row["source_fingerprint"]:
                raise ExtractionError("AUDITED_SOURCE_CHANGED")
            if record.kind == "resume":
                expected = audit_row["source"]
                if record.pdf_path != expected["pdf_path"]:
                    raise ExtractionError("AUDITED_PDF_CHANGED")
                if file_sha256(safe_dataset_path(root, record.pdf_path)) != expected["pdf_sha256"]:
                    raise ExtractionError("AUDITED_PDF_CHANGED")
            found[key] = record
            if selected.issubset(found):
                break
    if set(found) != set(wanted):
        raise ExtractionError("AUDITED_SOURCE_MISSING")
    for key, row in wanted.items():
        yield found[key], row


def source_text_fields(record: SourceRecord, root: Path) -> tuple[dict[str, str], dict]:
    """Read exact professional fields and native PDF page metadata locally."""
    if record.kind == "resume":
        if not record.pdf_path:
            raise ExtractionError("ORIGINAL_PDF_MISSING")
        document = read_pdf(safe_dataset_path(root, record.pdf_path))
        fields = {f"pdf_page_{number}": text for number, text in enumerate(document.pages, 1)}
        return fields, {"page_methods": list(document.methods)}
    fields = {
        key: str(value) for key in EVIDENCE_COLUMNS[record.kind]
        if (value := record.values.get(key)) is not None and str(value).strip()
    }
    return fields, {"page_methods": []}


def verify_privacy_review(
    fields: dict[str, str], record_id: str, review: dict[str, Any],
) -> tuple[tuple[str, ...], dict[str, list[tuple[int, int]]]]:
    """Require a reviewed privacy decision bound to every exact input field.

    This decision concerns external disclosure only, not factual truth.
    Changed bytes, omitted fields, held records or missing reviews block
    the provider call. Preserve explicit mask spans in local receipts.
    """
    if review.get("record_id") != record_id or review.get("status") != "PRIVACY_CHECKED":
        raise ExtractionError("PRIVACY_REVIEW_REQUIRED")
    hashes = {key: hashlib.sha256(value.encode()).hexdigest() for key, value in fields.items()}
    if hashes != review.get("original_field_sha256"):
        raise ExtractionError("PRIVACY_REVIEW_TEXT_CHANGED")
    literals = review.get("identifier_literals")
    spans = review.get("identifier_spans")
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
            if type(start) is not int or type(end) is not int:
                raise ExtractionError("INVALID_PRIVACY_SPAN")
            if not 0 <= start < end <= len(fields[key]):
                raise ExtractionError("INVALID_PRIVACY_SPAN")
            locations[key].append((start, end))
    return tuple(literals), locations


def extract_audited_batch(
    manifest_path: Path,
    audit_dir: Path,
    schema_path: Path,
    output_dir: Path,
    provider: FactExtractionProvider,
    limit: int | None = None,
    progress: Callable[[dict[str, Any]], None] | None = None,
    privacy_review_path: Path | None = None,
) -> dict[str, Any]:
    """Write local unconfirmed drafts, original evidence and call receipts.

    Only redacted professional fields cross the provider boundary.
    Revalidate successful cached replies against the current original;
    failed runs are preserved separately and may be retried explicitly.
    Stop after a permanent vendor configuration failure. The returned
    summary contains counts and stage codes, never personal source text.
    """
    root, _ = load_acquisition_manifest(manifest_path)
    output = clean_output_path(root, output_dir)
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    privacy_path = privacy_review_path or audit_dir / "verification/extraction-privacy-review.json"
    if not privacy_path.is_file():
        raise ExtractionError("PRIVACY_REVIEW_REQUIRED")
    privacy = json.loads(privacy_path.read_text(encoding="utf-8"))
    if privacy.get("schema_version") != "extraction-privacy-review-v1":
        raise ExtractionError("INVALID_PRIVACY_REVIEW")
    reviews = {row["record_id"]: row for row in privacy["records"]}
    if len(reviews) != len(privacy["records"]):
        raise ExtractionError("DUPLICATE_PRIVACY_REVIEW")
    if limit is not None and limit <= 0:
        raise ExtractionError("INVALID_BATCH_LIMIT")
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    summary = {
        "schema_version": "extraction-batch-v1", "run_id": run_id,
        "specification_revision": 221, "provider_version": provider.version,
        "fact_schema_sha256": file_sha256(schema_path),
        "redaction_version": REDACTION_VERSION, "counts": {},
        "privacy_review_sha256": file_sha256(privacy_path),
        "training_samples": 0, "human_approved": 0, "records": [],
    }
    counts = Counter()
    for number, (record, audit) in enumerate(audited_records(manifest_path, audit_dir), 1):
        if limit is not None and number > limit:
            break
        details = {
            "record_id": audit["record_id"], "audit_index": audit["audit_index"],
            "kind": record.kind, "source_fingerprint": content_fingerprint(record),
            "source_id": record.source_id, "source_record_id": record.source_record_id,
            "source_locator": {"file": record.source_file, "row": record.row_number},
            "pdf_path": record.pdf_path,
            "existing_audit_issues": audit.get("issues", []),
            "status": "FAILED_FINAL", "validation_codes": [],
        }
        cache_hit = False
        try:
            fields, text_meta = source_text_fields(record, root)
            if sum(map(len, fields.values())) > 48000:
                raise ExtractionError("SOURCE_TOO_LONG_REQUIRES_SEGMENT_REVIEW")
            reviewed_identifiers, reviewed_spans = verify_privacy_review(
                fields, audit["record_id"], reviews.get(audit["record_id"], {}),
            )
            identifiers = reviewed_identifiers + tuple(
                str(record.values[key]) for key in (
                    "Name", "Full Name", "Candidate Name", "First Name", "Last Name",
                ) if record.values.get(key)
            )
            redacted = redact_fields(fields, identifiers, reviewed_spans, privacy_checked=True)
            request = ExtractionRequest(record.kind, redacted.fields, schema)
            key = extraction_key(provider, request)
            cache = output / "cache" / f"{key}.json"
            details.update({
                "original_text": fields, "redacted_text": redacted.fields,
                "redaction_spans": redacted.spans, "text_reading": text_meta,
                "privacy_review_sha256": file_sha256(privacy_path), "cache_key": key,
            })
            if cache.is_file():
                result = _cached_reply(cache)
                if result["status"] != "REVIEW_REQUIRED":
                    raise ExtractionError("INVALID_SUCCESS_CACHE")
                result["facts"] = validate_reply(
                    result["provider_responses"][-1], schema, record.kind, redacted,
                )
                cache_hit = True
            else:
                result = extract_validated(provider, request, redacted)
                if result["status"] == "REVIEW_REQUIRED":
                    _write(cache, result)
                else:
                    _write(output / "failures" / f"{key}-{run_id}.json", {**details, **result})
            details.update(result)
            counts["cache_hits" if cache_hit else "new_extractions"] += 1
        except ExtractionError as error:
            details["validation_codes"] = [error.code]
            details["status"] = "FAILED_RETRYABLE" if error.retryable else "FAILED_FINAL"
            _write(output / "failures" / f"{record.kind}-{number}-{run_id}.json", details)
        _write(output / "drafts" / f"{record.kind}-{audit['audit_index']}-{run_id}.json", details)
        counts[details["status"]] += 1
        counts[record.kind] += 1
        summary["records"].append({
            key: details.get(key) for key in (
                "record_id", "kind", "audit_index", "status", "cache_key", "validation_codes",
            )
        })
        if progress is not None:
            progress({
                "completed": number, "kind": record.kind, "status": details["status"],
                "cache_hit": cache_hit, "stage_codes": details["validation_codes"],
            })
        fatal = {"DEEPSEEK_HTTP_400", "DEEPSEEK_HTTP_401", "DEEPSEEK_HTTP_402",
                 "DEEPSEEK_HTTP_403", "DEEPSEEK_HTTP_404"}
        if fatal.intersection(details["validation_codes"]):
            summary["stopped_on_provider_configuration"] = True
            break
    summary["counts"] = dict(counts)
    _write(output / "runs" / f"{run_id}.json", summary)
    return {key: value for key, value in summary.items() if key != "records"}
