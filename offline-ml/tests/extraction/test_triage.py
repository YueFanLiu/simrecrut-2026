"""Keep source-check proposals separate from approval and source history."""

import hashlib
import json

import pytest

from simrecrut_ml.data.models import SourceRecord
from simrecrut_ml.data.normalization import content_fingerprint
from simrecrut_ml.extraction import triage
from simrecrut_ml.extraction.models import ExtractionError


def _fixture(tmp_path, monkeypatch):
    root = tmp_path / "datasets"
    audit = root / "clean/pilot-audit"
    output = audit / "extraction"
    (output / "runs").mkdir(parents=True)
    (output / "drafts").mkdir()
    (audit / "verification").mkdir()
    fields = {"pdf_page_1": "C++ development"}
    record = SourceRecord("fixture", "resume", "1", "raw/fixture.csv", 1, {}, "raw/1.pdf")
    row = {"record_id": "draft-fixture", "audit_index": 1}
    monkeypatch.setattr(triage, "load_acquisition_manifest", lambda path: (root, []))
    monkeypatch.setattr(triage, "audited_records", lambda *args: iter([(record, row)]))
    monkeypatch.setattr(triage, "source_text_fields", lambda *args: (fields, {}))
    privacy_path = audit / "verification/extraction-privacy-review.json"
    privacy_path.write_text(json.dumps({"records": [{
        **row, "status": "PRIVACY_CHECKED", "identifier_literals": [], "identifier_spans": {},
        "original_field_sha256": {
            "pdf_page_1": hashlib.sha256(fields["pdf_page_1"].encode()).hexdigest(),
        },
    }]}), encoding="utf-8")
    entry = {**row, "kind": "resume", "status": "FAILED_FINAL"}
    run = {"schema_version": "extraction-batch-v1", "run_id": "fixture", "records": [entry],
           "counts": {"held_fields": 1},
           "current_privacy_review_sha256": hashlib.sha256(privacy_path.read_bytes()).hexdigest()}
    run_path = output / "runs/fixture.json"
    run_path.write_text(json.dumps(run), encoding="utf-8")
    span = {"source_field": "Resume_str", "start": 0, "end": 3, "excerpt": "C++"}
    coverage_path = audit / "verification/coverage.json"
    coverage_path.write_text(json.dumps({
        "schema_version": "extraction-evidence-comparison-v1", "run_id": "fixture",
        "counts": {"unrepresented_spans": 1},
        "records": [{**entry, "unrepresented_spans": [span]}],
    }), encoding="utf-8")
    draft_path = output / "drafts/resume-1-fixture.json"
    draft_path.write_text(json.dumps({
        **entry, "original_text": fields, "redacted_text": fields,
        "source_id": record.source_id, "source_record_id": record.source_record_id,
        "source_fingerprint": content_fingerprint(record),
        "source_locator": {"file": record.source_file, "row": record.row_number},
        "pdf_path": record.pdf_path,
        "held_fields": [{"path": "$.professional.skills[0].wording",
                         "code": "VALUE_IS_NOT_SOURCE_WORDING"}],
    }), encoding="utf-8")
    common = {**{key: entry[key] for key in ("record_id", "kind", "audit_index")},
              "sequence": 1, "category": "SOURCE_SUPPORTED_OMISSION",
              "resolution": "AUTOMATED_SOURCE_CHECK_ONLY", "rationale": "Exact source wording.",
              "proposed_value": "C++", "owner_question": None,
              "human_confirmed": False, "training_ready": False,
              "proposed_evidence": [{"source_field": "pdf_page_1", "start": 0,
                                     "end": 3, "excerpt": "C++"}]}
    receipt = {
        "schema_version": "extraction-triage-v1", "run_id": "fixture",
        "kind": "resume", "run_sha256": hashlib.sha256(run_path.read_bytes()).hexdigest(),
        "privacy_review_sha256": hashlib.sha256(privacy_path.read_bytes()).hexdigest(),
        "draft_sha256": {"draft-fixture": hashlib.sha256(draft_path.read_bytes()).hexdigest()},
        "coverage_sha256": hashlib.sha256(coverage_path.read_bytes()).hexdigest(),
        "cases": [
            {**common, "case_id": "resume-01-span-01", "case_type": "UNREPRESENTED_SPAN",
             "original_span": span},
            {**common, "case_id": "resume-01-held-01", "case_type": "HELD_FIELD",
             "provider_path": "$.professional.skills[0].wording"},
        ],
    }
    receipt_path = audit / "verification/triage-cv.json"
    receipt_path.write_text(json.dumps(receipt), encoding="utf-8")
    arguments = (tmp_path / "manifest.json", audit, run_path, coverage_path, [receipt_path],
                 audit / "verification/triage.json")
    return arguments, receipt_path, receipt, draft_path, privacy_path


def test_source_proposals_preserve_failed_receipts_and_zero_approval(tmp_path, monkeypatch):
    arguments, _, _, draft, _ = _fixture(tmp_path, monkeypatch)
    original = draft.read_bytes()
    summary = triage.assemble_triage(*arguments)
    assert summary["case_count"] == 2 and summary["new_provider_calls"] == 0
    assert summary["human_approved"] == 0 and summary["training_samples"] == 0
    assert draft.read_bytes() == original
    assert json.loads(draft.read_text())["status"] == "FAILED_FINAL"


@pytest.mark.parametrize("mutation,code", [
    ("approval", "TRIAGE_CANNOT_GRANT_APPROVAL"),
    ("quote", "TRIAGE_EVIDENCE_DOES_NOT_MATCH_SOURCE"),
    ("missing", "TRIAGE_CASES_INCOMPLETE"),
    ("duplicate", "TRIAGE_CASE_MISSING_OR_DUPLICATED"),
    ("privacy", "TRIAGE_PRIVACY_BINDING_CHANGED"),
    ("identity", "TRIAGE_SOURCE_IDENTITY_MISMATCH"),
    ("draft_reply", "TRIAGE_DRAFT_BINDING_MISMATCH"),
    ("hidden_question", "TRIAGE_OWNER_DECISION_CANNOT_BE_HIDDEN"),
])
def test_triage_rejects_changed_sources_or_unbound_proposals(
    tmp_path, monkeypatch, mutation, code,
):
    arguments, path, receipt, draft_path, privacy_path = _fixture(tmp_path, monkeypatch)
    if mutation == "approval":
        receipt["cases"][0]["human_confirmed"] = True
    elif mutation == "quote":
        receipt["cases"][0]["proposed_evidence"][0]["excerpt"] = "Python"
    elif mutation == "missing":
        receipt["cases"].pop()
    elif mutation == "duplicate":
        receipt["cases"].append(receipt["cases"][0])
    elif mutation == "hidden_question":
        receipt["cases"][0]["category"] = "NEEDS_OWNER_DECISION"
    elif mutation == "privacy":
        privacy_path.write_text(privacy_path.read_text() + "\n", encoding="utf-8")
    elif mutation == "identity":
        draft = json.loads(draft_path.read_text())
        draft["source_record_id"] = "other-person"
        draft_path.write_text(json.dumps(draft), encoding="utf-8")
    else:
        draft = json.loads(draft_path.read_text())
        draft["provider_responses"] = ["Changed saved response at the same field path"]
        draft_path.write_text(json.dumps(draft), encoding="utf-8")
    path.write_text(json.dumps(receipt), encoding="utf-8")
    with pytest.raises(ExtractionError, match=code):
        triage.assemble_triage(*arguments)
    assert not arguments[-1].exists()


def test_triage_rejects_quote_crossing_a_masked_space(tmp_path, monkeypatch):
    arguments, path, receipt, _, privacy_path = _fixture(tmp_path, monkeypatch)
    privacy = json.loads(privacy_path.read_text())
    privacy["records"][0]["identifier_spans"] = {
        "pdf_page_1": [{"start": 3, "end": 4, "reason_code": "IDENTIFIER_BOUNDARY"}],
    }
    privacy_path.write_text(json.dumps(privacy), encoding="utf-8")
    run_path = arguments[2]
    run = json.loads(run_path.read_text())
    privacy_hash = hashlib.sha256(privacy_path.read_bytes()).hexdigest()
    run["current_privacy_review_sha256"] = privacy_hash
    run_path.write_text(json.dumps(run), encoding="utf-8")
    receipt["run_sha256"] = hashlib.sha256(run_path.read_bytes()).hexdigest()
    receipt["privacy_review_sha256"] = privacy_hash
    receipt["cases"][0]["proposed_evidence"][0].update({"end": 4, "excerpt": "C++ "})
    path.write_text(json.dumps(receipt), encoding="utf-8")
    with pytest.raises(ExtractionError, match="TRIAGE_EVIDENCE_OVERLAPS_IDENTIFIER_MASK"):
        triage.assemble_triage(*arguments)
