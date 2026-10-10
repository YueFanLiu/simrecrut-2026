"""Retain missing source evidence without treating coverage as fact approval."""

import json

import pytest

from simrecrut_ml.extraction.audit import compare_extraction


@pytest.mark.parametrize("evidence_page, represented", [(1, 1), (2, 0)])
def test_unrepresented_evidence_remains_a_local_review_task(
    tmp_path, evidence_page, represented,
):
    audit = tmp_path / "datasets/clean/pilot-audit"
    evidence = audit / "evidence"
    evidence.mkdir(parents=True)
    row = {
        "record_id": "draft-fixture", "issues": ["SOURCE_DATE_CONFLICT"],
        "fields": {"skills": {"proposals": [
            {"source_field": "Resume_str", "start": 0, "end": 3, "original": "C++",
             "pdf_pages": [1]},
            {"source_field": "Resume_str", "start": 4, "end": 8, "original": "Java",
             "pdf_pages": [1]},
        ]}},
    }
    (evidence / "cv-review.jsonl").write_text(json.dumps(row) + "\n", encoding="utf-8")
    (evidence / "job-review.jsonl").write_text("", encoding="utf-8")
    output = audit / "extraction"
    (output / "drafts").mkdir(parents=True)
    (output / "runs").mkdir()
    entry = {"record_id": "draft-fixture", "kind": "resume", "audit_index": 1,
             "status": "REVIEW_REQUIRED"}
    run = {"schema_version": "extraction-batch-v1", "run_id": "fixture", "records": [entry]}
    run_path = output / "runs/fixture.json"
    run_path.write_text(json.dumps(run), encoding="utf-8")
    draft = {
        **entry, "original_text": {"pdf_page_1": "C++ Java", "pdf_page_2": "C++ Java"},
        "facts": {"wording": {"value": "C++", "status": "OBSERVED", "evidence": [
            {"source_field": f"pdf_page_{evidence_page}", "excerpt": "C++",
             "locations": [{"start": 0,
             "end": 3}], "ambiguous_location": False, "alignment": "EXACT"},
        ]}},
    }
    (output / "drafts/resume-1-fixture.json").write_text(json.dumps(draft), encoding="utf-8")
    report = audit / "verification/comparison.json"
    summary = compare_extraction(audit, run_path, report)
    assert summary["counts"]["represented_spans"] == represented
    assert summary["counts"]["unrepresented_spans"] == 2 - represented
    assert summary["human_approved"] == 0 and summary["training_samples"] == 0
    result = json.loads(report.read_text(encoding="utf-8"))
    assert "Java" in [row["excerpt"] for row in result["records"][0]["unrepresented_spans"]]
    assert "SOURCE_ISSUES_REMAIN" in result["records"][0]["quality_codes"]


def test_job_years_are_not_covered_by_a_language_level(tmp_path):
    audit = tmp_path / "datasets/clean/pilot-audit"
    evidence = audit / "evidence"
    evidence.mkdir(parents=True)
    (evidence / "cv-review.jsonl").write_text("", encoding="utf-8")
    row = {"record_id": "job-fixture", "years": {
        "source_field": "Exp Years", "start": 0, "end": 1,
    }}
    (evidence / "job-review.jsonl").write_text(json.dumps(row) + "\n", encoding="utf-8")
    output = audit / "extraction"
    (output / "drafts").mkdir(parents=True)
    (output / "runs").mkdir()
    entry = {"record_id": "job-fixture", "kind": "job", "audit_index": 1,
             "status": "REVIEW_REQUIRED"}
    run = {"schema_version": "extraction-batch-v1", "run_id": "fixture", "records": [entry]}
    run_path = output / "runs/fixture.json"
    run_path.write_text(json.dumps(run), encoding="utf-8")
    draft = {**entry, "original_text": {"Exp Years": "1", "English Level": "B1"},
             "facts": {"wording": {"value": "B1", "status": "OBSERVED", "evidence": [
                 {"source_field": "English Level", "excerpt": "B1",
                  "locations": [{"start": 0, "end": 2}]},
             ]}}}
    (output / "drafts/job-1-fixture.json").write_text(json.dumps(draft), encoding="utf-8")
    summary = compare_extraction(audit, run_path, audit / "verification/comparison.json")
    assert summary["counts"]["represented_spans"] == 0
    assert summary["counts"]["unrepresented_spans"] == 1
