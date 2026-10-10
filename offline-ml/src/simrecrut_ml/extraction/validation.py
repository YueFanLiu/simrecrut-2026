"""Check the fact schema and resolve every excerpt to original evidence."""

import json
import re
from copy import deepcopy
from typing import Any

from .models import ExtractionError
from .redaction import RedactedText


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


def validate_reply(
    content: str, schema: dict[str, Any], kind: str, text: RedactedText,
) -> dict[str, Any]:
    """Return a validated copy with exact local offsets and page locations.

    No vendor score, personal field, inferred code or HR flag is allowed.
    Whitespace differences alone may be aligned; case, punctuation and
    spelling remain exact. Save the original excerpt and provider wording.
    Missing lists are unknown, not confirmed absence. This validates
    evidence attachment, not the truth or completeness of source claims.
    Human review is still required before matching or model preparation.
    """
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
    result = deepcopy(payload)
    _walk_fields(result, text)
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
