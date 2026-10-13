"""Keep source corrections literal, reversible and separate from approval."""

import hashlib
import json
from copy import deepcopy
from pathlib import Path

import pytest

from simrecrut_ml.data.adapters import file_sha256
from simrecrut_ml.data.models import SourceRecord
from simrecrut_ml.data.normalization import content_fingerprint
from simrecrut_ml.extraction import repairs
from simrecrut_ml.extraction.models import ExtractionError
from simrecrut_ml.extraction.redaction import redact_fields

from test_validation import blank, observed, schema


def test_literal_repair_keeps_page_fragments_and_unresolved_fields(schema):
    text = redact_fields({"pdf_page_1": "Team-\nBuilding\nResearch on smart cards and",
                          "pdf_page_2": "visitor systems."})
    payload = blank()
    payload["professional"]["skills"] = [{
        "skillCode": None, "wording": observed("Team-Building", "Team-\nBuilding"),
    }]
    before = deepcopy(payload["professional"]["skills"][0]["wording"])
    action = {"path": "$.professional.skills[0].wording", "before": before,
              "replacement": observed("Team-\nBuilding"), "rationale": "Copy the literal wrap."}
    content = json.dumps(payload)
    facts, changes, unresolved = repairs.repair_fact_payload(
        content, [action], schema, "resume", text,
    )
    assert json.loads(content)["professional"]["skills"][0]["wording"] == before
    assert facts["professional"]["skills"][0]["wording"]["value"] == "Team-\nBuilding"
    assert facts["professional"]["skills"][0]["wording"]["evidence"][0]["page"] == 1
    assert facts["field_status"]["skills"] == "OBSERVED" and not unresolved
    assert len(changes) == 2
    action["replacement"] = {"value": None, "status": "UNKNOWN", "evidence": []}
    with pytest.raises(ExtractionError):
        repairs.repair_fact_payload(content, [action], schema, "resume", text)


def test_cross_page_repair_uses_short_literal_value_and_keeps_both_quotes(schema):
    text = redact_fields({"pdf_page_1": "Research on smart cards and",
                          "pdf_page_2": "visitor systems."})
    payload = blank()
    before = {"value": "Research on smart cards and visitor systems.", "status": "OBSERVED",
              "evidence": [{"source_field": key, "excerpt": value}
                           for key, value in text.fields.items()]}
    payload["professional"]["projects"] = [{
        "projectId": None, "supportedConditionCodes": [], "supportedSkillCodes": [],
        "description": before,
    }]
    after = {**deepcopy(before), "value": "Research on smart cards"}
    action = {"path": "$.professional.projects[0].description", "before": before,
              "replacement": after, "rationale": "Keep page fragments separate."}
    facts, _, _ = repairs.repair_fact_payload(json.dumps(payload), [action], schema,
                                             "resume", text)
    field = facts["professional"]["projects"][0]["description"]
    assert field["value"] == "Research on smart cards"
    assert [item["page"] for item in field["evidence"]] == [1, 2]
    assert [item["excerpt"] for item in field["evidence"]] == list(text.fields.values())


@pytest.mark.parametrize("change", ["invented", "masked", "code", "conflict", "stale"])
def test_repair_rejects_unsupported_facts_and_conflict_resolution(schema, change):
    text = redact_fields({"pdf_page_1": "C++\nPrivate Person"}, ("Private Person",))
    payload = blank()
    payload["professional"]["skills"] = [{"skillCode": None, "wording": observed("C++")}]
    payload["field_status"]["skills"] = "OBSERVED"
    action = {"path": "$.professional.skills[0].wording", "before": observed("C++"),
              "replacement": observed("C++"), "rationale": "Retain literal evidence."}
    if change in {"invented", "masked"}:
        action["replacement"] = observed("C#" if change == "invented" else "Private Person")
    elif change == "code":
        action.update({"path": "$.professional.skills[0].skillCode", "before": None,
                       "replacement": "CPP"})
    elif change == "conflict":
        field = {"value": None, "status": "CONFLICT", "evidence": [
            {"source_field": "pdf_page_1", "excerpt": "C++"},
            {"source_field": "pdf_page_1", "excerpt": "C++"},
        ]}
        payload["professional"]["skills"][0]["wording"] = field
        action["before"] = deepcopy(field)
    else:
        action["before"] = observed("Old value")
    with pytest.raises(ExtractionError):
        repairs.repair_fact_payload(json.dumps(payload), [action], schema, "resume", text)


def test_unknown_language_preserves_literal_requirement_and_null_flags(schema):
    payload = blank("job")
    requirement = {
        "criterionType": "LANGUAGES", "requirementCode": None, "assessmentIncluded": None,
        "mandatory": None, "publicVisible": None, "sourceUse": "REQUIREMENT_WORDING",
        "wording": observed("Written English (read & write)", source="Long Description"),
        "alternatives": {"value": None, "status": "UNKNOWN", "evidence": []},
        "payload": {"requiredLanguages": [{
            "languageCode": None, "levelCode": None,
            "language": observed("English", "Written English (read & write)", "Long Description"),
            "level": observed("Written (read & write)", "Written English (read & write)",
                              "Long Description"),
        }]},
    }
    payload["requirements"] = [requirement]
    before = requirement["payload"]["requiredLanguages"][0]["level"]
    action = {"path": "$.requirements[0].payload.requiredLanguages[0].level", "before": before,
              "replacement": {"value": None, "status": "UNKNOWN", "evidence": []},
              "rationale": "A communication mode does not establish an ordered level."}
    text = redact_fields({"Long Description": "Written English (read & write)"})
    facts, _, unresolved = repairs.repair_fact_payload(json.dumps(payload), [action],
                                                       schema, "job", text)
    req = facts["requirements"][0]
    assert req["wording"]["value"] == "Written English (read & write)"
    assert req["payload"]["requiredLanguages"][0]["level"]["status"] == "UNKNOWN"
    assert len(unresolved) == 1 and req["mandatory"] is None
    assert facts["field_status"]["languages"] == "OBSERVED"


def test_aggregate_conflict_is_preserved_when_individual_quotes_validate(schema):
    payload = blank()
    payload["professional"]["skills"] = [
        {"skillCode": None, "wording": observed("C++")},
        {"skillCode": None, "wording": observed("C#")},
    ]
    payload["field_status"]["skills"] = "CONFLICT"
    facts, changes, _ = repairs.repair_fact_payload(
        json.dumps(payload), [], schema, "resume", redact_fields({"pdf_page_1": "C++ C#"}),
    )
    assert facts["field_status"]["skills"] == "CONFLICT"
    assert not changes


def test_batch_preserves_history_and_checks_bindings_before_writes(tmp_path, monkeypatch, schema):
    root = tmp_path / "datasets"
    audit = root / "clean/pilot-audit"
    output = audit / "extraction"
    (output / "runs").mkdir(parents=True)
    (output / "drafts").mkdir()
    (audit / "verification").mkdir()
    record = SourceRecord("fixture", "resume", "1", "raw/fixture.csv", 1, {}, "raw/fixture.pdf")
    row = {"record_id": "draft-fixture", "audit_index": 1}
    fields = {"pdf_page_1": "Team-\nBuilding"}
    monkeypatch.setattr(repairs, "load_acquisition_manifest", lambda path: (root, []))
    monkeypatch.setattr(repairs, "audited_records", lambda *args: iter([(record, row)]))
    monkeypatch.setattr(repairs, "source_text_fields", lambda *args: (fields, {}))
    privacy_path = audit / "verification/extraction-privacy-review.json"
    privacy_path.write_text(json.dumps({"schema_version": "extraction-privacy-review-v1",
        "records": [{"record_id": "draft-fixture", "status": "PRIVACY_CHECKED",
                     "original_field_sha256": {
                         key: hashlib.sha256(value.encode()).hexdigest()
                         for key, value in fields.items()},
                     "identifier_literals": [], "identifier_spans": {}}]}))
    schema_path = tmp_path / "schema.json"
    schema_path.write_text(json.dumps(schema))
    payload = blank()
    before = observed("Team-Building", "Team-\nBuilding")
    payload["professional"]["skills"] = [{"skillCode": None, "wording": before}]
    entry = {"record_id": "draft-fixture", "kind": "resume", "audit_index": 1,
             "status": "FAILED_FINAL"}
    run_path = output / "runs/fixture.json"
    run_path.write_text(json.dumps({"schema_version": "extraction-batch-v1", "run_id": "fixture",
        "records": [entry], "fact_schema_sha256": file_sha256(schema_path),
        "privacy_review_sha256": file_sha256(privacy_path)}))
    old = output / "drafts/resume-1-fixture.json"
    old.write_text(json.dumps({**entry, "source_id": record.source_id,
        "source_record_id": record.source_record_id,
        "source_fingerprint": content_fingerprint(record),
        "source_locator": {"file": record.source_file, "row": record.row_number},
        "pdf_path": record.pdf_path, "original_text": fields, "redacted_text": fields,
        "provider_responses": [json.dumps(payload)], "facts": None}))
    original_hash = file_sha256(old)
    plan_path = audit / "verification/repair-plan.json"
    plan = {"schema_version": "extraction-repair-plan-v1", "run_id": "fixture",
            "run_sha256": file_sha256(run_path), "fact_schema_sha256": file_sha256(schema_path),
            "privacy_review_sha256": file_sha256(privacy_path), "records": [{
                "record_id": "draft-fixture", "draft_sha256": original_hash, "actions": [{
                    "path": "$.professional.skills[0].wording", "before": before,
                    "replacement": observed("Team-\nBuilding"), "rationale": "Copy the wrap."}]}]}
    plan_path.write_text(json.dumps(plan))
    result = repairs.repair_saved_batch(tmp_path / "manifest.json", audit, run_path,
                                        schema_path, plan_path)
    assert file_sha256(old) == original_hash
    assert result["counts"]["changed_records"] == 1
    assert result["new_provider_calls"] == 0
    assert result["human_approved"] == result["training_samples"] == 0
    new = json.loads((output / "drafts" / f"resume-1-{result['run_id']}.json").read_text())
    assert new["provider_responses"] == json.loads(old.read_text())["provider_responses"]
    assert not new["human_confirmed"] and not new["training_ready"]
    count = len(list(output.rglob("*.json")))
    plan["records"][0]["draft_sha256"] = "0" * 64
    plan_path.write_text(json.dumps(plan))
    with pytest.raises(ExtractionError, match="REPAIR_DRAFT_BINDING_CHANGED"):
        repairs.repair_saved_batch(tmp_path / "manifest.json", audit, run_path,
                                   schema_path, plan_path)
    assert len(list(output.rglob("*.json"))) == count
