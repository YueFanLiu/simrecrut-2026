"""Revalidate saved responses locally without making another provider call."""

import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ..data.adapters import file_sha256, load_acquisition_manifest
from ..data.pipeline import clean_output_path
from ..data.normalization import content_fingerprint
from .batch import audited_records, source_text_fields, verify_privacy_review
from .models import ExtractionError
from .redaction import redact_fields
from .validation import supported_partial_reply, validate_reply


def _write_new(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n")


def recheck_saved_batch(
    manifest_path: Path, audit_dir: Path, run_path: Path, schema_path: Path,
) -> dict[str, Any]:
    """Preserve failed replies and recover supported drafts with zero API calls.

    Verify the current original rows, PDF bytes and hash-bound privacy
    receipt again. Reject changed input or masking. Save a new derived
    receipt; never rewrite the history of the original API run. Partial
    facts remain attached to failed records and cannot enter training.
    """
    root, _ = load_acquisition_manifest(manifest_path)
    run_path = clean_output_path(root, run_path)
    run = json.loads(run_path.read_text(encoding="utf-8"))
    if run.get("schema_version") != "extraction-batch-v1":
        raise ExtractionError("UNSUPPORTED_EXTRACTION_RUN")
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    schema_hash = file_sha256(schema_path)
    if run.get("fact_schema_sha256") != schema_hash:
        raise ExtractionError("SAVED_EXTRACTION_SCHEMA_CHANGED")
    privacy_path = audit_dir / "verification/extraction-privacy-review.json"
    privacy = json.loads(privacy_path.read_text(encoding="utf-8"))
    if privacy.get("schema_version") != "extraction-privacy-review-v1":
        raise ExtractionError("INVALID_PRIVACY_REVIEW")
    reviews = {row["record_id"]: row for row in privacy["records"]}
    originals = {row["record_id"]: (record, row)
                 for record, row in audited_records(manifest_path, audit_dir)}
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = run_path.parent.parent
    rows, counts = [], Counter()
    for entry in run["records"]:
        old = output / "drafts" / f"{entry['kind']}-{entry['audit_index']}-{run['run_id']}.json"
        saved = json.loads(old.read_text(encoding="utf-8"))
        record, audit = originals[entry["record_id"]]
        expected_identity = {
            "record_id": audit["record_id"], "kind": record.kind,
            "audit_index": audit["audit_index"], "source_id": record.source_id,
            "source_record_id": record.source_record_id,
            "source_fingerprint": content_fingerprint(record),
            "source_locator": {"file": record.source_file, "row": record.row_number},
            "pdf_path": record.pdf_path,
        }
        if any(saved.get(key) != value for key, value in expected_identity.items()):
            raise ExtractionError("SAVED_EXTRACTION_SOURCE_IDENTITY_MISMATCH")
        if any(entry.get(key) != expected_identity[key] for key in (
            "record_id", "kind", "audit_index",
        )):
            raise ExtractionError("SAVED_EXTRACTION_SOURCE_IDENTITY_MISMATCH")
        fields, _ = source_text_fields(record, root)
        identifiers, spans = verify_privacy_review(fields, entry["record_id"],
                                                  reviews.get(entry["record_id"], {}))
        redacted = redact_fields(fields, identifiers, spans, privacy_checked=True)
        if fields != saved.get("original_text") or redacted.fields != saved.get("redacted_text"):
            raise ExtractionError("SAVED_EXTRACTION_INPUT_CHANGED")
        responses = saved.get("provider_responses", [])
        if responses:
            try:
                facts = validate_reply(responses[-1], schema, record.kind, redacted)
            except ExtractionError as error:
                partial, held = supported_partial_reply(responses[-1], schema, record.kind,
                                                        redacted)
                saved.update({
                    "status": ("FAILED_RETRYABLE" if saved["status"] == "FAILED_RETRYABLE"
                               else "FAILED_FINAL"), "facts": None,
                    "supported_partial_facts": partial, "held_fields": held,
                    "current_validation_codes": [error.code],
                })
                if partial is not None:
                    counts["failed_records_with_supported_partial_facts"] += 1
                counts["held_fields"] += len(held)
            else:
                saved.update({"status": "REVIEW_REQUIRED", "facts": facts,
                              "current_validation_codes": [], "held_fields": []})
                cache = output / "cache" / f"{saved['cache_key']}.json"
                if not cache.exists():
                    keys = {
                        "status", "facts", "attempts", "provider_responses", "validation_codes",
                        "transport_retries", "schema_repairs", "elapsed_seconds",
                        "human_confirmed", "training_ready",
                    }
                    _write_new(cache, {
                        **{key: value for key, value in saved.items() if key in keys},
                        "human_confirmed": False, "training_ready": False,
                    })
        saved.update({"locally_revalidated": True, "new_provider_calls": 0,
                      "original_run_id": run["run_id"], "human_confirmed": False,
                      "training_ready": False,
                      "current_fact_schema_sha256": schema_hash,
                      "current_privacy_review_sha256": file_sha256(privacy_path),
                      "validator_sha256": file_sha256(
                          Path(__file__).with_name("validation.py"),
                      )})
        draft_name = f"{entry['kind']}-{entry['audit_index']}-{run_id}.json"
        _write_new(output / "drafts" / draft_name, saved)
        rows.append({**entry, "status": saved["status"],
                     "validation_codes": saved.get("current_validation_codes", [])})
        counts[saved["status"]] += 1
        counts[record.kind] += 1
    result = {
        **run, "run_id": run_id, "records": rows, "counts": dict(counts),
        "original_run_id": run["run_id"], "derivation": "LOCAL_REVALIDATION_NO_PROVIDER_CALLS",
        "new_provider_calls": 0, "human_approved": 0, "training_samples": 0,
        "current_fact_schema_sha256": schema_hash,
        "current_privacy_review_sha256": file_sha256(privacy_path),
        "validator_sha256": file_sha256(Path(__file__).with_name("validation.py")),
    }
    _write_new(output / "runs" / f"{run_id}.json", result)
    return {key: value for key, value in result.items() if key != "records"}
