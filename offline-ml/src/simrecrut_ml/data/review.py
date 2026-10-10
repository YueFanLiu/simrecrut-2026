"""Export local review drafts and accept explicit, validated corrections."""

import heapq
import json
import math
import re
from pathlib import Path
from typing import Any, Iterator

from .adapters import file_sha256, safe_dataset_path
from .mappings import load_reviewed_mappings, review_identity
from .models import PreparationError
from .normalization import EVIDENCE_COLUMNS, PROFESSIONAL_AREAS
from .pipeline import clean_output_path, write_json_line


FIELD_STATES = {"CONFIRMED", "CONFIRMED_ABSENT", "UNKNOWN", "CONFLICTING"}
MONTH = re.compile(r"^\d{4}-(?:0[1-9]|1[0-2])$")


def _read_lines(path: Path) -> Iterator[dict[str, Any]]:
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            if line.strip():
                value = json.loads(line)
                if not isinstance(value, dict):
                    raise PreparationError("Local data rows must be JSON objects.")
                yield value


def _prepared_inputs(clean_dir: Path) -> tuple[Path, Path, set[str]]:
    directory = Path(clean_dir).resolve()
    clean_parent = next((item for item in (directory, *directory.parents)
                         if item.name == "clean"), None)
    if clean_parent is None:
        raise PreparationError("Review inputs must be inside datasets/clean.")
    manifest_path = directory / "preparation-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != "preparation-manifest-v1":
        raise PreparationError("Unsupported preparation manifest version.")
    paths = {}
    for name in ("prepared", "issues"):
        entry = manifest["files"][name]
        relative = directory.relative_to(clean_parent.parent) / entry["path"]
        path = safe_dataset_path(clean_parent.parent, relative.as_posix())
        if file_sha256(path) != entry["sha256"]:
            raise PreparationError("Prepared review evidence has changed after preparation.")
        paths[name] = path
    blocked = {
        record_id for issue in _read_lines(paths["issues"])
        for record_id in issue.get("record_ids", [])
    }
    return clean_parent.parent, paths["prepared"], blocked


def export_review_batch(
    clean_dir: Path,
    output_path: Path,
    profile_count: int = 20,
    job_count: int = 20,
) -> dict[str, int]:
    """Write a deterministic review batch with source evidence and blanks.

    Prefer original Resume Dataset CVs for the profile target, then use
    Djinni profile text if too few are available. Return actual counts;
    a small source never becomes a fabricated 20-record pilot. Every row
    stays REVIEW_REQUIRED and contains no automatic professional facts.
    The output may contain personal evidence and must remain local.
    """
    if profile_count < 0 or job_count < 0:
        raise PreparationError("Review counts cannot be negative.")
    dataset_root, prepared_path, blocked = _prepared_inputs(clean_dir)
    output = clean_output_path(dataset_root, output_path)
    if output.exists():
        raise PreparationError("Review export does not overwrite existing reviews.")
    groups: dict[str, list[tuple[str, dict[str, Any]]]] = {
        "resume": [], "profile": [], "job": [],
    }
    limits = {"resume": profile_count, "profile": profile_count, "job": job_count}
    for draft in _read_lines(prepared_path):
        if draft["record_id"] in blocked:
            continue
        group = groups[draft["kind"]]
        limit = limits[draft["kind"]]
        if limit == 0:
            continue
        group.append((draft["record_id"], draft))
        if len(group) > limit:
            group[:] = heapq.nsmallest(limit, group, key=lambda item: item[0])
    resumes = sorted(groups["resume"], key=lambda item: item[0])
    profiles = sorted(groups["profile"], key=lambda item: item[0])[:profile_count - len(resumes)]
    jobs = sorted(groups["job"], key=lambda item: item[0])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        for _, draft in resumes + profiles + jobs:
            row = {
                "record_id": draft["record_id"],
                "source_fingerprint": draft["source_fingerprint"],
                "kind": draft["kind"], "status": "REVIEW_REQUIRED",
                "reviewer": "", "reviewed_at": "", "notes": "",
                "job_family": "", "mapping_version": "",
                "field_status": dict(draft["field_status"]),
                "professional": dict(draft["professional"]), "requirements": [],
                "pdf_inspection": {"inspected": False, "ocr_needed": None, "notes": ""},
                "evidence": {
                    "source_id": draft["source_id"],
                    "source_record_id": draft["source_record_id"],
                    "source_locator": draft["source_locator"],
                    "source_values": draft["source_values"],
                    "observations": draft["observations"], "pdf": draft["pdf"],
                    "provisional_family": draft["selection"]["family"],
                },
            }
            write_json_line(stream, row)
    return {"resumes": len(resumes), "profiles": len(profiles), "jobs": len(jobs)}


def _keys(value: dict[str, Any], allowed: set[str], required: set[str]) -> None:
    if not isinstance(value, dict) or set(value) - allowed or required - set(value):
        raise PreparationError("Reviewed data contains missing or unsupported fields.")


def _code(value: Any, choices: set[str] | list[str]) -> None:
    if not isinstance(value, str) or value not in choices:
        raise PreparationError("A professional code is absent from the reviewed mapping version.")


def _codes(values: Any, choices: set[str] | list[str], nonempty: bool = False) -> None:
    if not isinstance(values, list) or (nonempty and not values):
        raise PreparationError("Professional code sets must be explicit lists.")
    for value in values:
        _code(value, choices)
    if len(values) != len(set(values)):
        raise PreparationError("A reviewed professional code cannot be counted twice.")


def _number(value: Any, positive: bool = False) -> None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise PreparationError("Reviewed durations must be finite numbers.")
    if not math.isfinite(value) or value < 0 or (positive and value == 0):
        raise PreparationError("Reviewed durations are outside their allowed range.")


def _evidence(values: Any, draft: dict[str, Any]) -> None:
    if not isinstance(values, list) or not values:
        raise PreparationError("Confirmed items need explicit source evidence references.")
    for evidence in values:
        _keys(evidence, {"source_field", "start", "end"}, {"source_field", "start", "end"})
        field = evidence["source_field"]
        if not isinstance(field, str) or field not in EVIDENCE_COLUMNS[draft["kind"]]:
            raise PreparationError("Review evidence must use a professional source field.")
        source = draft["source_values"].get(field)
        start, end = evidence["start"], evidence["end"]
        if source is None or isinstance(start, bool) or isinstance(end, bool):
            raise PreparationError("A review evidence reference has no source value.")
        if not isinstance(start, int) or not isinstance(end, int):
            raise PreparationError("A review evidence span must use integer offsets.")
        if not 0 <= start < end <= len(str(source)):
            raise PreparationError("A review evidence span is outside its source value.")


def _profile(professional: Any, draft: dict[str, Any], mapping: dict[str, Any]) -> None:
    _keys(professional, set(PROFESSIONAL_AREAS), set(PROFESSIONAL_AREAS))
    skill_codes = set(mapping["skill_aliases"].values())
    for area, items in professional.items():
        if not isinstance(items, list):
            raise PreparationError("Professional areas must contain item arrays.")
        for item in items:
            if area == "skills":
                _keys(item, {"skillCode", "evidence"}, {"skillCode", "evidence"})
                _code(item["skillCode"], skill_codes)
            elif area == "experience":
                _keys(item, {
                    "roleFamilyCode", "startMonth", "endMonth", "isCurrent",
                    "supportedSkillCodes", "reviewedRelevantYears", "evidence",
                }, {"roleFamilyCode", "supportedSkillCodes", "evidence"})
                _code(item["roleFamilyCode"], mapping["job_family_codes"])
                _codes(item["supportedSkillCodes"], skill_codes)
                if "isCurrent" in item and not isinstance(item["isCurrent"], bool):
                    raise PreparationError("The current experience flag must be a boolean.")
                if item.get("reviewedRelevantYears") is not None:
                    _number(item["reviewedRelevantYears"])
                    if item.get("startMonth") is not None or item.get("endMonth") is not None:
                        raise PreparationError(
                            "Do not count a reviewed duration and dated period twice."
                        )
                else:
                    start = item.get("startMonth")
                    end = item.get("endMonth")
                    if not isinstance(start, str) or not MONTH.fullmatch(start):
                        raise PreparationError("Dated experience needs a valid start month.")
                    if not isinstance(item.get("isCurrent"), bool):
                        raise PreparationError("Dated experience needs an explicit current flag.")
                    if item["isCurrent"]:
                        if end is not None:
                            raise PreparationError("Current experience has no fixed end month.")
                    elif not isinstance(end, str) or not MONTH.fullmatch(end) or end < start:
                        raise PreparationError("Experience end month must follow its start month.")
            elif area == "education":
                _keys(item, {"degreeLevelCode", "subjectCode", "evidence"},
                      {"degreeLevelCode", "subjectCode", "evidence"})
                _code(item["degreeLevelCode"], mapping["degree_order"])
                _code(item["subjectCode"], mapping["subject_codes"])
            elif area == "languages":
                _keys(item, {"languageCode", "levelCode", "evidence"},
                      {"languageCode", "levelCode", "evidence"})
                _code(item["languageCode"], mapping["language_codes"])
                _code(item["levelCode"], mapping["language_level_order"])
            else:
                _keys(item, {
                    "projectId", "supportedConditionCodes", "supportedSkillCodes", "evidence",
                }, {"projectId", "supportedConditionCodes", "supportedSkillCodes", "evidence"})
                if not isinstance(item["projectId"], str) or not item["projectId"].strip():
                    raise PreparationError("Reviewed projects need local project identifiers.")
                _codes(item["supportedConditionCodes"], mapping["project_condition_codes"])
                _codes(item["supportedSkillCodes"], skill_codes)
            _evidence(item["evidence"], draft)
        if area == "skills" and len({item["skillCode"] for item in items}) != len(items):
            raise PreparationError("A reviewed skill contributes only once.")
        if area == "languages" and len({item["languageCode"] for item in items}) != len(items):
            raise PreparationError("A reviewed language cannot repeat.")


def _requirements(items: Any, draft: dict[str, Any], mapping: dict[str, Any]) -> None:
    if not isinstance(items, list):
        raise PreparationError("Reviewed requirements must be an item array.")
    identities = set()
    for item in items:
        _keys(item, {
            "criterionType", "requirementCode", "assessmentIncluded", "mandatory",
            "publicVisible", "payload", "evidence",
        }, {"criterionType", "requirementCode", "assessmentIncluded", "mandatory",
            "publicVisible", "payload", "evidence"})
        criterion = item["criterionType"]
        _code(criterion, {area.upper() for area in PROFESSIONAL_AREAS})
        identifier = item["requirementCode"]
        if not isinstance(identifier, str) or not identifier.strip():
            raise PreparationError("A reviewed requirement needs a code.")
        identity = (criterion, identifier)
        if identity in identities:
            raise PreparationError("A reviewed requirement cannot repeat.")
        identities.add(identity)
        if any(not isinstance(item[field], bool) for field in
               ("assessmentIncluded", "mandatory", "publicVisible")):
            raise PreparationError(
                "Requirement inclusion, mandatory, and visibility are independent flags."
            )
        _evidence(item["evidence"], draft)
        payload = item["payload"]
        if criterion == "SKILLS":
            _keys(payload, {"assessedSkillCodes"}, {"assessedSkillCodes"})
            _codes(payload["assessedSkillCodes"], set(mapping["skill_aliases"].values()), True)
        elif criterion == "EXPERIENCE":
            _keys(payload, {"targetYears", "relevantRoleFamilyCodes", "minimumYears"},
                  {"targetYears", "relevantRoleFamilyCodes"})
            _number(payload["targetYears"], True)
            _codes(payload["relevantRoleFamilyCodes"], mapping["job_family_codes"], True)
            if "minimumYears" in payload:
                _number(payload["minimumYears"])
        elif criterion == "EDUCATION":
            _keys(payload, {
                "minimumDegreeLevelCode", "acceptedSubjectCodes", "unrestrictedSubject",
            }, {"minimumDegreeLevelCode", "acceptedSubjectCodes", "unrestrictedSubject"})
            _code(payload["minimumDegreeLevelCode"], mapping["degree_order"])
            if not isinstance(payload["unrestrictedSubject"], bool):
                raise PreparationError("The unrestricted subject flag must be explicit.")
            _codes(payload["acceptedSubjectCodes"], mapping["subject_codes"],
                   not payload["unrestrictedSubject"])
        elif criterion == "LANGUAGES":
            _keys(payload, {"requiredLanguages"}, {"requiredLanguages"})
            languages = payload["requiredLanguages"]
            if not isinstance(languages, list) or not languages:
                raise PreparationError("Assessed languages cannot be an empty set.")
            for language in languages:
                _keys(language, {"languageCode", "minimumLevelCode"},
                      {"languageCode", "minimumLevelCode"})
                _code(language["languageCode"], mapping["language_codes"])
                _code(language["minimumLevelCode"], mapping["language_level_order"])
            if len({entry["languageCode"] for entry in languages}) != len(languages):
                raise PreparationError("A required language cannot repeat.")
        else:
            _keys(payload, {"assessedConditionCodes"}, {"assessedConditionCodes"})
            _codes(payload["assessedConditionCodes"], mapping["project_condition_codes"], True)


def _approved_row(
    row: dict[str, Any], draft: dict[str, Any], mapping: dict[str, Any]
) -> dict[str, Any]:
    if row.get("mapping_version") != mapping["version"]:
        raise PreparationError("The review must name the exact approved mapping version.")
    _code(row.get("job_family"), mapping["job_family_codes"])
    states = row.get("field_status")
    _keys(states, set(PROFESSIONAL_AREAS), set(PROFESSIONAL_AREAS))
    if any(not isinstance(state, str) or state not in FIELD_STATES or state == "CONFLICTING"
           for state in states.values()):
        raise PreparationError("Unresolved conflicts cannot enter approved research records.")
    if draft["kind"] == "job":
        requirements = row.get("requirements")
        _requirements(requirements, draft, mapping)
        job_profile = row.get("professional")
        _keys(job_profile, set(PROFESSIONAL_AREAS), set(PROFESSIONAL_AREAS))
        if any(job_profile.get(area) for area in PROFESSIONAL_AREAS):
            raise PreparationError("Job requirements cannot become candidate professional facts.")
        professional = None
        counts = {area: sum(item["criterionType"] == area.upper() for item in requirements)
                  for area in PROFESSIONAL_AREAS}
    else:
        professional = row.get("professional")
        _profile(professional, draft, mapping)
        if row.get("requirements"):
            raise PreparationError("Candidate facts cannot become job requirements.")
        requirements = None
        counts = {area: len(professional[area]) for area in PROFESSIONAL_AREAS}
    for area, count in counts.items():
        if (states[area] == "CONFIRMED") != (count > 0):
            raise PreparationError(
                "Field status must distinguish confirmed items, absence, and unknown."
            )
    inspection = row.get("pdf_inspection", {})
    _keys(inspection, {"inspected", "ocr_needed", "notes"},
          {"inspected", "ocr_needed", "notes"})
    if not isinstance(inspection["inspected"], bool):
        raise PreparationError("PDF inspection status must be explicit.")
    if inspection["inspected"]:
        if not draft["pdf"]["path"] or not isinstance(inspection["ocr_needed"], bool):
            raise PreparationError("An inspected PDF needs a file and recorded OCR assessment.")
    elif inspection["ocr_needed"] is not None:
        raise PreparationError("OCR need remains unknown until the PDF is inspected.")
    return {
        "schema_version": "reviewed-record-v1", "record_id": draft["record_id"],
        "source_identity": draft["source_identity"], "source_id": draft["source_id"],
        "source_record_id": draft["source_record_id"], "kind": draft["kind"],
        "source_locator": draft["source_locator"],
        "source_fingerprint": draft["source_fingerprint"],
        "base_profile_key": draft["base_profile_key"], "job_family": row["job_family"],
        "mapping_version": mapping["version"], "professional": professional,
        "requirements": requirements, "field_status": states,
        "research_attributes": draft["research_attributes"],
        "review": {field: row[field] for field in ("status", "reviewer", "reviewed_at", "notes")},
        "pdf_inspection": inspection,
    }


def apply_review_batch(
    clean_dir: Path,
    review_path: Path,
    mapping_path: Path,
    output_path: Path,
) -> dict[str, int]:
    """Import human reviews without silently confirming missing facts.

    Approved corrections require exact source identity/fingerprint,
    reviewer, timestamp, notes, evidence spans, and an approved mapping
    version. UNKNOWN remains unknown; a conflict blocks approval. Other
    review states are recorded separately and never become model inputs.
    Publisher text and direct-identifier fields are not copied into the
    professional schema. Review notes remain local; reviewers must not
    include identifiers or quote CV paragraphs in those notes.
    """
    dataset_root, prepared_path, blocked = _prepared_inputs(clean_dir)
    review_path = clean_output_path(dataset_root, review_path)
    output = clean_output_path(dataset_root, output_path)
    decisions = output.with_name(output.stem + "-decisions.jsonl")
    if output.exists() or decisions.exists():
        raise PreparationError("Review import does not overwrite previous decisions.")
    mapping = load_reviewed_mappings(mapping_path)
    audit_sources = {
        "preparation_manifest_sha256": file_sha256(Path(clean_dir) / "preparation-manifest.json"),
        "review_file_sha256": file_sha256(review_path),
        "mapping_sha256": file_sha256(mapping_path),
    }
    rows = {}
    for row in _read_lines(review_path):
        record_id = row.get("record_id")
        if not isinstance(record_id, str) or record_id in rows:
            raise PreparationError("A review must identify each record once.")
        rows[record_id] = row
    drafts = {draft["record_id"]: draft for draft in _read_lines(prepared_path)
              if draft["record_id"] in rows}
    if set(drafts) != set(rows):
        raise PreparationError("A review refers to a record outside this prepared source.")
    approved, decision_rows = [], []
    counts = {"approved": 0, "needs_correction": 0, "rejected": 0, "pending": 0}
    for record_id, row in rows.items():
        draft = drafts[record_id]
        if row.get("source_fingerprint") != draft["source_fingerprint"]:
            raise PreparationError("Review source evidence does not match the prepared record.")
        status = row.get("status")
        if status not in {"APPROVED", "NEEDS_CORRECTION", "REJECTED", "REVIEW_REQUIRED"}:
            raise PreparationError("Unsupported human review state.")
        if status == "REVIEW_REQUIRED":
            counts["pending"] += 1
            continue
        review_identity(row)
        if not isinstance(row.get("notes"), str) or not row["notes"].strip():
            raise PreparationError("A completed review needs a recorded explanation.")
        if status == "APPROVED":
            if record_id in blocked:
                raise PreparationError("Resolve a conflicting publisher identity before approval.")
            approved.append(_approved_row(row, draft, mapping) | {"audit_sources": audit_sources})
            counts["approved"] += 1
        else:
            counts["needs_correction" if status == "NEEDS_CORRECTION" else "rejected"] += 1
        decision_rows.append({
            "record_id": record_id, "source_fingerprint": draft["source_fingerprint"],
            **{field: row[field] for field in ("status", "reviewer", "reviewed_at", "notes")},
        })
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        for row in sorted(approved, key=lambda item: item["record_id"]):
            write_json_line(stream, row)
    with decisions.open("w", encoding="utf-8", newline="\n") as stream:
        for row in sorted(decision_rows, key=lambda item: item["record_id"]):
            write_json_line(stream, row)
    return counts
