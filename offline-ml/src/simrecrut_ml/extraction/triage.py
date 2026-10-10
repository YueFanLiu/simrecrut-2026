"""Bind source-check proposals to saved evidence without approving facts."""

import json
import re
from collections import Counter
from pathlib import Path
from typing import Any

from ..data.adapters import file_sha256, load_acquisition_manifest
from ..data.normalization import content_fingerprint
from ..data.pipeline import clean_output_path
from .batch import audited_records, source_text_fields, verify_privacy_review
from .models import ExtractionError
from .redaction import redact_fields


CATEGORIES = {
    "SOURCE_SUPPORTED_OMISSION", "ALREADY_REPRESENTED_DIFFERENT_EXCERPT",
    "NON_ASSESSED_CONTEXT", "SOURCE_CONFLICT_OR_UNKNOWN", "SOURCE_LOCATION_MISMATCH",
    "NEEDS_OWNER_DECISION",
}
RESOLUTIONS = {"AUTOMATED_SOURCE_CHECK_ONLY", "OWNER_DECISION_REQUIRED"}


def _read(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _expected_cases(
    coverage: dict[str, Any], drafts: dict[str, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    expected = {}
    for row in coverage["records"]:
        identity = {key: row[key] for key in ("record_id", "kind", "audit_index")}
        for index, span in enumerate(row["unrepresented_spans"], 1):
            key = f"{row['kind']}-{row['audit_index']:02}-span-{index:02}"
            expected[key] = {**identity, "case_type": "UNREPRESENTED_SPAN",
                             "sequence": index, "original_span": span}
        for index, held in enumerate(drafts[row["record_id"]].get("held_fields", []), 1):
            key = f"{row['kind']}-{row['audit_index']:02}-held-{index:02}"
            expected[key] = {**identity, "case_type": "HELD_FIELD", "sequence": index,
                             "provider_path": held["path"]}
    return expected


def _check_case(
    case: dict[str, Any], expected: dict[str, Any], fields: dict[str, str],
    redacted: dict[str, str], masks: dict[str, list[tuple[int, int]]],
) -> None:
    if any(case.get(key) != value for key, value in expected.items()):
        raise ExtractionError("TRIAGE_CASE_IDENTITY_MISMATCH")
    if case.get("category") not in CATEGORIES or case.get("resolution") not in RESOLUTIONS:
        raise ExtractionError("INVALID_TRIAGE_CLASSIFICATION")
    if case["category"] == "NEEDS_OWNER_DECISION" and (
        case["resolution"] != "OWNER_DECISION_REQUIRED"
    ):
        raise ExtractionError("TRIAGE_OWNER_DECISION_CANNOT_BE_HIDDEN")
    if case.get("human_confirmed") is not False or case.get("training_ready") is not False:
        raise ExtractionError("TRIAGE_CANNOT_GRANT_APPROVAL")
    if not isinstance(case.get("rationale"), str) or not case["rationale"].strip():
        raise ExtractionError("TRIAGE_RATIONALE_REQUIRED")
    question = case.get("owner_question")
    if question is not None and (not isinstance(question, str) or not question.strip()):
        raise ExtractionError("INVALID_TRIAGE_QUESTION")
    if case["resolution"] == "OWNER_DECISION_REQUIRED" and question is None:
        raise ExtractionError("TRIAGE_QUESTION_REQUIRED")
    evidence = case.get("proposed_evidence")
    if not isinstance(evidence, list):
        raise ExtractionError("TRIAGE_EVIDENCE_LIST_REQUIRED")
    for item in evidence:
        key, start, end = item.get("source_field"), item.get("start"), item.get("end")
        if key not in fields or type(start) is not int or type(end) is not int:
            raise ExtractionError("INVALID_TRIAGE_EVIDENCE_LOCATION")
        if not 0 <= start < end <= len(fields[key]):
            raise ExtractionError("INVALID_TRIAGE_EVIDENCE_LOCATION")
        if fields[key][start:end] != item.get("excerpt"):
            raise ExtractionError("TRIAGE_EVIDENCE_DOES_NOT_MATCH_SOURCE")
        if not item["excerpt"].strip():
            raise ExtractionError("TRIAGE_EVIDENCE_IS_EMPTY")
        if fields[key][start:end] != redacted[key][start:end] or any(
            start < right and end > left for left, right in masks[key]
        ):
            raise ExtractionError("TRIAGE_EVIDENCE_OVERLAPS_IDENTIFIER_MASK")
    value = case.get("proposed_value")
    if value is not None:
        if not isinstance(value, str) or not value.strip():
            raise ExtractionError("INVALID_TRIAGE_PROPOSED_VALUE")
        normalized = re.sub(r"\s+", " ", value).strip()
        if not any(normalized in re.sub(r"\s+", " ", item["excerpt"]).strip()
                   for item in evidence):
            raise ExtractionError("TRIAGE_VALUE_IS_NOT_SOURCE_WORDING")


def assemble_triage(
    manifest_path: Path, audit_dir: Path, run_path: Path, coverage_path: Path,
    review_paths: list[Path], output_path: Path,
) -> dict[str, Any]:
    """Validate all local source-check cases and save one unconfirmed receipt.

    Recheck current originals, PDF bytes and privacy bindings before accepting
    a proposed quote. Each missing span and held provider field needs exactly
    one source-check case. Quotes must match original character ranges and
    cannot cross identifier masks. No provider is called and no original
    response or extraction status is rewritten. Categories are review
    proposals, not certified semantics or human qualification approval.
    Write below datasets/clean; refuse to overwrite an earlier receipt.
    """
    root, _ = load_acquisition_manifest(manifest_path)
    audit = clean_output_path(root, audit_dir)
    run_path = clean_output_path(root, run_path)
    coverage_path = clean_output_path(root, coverage_path)
    output = clean_output_path(root, output_path)
    if output.exists():
        raise ExtractionError("TRIAGE_OUTPUT_EXISTS")
    run, coverage = _read(run_path), _read(coverage_path)
    if run.get("schema_version") != "extraction-batch-v1" or (
        coverage.get("schema_version") != "extraction-evidence-comparison-v1"
        or run["run_id"] != coverage.get("run_id")
    ):
        raise ExtractionError("TRIAGE_INPUT_RUN_MISMATCH")
    identities = [{(row["record_id"], row["kind"], row["audit_index"], row["status"])
                   for row in value["records"]} for value in (run, coverage)]
    if identities[0] != identities[1] or any(
        len(identity) != len(value["records"])
        for identity, value in zip(identities, (run, coverage))
    ):
        raise ExtractionError("TRIAGE_INPUT_IDENTITY_MISMATCH")
    if any(len({row["record_id"] for row in value["records"]}) != len(value["records"])
           for value in (run, coverage)):
        raise ExtractionError("TRIAGE_DUPLICATE_RECORD_ID")
    privacy_path = audit / "verification/extraction-privacy-review.json"
    expected_privacy = run.get("current_privacy_review_sha256", run.get("privacy_review_sha256"))
    if expected_privacy != file_sha256(privacy_path):
        raise ExtractionError("TRIAGE_PRIVACY_BINDING_CHANGED")
    privacy = _read(privacy_path)
    reviews = {row["record_id"]: row for row in privacy["records"]}
    current = {}
    for record, row in audited_records(manifest_path, audit):
        fields, _ = source_text_fields(record, root)
        literals, spans = verify_privacy_review(fields, row["record_id"],
                                               reviews.get(row["record_id"], {}))
        masked = redact_fields(fields, literals, spans, privacy_checked=True)
        identity = {
            "record_id": row["record_id"], "kind": record.kind,
            "audit_index": row["audit_index"], "source_id": record.source_id,
            "source_record_id": record.source_record_id,
            "source_fingerprint": content_fingerprint(record),
            "source_locator": {"file": record.source_file, "row": record.row_number},
            "pdf_path": record.pdf_path,
        }
        current[row["record_id"]] = (fields, masked.fields, identity, masked.spans)
    drafts, draft_hashes = {}, {}
    extraction = run_path.parent.parent
    for entry in run["records"]:
        name = f"{entry['kind']}-{entry['audit_index']}-{run['run_id']}.json"
        draft_path = extraction / "drafts" / name
        draft = _read(draft_path)
        fields, masked, identity, _ = current[entry["record_id"]]
        if any(entry.get(key) != identity[key] for key in ("record_id", "kind", "audit_index")):
            raise ExtractionError("TRIAGE_SOURCE_IDENTITY_MISMATCH")
        if draft.get("original_text") != fields or draft.get("redacted_text") != masked:
            raise ExtractionError("TRIAGE_SOURCE_CHANGED")
        if any(draft.get(key) != value for key, value in identity.items()):
            raise ExtractionError("TRIAGE_SOURCE_IDENTITY_MISMATCH")
        if draft.get("status") != entry["status"]:
            raise ExtractionError("TRIAGE_EXTRACTION_STATUS_CHANGED")
        drafts[entry["record_id"]] = draft
        draft_hashes[entry["record_id"]] = file_sha256(draft_path)
    expected = _expected_cases(coverage, drafts)
    span_total = sum(len(row["unrepresented_spans"]) for row in coverage["records"])
    held_total = sum(len(draft.get("held_fields", [])) for draft in drafts.values())
    if coverage.get("counts", {}).get("unrepresented_spans") != span_total:
        raise ExtractionError("TRIAGE_COVERAGE_TOTAL_MISMATCH")
    if run.get("counts", {}).get("held_fields") != held_total:
        raise ExtractionError("TRIAGE_HELD_TOTAL_MISMATCH")
    if len(expected) != span_total + held_total:
        raise ExtractionError("TRIAGE_CASE_ID_COLLISION")
    cases, inputs = {}, {}
    coverage_hash = file_sha256(coverage_path)
    for path in review_paths:
        path = clean_output_path(root, path)
        receipt = _read(path)
        if receipt.get("schema_version") != "extraction-triage-v1" or (
            receipt.get("run_id") != run["run_id"]
            or receipt.get("coverage_sha256") != coverage_hash
            or receipt.get("run_sha256") != file_sha256(run_path)
            or receipt.get("privacy_review_sha256") != file_sha256(privacy_path)
        ):
            raise ExtractionError("TRIAGE_REVIEW_BINDING_MISMATCH")
        kind = receipt.get("kind")
        case_records = {case["record_id"] for case in receipt["cases"]}
        if receipt.get("draft_sha256") != {key: draft_hashes[key] for key in case_records}:
            raise ExtractionError("TRIAGE_DRAFT_BINDING_MISMATCH")
        if any(case["kind"] != kind for case in receipt["cases"]):
            raise ExtractionError("TRIAGE_REVIEW_KIND_MISMATCH")
        inputs[str(path.relative_to(root))] = file_sha256(path)
        for case in receipt["cases"]:
            key = case["case_id"]
            if key not in expected or key in cases:
                raise ExtractionError("TRIAGE_CASE_MISSING_OR_DUPLICATED")
            fields, masked, _, masks = current[case["record_id"]]
            _check_case(case, expected[key], fields, masked, masks)
            cases[key] = case
    if set(cases) != set(expected):
        raise ExtractionError("TRIAGE_CASES_INCOMPLETE")
    counts = Counter(case["category"] for case in cases.values())
    result = {
        "schema_version": "extraction-triage-v1", "run_id": run["run_id"],
        "coverage_sha256": coverage_hash, "run_sha256": file_sha256(run_path),
        "privacy_review_sha256": file_sha256(privacy_path), "review_inputs": inputs,
        "draft_sha256": draft_hashes,
        "counts": dict(counts), "case_count": len(cases),
        "case_type_counts": dict(Counter(case["case_type"] for case in cases.values())),
        "resolution_counts": dict(Counter(case["resolution"] for case in cases.values())),
        "cases": [cases[key] for key in sorted(cases)],
        "new_provider_calls": 0, "human_approved": 0, "training_samples": 0,
        "interpretation": "Source-check proposals only; original extraction history is unchanged.",
    }
    encoded = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(encoded)
    return {key: value for key, value in result.items()
            if key not in {"cases", "draft_sha256", "review_inputs"}}
