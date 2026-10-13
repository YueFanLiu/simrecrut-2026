"""Apply explicit source corrections without changing extraction history."""

import json
import re
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from ..data.adapters import file_sha256, load_acquisition_manifest
from ..data.normalization import content_fingerprint
from ..data.pipeline import clean_output_path
from .batch import audited_records, source_text_fields, verify_privacy_review
from .models import ExtractionError
from .redaction import RedactedText, redact_fields
from .validation import parse_reply, validate_reply


def _location(payload: dict, path: str) -> tuple[Any, str | int]:
    if not isinstance(path, str) or not re.fullmatch(
        r"\$(?:\.[A-Za-z_][A-Za-z_0-9]*|\[\d+\])+", path,
    ):
        raise ExtractionError("INVALID_REPAIR_PATH")
    tokens = [word if word else int(index)
              for word, index in re.findall(r"\.([A-Za-z_][A-Za-z_0-9]*)|\[(\d+)\]", path)]
    if tokens[0] not in {"professional", "requirements"}:
        raise ExtractionError("REPAIR_PATH_OUTSIDE_FACTS")
    current = payload
    try:
        for token in tokens[:-1]:
            current = current[token]
        current[tokens[-1]]
    except (KeyError, IndexError, TypeError):
        raise ExtractionError("REPAIR_PATH_NOT_FOUND") from None
    return current, tokens[-1]


def _states(value: Any) -> set[str]:
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            return {value["status"]} if value["evidence"] else set()
        return set().union(*(_states(child) for child in value.values()))
    if isinstance(value, list):
        return set().union(*(_states(child) for child in value))
    return set()


def repair_fact_payload(
    content: str, actions: list[dict], schema: dict, kind: str, text: RedactedText,
) -> tuple[dict, list[dict], list[dict]]:
    """Validate explicit field corrections and retain unresolved field paths.

    Each action binds to the exact previous value and explains its reason.
    Only evidence fields or a downgrade to UNRESOLVED source use may change.
    A conflict cannot be resolved by a repair. Recompute area summaries from
    retained observations; then run the unchanged schema/evidence validator.
    Return validated facts, change receipts and unresolved corrections.
    Neither schema validity nor a literal repair grants human approval.
    """
    payload = parse_reply(content)
    changes, unresolved, seen = [], [], set()
    for action in actions:
        path = action.get("path")
        if not isinstance(path, str) or path in seen:
            raise ExtractionError("REPAIR_PATH_DUPLICATED_OR_INVALID")
        seen.add(path)
        reason = action.get("rationale")
        if not isinstance(reason, str) or not reason.strip():
            raise ExtractionError("REPAIR_RATIONALE_REQUIRED")
        parent, key = _location(payload, path)
        before, after = parent[key], action.get("replacement")
        if before != action.get("before"):
            raise ExtractionError("REPAIR_PREVIOUS_VALUE_CHANGED")
        if isinstance(before, dict) and set(before) == {"value", "status", "evidence"}:
            if not isinstance(after, dict) or set(after) != set(before):
                raise ExtractionError("REPAIR_MUST_REPLACE_EVIDENCE_FIELD")
            if before["status"] == "CONFLICT" and after != before:
                raise ExtractionError("REPAIR_CANNOT_RESOLVE_CONFLICT")
            if after.get("status") == "UNKNOWN":
                unresolved.append({"path": path, "rationale": reason})
        elif key != "sourceUse" or after != "UNRESOLVED" or not isinstance(before, str):
            raise ExtractionError("REPAIR_CANNOT_ASSIGN_CODES_OR_FLAGS")
        parent[key] = deepcopy(after)
        changes.append({"path": path, "before": before, "after": deepcopy(after),
                        "rationale": reason})
    for area, before in payload["field_status"].items():
        items = ([item for item in payload["requirements"]
                  if item["criterionType"] == area.upper()]
                 if kind == "job" else payload["professional"][area])
        states = _states(items)
        after = ("CONFLICT" if before == "CONFLICT" or "CONFLICT" in states
                 else "OBSERVED" if states else "UNKNOWN")
        payload["field_status"][area] = after
        if before != after:
            changes.append({"path": f"$.field_status.{area}", "before": before, "after": after,
                            "rationale": "Derive the summary from retained evidence fields."})
    facts = validate_reply(json.dumps(payload, allow_nan=False), schema, kind, text)
    return facts, changes, unresolved


def _write_new(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n")


def repair_saved_batch(
    manifest_path: Path, audit_dir: Path, run_path: Path, schema_path: Path, plan_path: Path,
) -> dict:
    """Save a derived batch from a hash-bound local correction plan.

    Verify current publisher rows, PDF bytes, source identities and privacy
    masks before applying corrections. Prepare and validate all drafts before
    writing new files. Preserve original replies, caches and failure records;
    make no API calls. Explicit unknowns remain review tasks, not confirmed
    absence. Return only counts and non-content provenance.
    """
    root, _ = load_acquisition_manifest(manifest_path)
    audit = clean_output_path(root, audit_dir)
    run_path = clean_output_path(root, run_path)
    plan_path = clean_output_path(root, plan_path)
    run = json.loads(run_path.read_text(encoding="utf-8"))
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    privacy_path = audit / "verification/extraction-privacy-review.json"
    schema_hash, privacy_hash = file_sha256(schema_path), file_sha256(privacy_path)
    validator_hash = file_sha256(Path(__file__).with_name("validation.py"))
    if run.get("schema_version") != "extraction-batch-v1" or (
        plan.get("schema_version") != "extraction-repair-plan-v1"
        or plan.get("run_id") != run["run_id"]
        or plan.get("run_sha256") != file_sha256(run_path)
        or plan.get("fact_schema_sha256") != schema_hash
        or run.get("fact_schema_sha256") != schema_hash
        or plan.get("privacy_review_sha256") != privacy_hash
        or run.get("current_privacy_review_sha256", run.get("privacy_review_sha256"))
        != privacy_hash
    ):
        raise ExtractionError("REPAIR_INPUT_BINDING_MISMATCH")
    entries = {row["record_id"]: row for row in plan["records"]}
    run_ids = {row["record_id"] for row in run["records"]}
    if len(entries) != len(plan["records"]) or len(run_ids) != len(run["records"]):
        raise ExtractionError("REPAIR_DUPLICATE_RECORD")
    if set(entries) != run_ids:
        raise ExtractionError("REPAIR_PLAN_RECORDS_INCOMPLETE")
    privacy = json.loads(privacy_path.read_text(encoding="utf-8"))
    reviews = {row["record_id"]: row for row in privacy["records"]}
    if privacy.get("schema_version") != "extraction-privacy-review-v1" or (
        len(reviews) != len(privacy["records"])
    ):
        raise ExtractionError("INVALID_PRIVACY_REVIEW")
    current = {row["record_id"]: (record, row)
               for record, row in audited_records(manifest_path, audit)}
    output, prepared, counts = run_path.parent.parent, [], Counter()
    for entry in run["records"]:
        old = output / "drafts" / f"{entry['kind']}-{entry['audit_index']}-{run['run_id']}.json"
        correction = entries[entry["record_id"]]
        if correction.get("draft_sha256") != file_sha256(old):
            raise ExtractionError("REPAIR_DRAFT_BINDING_CHANGED")
        saved = json.loads(old.read_text(encoding="utf-8"))
        record, row = current[entry["record_id"]]
        identity = {
            "record_id": row["record_id"], "kind": record.kind,
            "audit_index": row["audit_index"], "source_id": record.source_id,
            "source_record_id": record.source_record_id,
            "source_fingerprint": content_fingerprint(record),
            "source_locator": {"file": record.source_file, "row": record.row_number},
            "pdf_path": record.pdf_path,
        }
        if any(saved.get(key) != value for key, value in identity.items()) or any(
            entry.get(key) != identity[key] for key in ("record_id", "kind", "audit_index")
        ) or saved.get("status") != entry["status"]:
            raise ExtractionError("REPAIR_SOURCE_IDENTITY_CHANGED")
        fields, _ = source_text_fields(record, root)
        literals, spans = verify_privacy_review(fields, entry["record_id"],
                                               reviews.get(entry["record_id"], {}))
        masked = redact_fields(fields, literals, spans, privacy_checked=True)
        if fields != saved.get("original_text") or masked.fields != saved.get("redacted_text"):
            raise ExtractionError("REPAIR_SOURCE_OR_MASK_CHANGED")
        facts, changes, unresolved = repair_fact_payload(
            saved["provider_responses"][-1], correction["actions"], schema, record.kind, masked,
        )
        saved.update({
            "status": "REVIEW_REQUIRED", "facts": facts, "current_validation_codes": [],
            "held_fields": [], "unresolved_repairs": unresolved, "source_repairs": changes,
            "repair_notes": correction.get("notes", []), "parent_run_id": run["run_id"],
            "parent_draft_sha256": file_sha256(old), "repair_plan_sha256": file_sha256(plan_path),
            "derivation": "LOCAL_SOURCE_CORRECTION_NO_PROVIDER_CALLS", "new_provider_calls": 0,
            "human_confirmed": False, "training_ready": False,
            "current_fact_schema_sha256": schema_hash,
            "current_privacy_review_sha256": privacy_hash,
            "validator_sha256": validator_hash,
        })
        prepared.append((entry, saved))
        counts["REVIEW_REQUIRED"] += 1
        counts[record.kind] += 1
        counts["changed_records"] += bool(changes)
        counts["change_receipts"] += len(changes)
        counts["unresolved_fields"] += len(unresolved)
        counts["records_with_unresolved_repairs"] += bool(unresolved)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    rows = []
    for entry, saved in prepared:
        name = f"{entry['kind']}-{entry['audit_index']}-{run_id}.json"
        _write_new(output / "drafts" / name, saved)
        rows.append({**entry, "status": "REVIEW_REQUIRED", "validation_codes": [],
                     "unresolved_repair_count": len(saved["unresolved_repairs"])})
    result = {
        **run, "run_id": run_id, "parent_run_id": run["run_id"], "records": rows,
        "counts": dict(counts), "new_provider_calls": 0, "human_approved": 0,
        "training_samples": 0, "repair_plan_sha256": file_sha256(plan_path),
        "parent_run_sha256": file_sha256(run_path),
        "current_fact_schema_sha256": schema_hash,
        "current_privacy_review_sha256": privacy_hash, "validator_sha256": validator_hash,
        "repairer_sha256": file_sha256(Path(__file__)),
        "derivation": "LOCAL_SOURCE_CORRECTION_NO_PROVIDER_CALLS",
    }
    _write_new(output / "runs" / f"{run_id}.json", result)
    return {key: value for key, value in result.items() if key != "records"}
