"""Compare extraction evidence with the independently prepared source audit."""

import json
import re
from collections import Counter
from pathlib import Path
from typing import Any

from ..data.pipeline import clean_output_path
from .models import ExtractionError


def _read_rows(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


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
        normalized, offsets = [], []
        for match in re.finditer(r"\s+|\S", fields.get(key, "")):
            normalized.append(" " if match.group().isspace() else match.group())
            offsets.append(match.span())
        text, cursor = "".join(normalized), 0
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


def compare_extraction(audit_dir: Path, run_path: Path, output_path: Path) -> dict[str, Any]:
    """Save evidence coverage and outstanding issues without granting approval.

    The comparison asks whether earlier audited wording is represented in
    accepted excerpts from the same field and source position. CSV audit
    wording is located on its previously checked PDF pages before matching.
    This does not measure extraction accuracy or
    prove a qualification, equivalence or requirement classification.
    Missing evidence and failed drafts remain explicit local review tasks.
    Write the detailed result below datasets/clean and return safe counts.
    """
    audit = audit_dir.resolve()
    root = next((path.parent for path in (audit, *audit.parents) if path.name == "clean"), None)
    if root is None:
        raise ExtractionError("AUDIT_MUST_BE_LOCAL_CLEAN_DATA")
    output = clean_output_path(root, output_path)
    run_path = clean_output_path(root, run_path)
    run = json.loads(run_path.read_text(encoding="utf-8"))
    if run.get("schema_version") != "extraction-batch-v1":
        raise ExtractionError("UNSUPPORTED_EXTRACTION_RUN")
    originals = {
        row["record_id"]: row for filename in ("cv-review.jsonl", "job-review.jsonl")
        for row in _read_rows(audit / "evidence" / filename)
    }
    extraction = run_path.parent.parent
    rows, totals = [], Counter()
    for entry in run["records"]:
        path = extraction / "drafts" / (
            f"{entry['kind']}-{entry['audit_index']}-{run['run_id']}.json"
        )
        draft = json.loads(path.read_text(encoding="utf-8"))
        original = originals[entry["record_id"]]
        if any(draft.get(key) != entry.get(key) for key in (
            "record_id", "kind", "audit_index",
        )):
            raise ExtractionError("EXTRACTION_SOURCE_IDENTITY_MISMATCH")
        fields = draft.get("original_text", {})
        facts = draft.get("facts") or draft.get("supported_partial_facts")
        evidence = _excerpts(facts)
        areas = {}
        if entry["kind"] == "resume":
            expected = _source_spans(original["fields"], {})
            for item in expected:
                item["extraction_locations"] = _pdf_locations(item, fields)
            for area in original["fields"]:
                areas[area] = len(_source_spans(original["fields"][area], {}))
        else:
            expected = _source_spans(original, fields) if fields else []
            for item in expected:
                item["extraction_locations"] = [{key: item[key] for key in (
                    "source_field", "start", "end",
                )}]
        unlocated = [item for item in expected if not item["extraction_locations"]]
        missing = [item for item in expected if not _represented(item, evidence, fields)]
        quality = []
        if missing:
            quality.append("AUDIT_EVIDENCE_NOT_EXTRACTED")
        if unlocated:
            quality.append("AUDIT_WORDING_NOT_LOCATED_ON_CHECKED_PDF_PAGES")
        if any(len(item["extraction_locations"]) > 1 for item in expected):
            quality.append("AMBIGUOUS_AUDIT_PDF_LOCATION")
        if any(item.get("ambiguous_location") for item in evidence):
            quality.append("AMBIGUOUS_EVIDENCE_LOCATION")
        if original.get("issues"):
            quality.append("SOURCE_ISSUES_REMAIN")
        if entry["status"] != "REVIEW_REQUIRED":
            quality.append("EXTRACTION_HELD")
        if entry["kind"] == "job" and not draft.get("original_text"):
            quality.append("AUDIT_COMPARISON_PENDING_SOURCE_FIELDS")
        rows.append({
            **entry, "audited_unique_spans": len(expected),
            "draft_content": ("VALIDATED_FULL" if draft.get("facts") is not None else
                              "SUPPORTED_PARTIAL" if facts is not None else "NONE"),
            "held_field_count": len(draft.get("held_fields", [])),
            "represented_spans": len(expected) - len(missing),
            "unrepresented_spans": missing, "accepted_evidence_fields": len(evidence),
            "unlocated_audit_spans": unlocated,
            "ambiguous_evidence_fields": sum(bool(item.get("ambiguous_location"))
                                              for item in evidence),
            "whitespace_aligned_fields": sum(item.get("alignment") == "WHITESPACE_ONLY"
                                             for item in evidence),
            "expected_spans_by_area": areas, "quality_codes": quality,
            "existing_audit_issues": original.get("issues", []),
            "human_confirmed": False, "training_ready": False,
        })
        totals[entry["status"]] += 1
        totals["audited_unique_spans"] += len(expected)
        totals["represented_spans"] += len(expected) - len(missing)
        totals["unrepresented_spans"] += len(missing)
        totals["unlocated_audit_spans"] += len(unlocated)
        totals["accepted_evidence_fields"] += len(evidence)
    result = {
        "schema_version": "extraction-evidence-comparison-v1", "run_id": run["run_id"],
        "counts": dict(totals), "records": rows, "human_approved": 0,
        "training_samples": 0,
        "interpretation": "Evidence representation check; not accuracy or human confirmation.",
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {key: value for key, value in result.items() if key != "records"}
