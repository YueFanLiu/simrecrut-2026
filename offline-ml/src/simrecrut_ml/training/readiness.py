"""Report local source-audit progress without claiming training approval."""

import json
from pathlib import Path


SPECIFICATION_REVISION = 221


def _read_rows(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def inspect_preparation(audit_dir: Path, settings_path: Path) -> dict:
    """Summarize the current audit and list unmet corpus/protocol steps.

    Read the local pilot-audit schema and starting settings. Return counts
    and fixed prerequisite descriptions, never source text or identities.
    A source audit is distinct from human fact approval, the two-reviewer
    calibration, and training admission. This command cannot certify those
    stages; even completed audits leave training_ready false. Malformed or
    revision-mismatched inputs raise ValueError rather than being trusted.
    Nothing is written, uploaded, paired, labelled, or trained.
    """
    evidence_dir, mappings_dir = audit_dir / "evidence", audit_dir / "mappings"
    cvs = _read_rows(evidence_dir / "cv-review.jsonl")
    jobs = _read_rows(evidence_dir / "job-review.jsonl")
    cv_mapping_path = mappings_dir / "cv-mapping-proposals.json"
    cv_mappings = json.loads(cv_mapping_path.read_text(encoding="utf-8"))
    job_mapping_path = mappings_dir / "job-mapping-proposals.json"
    job_mappings = json.loads(job_mapping_path.read_text(encoding="utf-8"))
    settings = json.loads(settings_path.read_text(encoding="utf-8"))
    revisions = [row["specification"]["revision"] for row in cvs]
    revisions += [row["specification_revision"] for row in jobs]
    revisions += [obj["specification_revision"] for obj in (cv_mappings, job_mappings, settings)]
    if any(value != SPECIFICATION_REVISION for value in revisions):
        raise ValueError(
            "The audits and settings must use the same recorded specification revision.",
        )
    if any(obj["status"] != "HUMAN_APPROVAL_REQUIRED" for obj in (*cvs, *jobs)):
        raise ValueError(
            "This inspector reads unapproved source audits, not approved corpus records.",
        )
    if any(obj["status"] != "HUMAN_APPROVAL_REQUIRED" for obj in (cv_mappings, job_mappings)):
        raise ValueError("Approved mappings need the separate human-review contract.")
    indices = [row["audit_index"] for row in cvs]
    job_indices = [row["audit_index"] for row in jobs]
    if len(indices) != len(set(indices)) or len(job_indices) != len(set(job_indices)):
        raise ValueError("Audit indices must be unique within each source.")
    usable = sum(row["selection"]["provisionally_usable_it_cv"] is True for row in cvs)
    pages = 0
    for row in cvs:
        reviewed = row["pdf_review"]
        count = reviewed["pages"]
        if isinstance(count, bool) or not isinstance(count, int) or count < 1:
            raise ValueError("Every CV needs a positive page count.")
        if reviewed["visually_inspected_pages"] != list(range(1, count + 1)):
            raise ValueError("All CV pages must be recorded as visually inspected.")
        pages += count
    blockers = []
    if usable < 20 or len(jobs) < 20:
        blockers.append(
            "Complete source inspection of at least 20 technical IT CVs and 20 jobs.",
        )
    blockers.extend([
        "Approve profile facts, job requirements, aliases, equivalences and role compatibility.",
        "Resolve historical dates, degree completion, languages and conflicting job metadata.",
        "Confirm each job's assessed conditions, mandatory/public flags, active criteria "
        "and weights.",
        "Build compatible reviewed pairs with 500 base people as the source target.",
        "Collect at least 20 development pairs with two independent blinded human rubric reviews.",
        "Freeze the rule cutoff, controlled effect table and disconnected-optimum tie policy.",
        "Split original people before variants and fit data-derived mappings on training only.",
        "Generate own labels from the frozen protocol and verify both classes in each partition.",
    ])
    return {
        "specification_revision": SPECIFICATION_REVISION,
        "stage": "SOURCE_AUDIT_PREPARED",
        "training_ready": False,
        "cv_records_inspected": len(cvs),
        "original_cv_records": sum(row["cohort"] == "ORIGINAL_20" for row in cvs),
        "replacement_cv_records": sum(row["cohort"] == "REPLACEMENT" for row in cvs),
        "cv_pages_visually_inspected": pages,
        "provisionally_usable_it_cvs": usable,
        "job_records_inspected": len(jobs),
        "human_approved_records": 0,
        "rule_cutoff_configured": settings.get("rule_cutoff") is not None,
        "bias_effects_configured": settings.get("bias_effect_table") is not None,
        "settings_status": settings["status"],
        "blockers": blockers,
    }
