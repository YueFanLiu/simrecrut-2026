"""Reuse saved API evidence without another call or automatic fact approval."""

import hashlib
import json
from pathlib import Path

from simrecrut_ml.data.models import SourceRecord
from simrecrut_ml.data.normalization import content_fingerprint
from simrecrut_ml.extraction import recheck

from test_validation import blank, observed


def test_local_recheck_keeps_supported_partial_facts_and_original_failed_receipt(
    tmp_path, monkeypatch,
):
    root = tmp_path / "datasets"
    audit = root / "clean/pilot-audit"
    output = audit / "extraction"
    (output / "runs").mkdir(parents=True)
    (output / "drafts").mkdir()
    (audit / "verification").mkdir()
    record = SourceRecord("fixture", "resume", "1", "raw/fixture.csv", 1, {}, "raw/fixture.pdf")
    audit_row = {"record_id": "draft-fixture", "audit_index": 1}
    fields = {"pdf_page_1": "Skills C++ development"}
    monkeypatch.setattr(recheck, "load_acquisition_manifest", lambda path: (root, []))
    monkeypatch.setattr(recheck, "audited_records", lambda *args: iter([(record, audit_row)]))
    monkeypatch.setattr(recheck, "source_text_fields", lambda *args: (fields, {}))
    privacy = {"schema_version": "extraction-privacy-review-v1", "records": [
        {"record_id": "draft-fixture", "status": "PRIVACY_CHECKED",
         "original_field_sha256": {
             "pdf_page_1": hashlib.sha256(fields["pdf_page_1"].encode()).hexdigest(),
         }, "identifier_literals": [], "identifier_spans": {}},
    ]}
    (audit / "verification/extraction-privacy-review.json").write_text(json.dumps(privacy))
    payload = blank()
    payload["professional"]["skills"] = [
        {"skillCode": None, "wording": observed("C++")},
        {"skillCode": None, "wording": observed("Unsupported")},
    ]
    payload["field_status"]["skills"] = "OBSERVED"
    entry = {"record_id": "draft-fixture", "kind": "resume", "audit_index": 1,
             "status": "FAILED_FINAL"}
    run = {"schema_version": "extraction-batch-v1", "run_id": "fixture", "records": [entry]}
    run_path = output / "runs/fixture.json"
    schema = Path(__file__).resolve().parents[3] / "contracts/fact-schemas/extraction-v1.json"
    run["fact_schema_sha256"] = hashlib.sha256(schema.read_bytes()).hexdigest()
    run_path.write_text(json.dumps(run))
    saved = {**entry, "original_text": fields, "redacted_text": fields,
             "provider_responses": [json.dumps(payload)], "facts": None,
             "source_id": record.source_id, "source_record_id": record.source_record_id,
             "source_fingerprint": content_fingerprint(record),
             "source_locator": {"file": record.source_file, "row": record.row_number},
             "pdf_path": record.pdf_path}
    old = output / "drafts/resume-1-fixture.json"
    old.write_text(json.dumps(saved))
    summary = recheck.recheck_saved_batch(tmp_path / "manifest.json", audit, run_path, schema)
    assert summary["new_provider_calls"] == 0 and summary["training_samples"] == 0
    assert summary["counts"]["failed_records_with_supported_partial_facts"] == 1
    assert json.loads(old.read_text())["facts"] is None
    new = output / "drafts" / f"resume-1-{summary['run_id']}.json"
    result = json.loads(new.read_text())
    assert result["status"] == "FAILED_FINAL"
    assert len(result["supported_partial_facts"]["professional"]["skills"]) == 1
    assert result["held_fields"] and not result["human_confirmed"]
    assert result["current_fact_schema_sha256"] == run["fact_schema_sha256"]
    assert result["current_privacy_review_sha256"] and result["validator_sha256"]
