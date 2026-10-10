"""Keep source inspection separate from human approval and real training."""

import json

import pytest

from simrecrut_ml.cli import main
from simrecrut_ml.training.readiness import inspect_preparation


def write_audit(tmp_path, cv_count=20, job_count=20):
    evidence_dir, mappings_dir = tmp_path / "evidence", tmp_path / "mappings"
    evidence_dir.mkdir()
    mappings_dir.mkdir()
    cvs = [{
        "audit_index": index + 1,
        "status": "HUMAN_APPROVAL_REQUIRED",
        "cohort": "ORIGINAL_20",
        "specification": {"revision": 221},
        "selection": {"provisionally_usable_it_cv": True},
        "pdf_review": {"pages": 2, "visually_inspected_pages": [1, 2]},
        "source_text": "Private synthetic source text must not reach logs.",
    } for index in range(cv_count)]
    jobs = [{
        "audit_index": index + 1,
        "status": "HUMAN_APPROVAL_REQUIRED",
        "specification_revision": 221,
    } for index in range(job_count)]
    for filename, rows in (("cv-review.jsonl", cvs), ("job-review.jsonl", jobs)):
        (evidence_dir / filename).write_text(
            "".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8",
        )
    mappings = {"status": "HUMAN_APPROVAL_REQUIRED", "specification_revision": 221}
    for filename in ("cv-mapping-proposals.json", "job-mapping-proposals.json"):
        (mappings_dir / filename).write_text(json.dumps(mappings), encoding="utf-8")
    settings = tmp_path / "settings.json"
    settings.write_text(json.dumps({
        "specification_revision": 221, "status": "PREPARATION_ONLY",
        "rule_cutoff": None, "bias_effect_table": None,
    }), encoding="utf-8")
    return settings


def test_full_source_audit_never_creates_human_approval_or_training_readiness(tmp_path):
    settings = write_audit(tmp_path)
    result = inspect_preparation(tmp_path, settings)
    assert result["cv_pages_visually_inspected"] == 40
    assert result["provisionally_usable_it_cvs"] == result["job_records_inspected"] == 20
    assert result["human_approved_records"] == 0
    assert result["training_ready"] is False and len(result["blockers"]) == 8
    assert result["rule_cutoff_configured"] is result["bias_effects_configured"] is False
    assert "source_text" not in json.dumps(result)


def test_incomplete_inspection_has_additional_blocker_and_cli_only_prints_counts(tmp_path, capsys):
    settings = write_audit(tmp_path, cv_count=19)
    result = inspect_preparation(tmp_path, settings)
    assert len(result["blockers"]) == 9
    assert main([
        "audit-readiness", "--audit-dir", str(tmp_path), "--settings", str(settings),
    ]) == 0
    output = capsys.readouterr().out
    assert json.loads(output)["training_ready"] is False
    assert "Private synthetic" not in output and "source_text" not in output


@pytest.mark.parametrize("mutation", ["revision", "duplicate", "page", "approval"])
def test_audit_inspector_rejects_inconsistent_source_evidence(tmp_path, mutation):
    settings = write_audit(tmp_path)
    path = tmp_path / "evidence/cv-review.jsonl"
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    if mutation == "revision":
        rows[0]["specification"]["revision"] = 220
    elif mutation == "duplicate":
        rows[1]["audit_index"] = rows[0]["audit_index"]
    elif mutation == "page":
        rows[0]["pdf_review"]["visually_inspected_pages"] = [1]
    else:
        rows[0]["status"] = "APPROVED"
    path.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
    with pytest.raises(ValueError):
        inspect_preparation(tmp_path, settings)
