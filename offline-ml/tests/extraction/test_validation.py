"""Reject unsupported facts and attach exact evidence independently of vendors."""

import json
from pathlib import Path

import pytest

from simrecrut_ml.extraction.models import ExtractionError
from simrecrut_ml.extraction.redaction import redact_fields
from simrecrut_ml.extraction.validation import (
    parse_reply, supported_partial_reply, validate_reply,
)


@pytest.fixture
def schema():
    root = Path(__file__).resolve().parents[3]
    return json.loads((root / "contracts/fact-schemas/extraction-v1.json").read_text())


def blank(kind="resume"):
    """Create a sanitized empty draft, with unknown rather than absence."""
    areas = ("skills", "experience", "education", "languages", "projects")
    return {
        "schema_version": "fact-extraction-v1", "kind": kind,
        "professional": {area: [] for area in areas}, "requirements": [],
        "field_status": {area: "UNKNOWN" for area in areas},
    }


def observed(value, excerpt=None, source="pdf_page_1"):
    """Reference a literal fixture excerpt without assigning a code."""
    return {"value": value, "status": "OBSERVED", "evidence": [
        {"source_field": source, "excerpt": excerpt or value},
    ]}


def test_exact_offsets_repeated_evidence_and_page(schema):
    text = redact_fields({"pdf_page_1": "Skills C++\nExperience with C++"})
    value = blank()
    value["professional"]["skills"] = [{"skillCode": None, "wording": observed("C++")}]
    value["field_status"]["skills"] = "OBSERVED"
    result = validate_reply(json.dumps(value), schema, "resume", text)
    evidence = result["professional"]["skills"][0]["wording"]["evidence"][0]
    assert evidence["page"] == 1
    assert evidence["ambiguous_location"]
    assert evidence["locations"] == [{"start": 7, "end": 10}, {"start": 27, "end": 30}]


@pytest.mark.parametrize("change", ["score", "invented_code", "invented_value", "false_absence"])
def test_unsupported_values_are_rejected(schema, change):
    text = redact_fields({"pdf_page_1": "Skills C++"})
    value = blank()
    value["professional"]["skills"] = [{"skillCode": None, "wording": observed("C++")}]
    value["field_status"]["skills"] = "OBSERVED"
    if change == "score":
        value["acceptanceScore"] = 0.9
    elif change == "invented_code":
        value["professional"]["skills"][0]["skillCode"] = "JAVA"
    elif change == "invented_value":
        value["professional"]["skills"][0]["wording"]["value"] = "C#"
    else:
        value["field_status"]["languages"] = "OBSERVED"
    with pytest.raises(ExtractionError):
        validate_reply(json.dumps(value), schema, "resume", text)


def test_conflict_retains_both_sources_and_has_no_selected_value(schema):
    text = redact_fields({"Exp Years": "1", "Long Description": "At least 2 years"})
    value = blank("job")
    conflict = {
        "value": None, "status": "CONFLICT", "evidence": [
            {"source_field": "Exp Years", "excerpt": "1"},
            {"source_field": "Long Description", "excerpt": "At least 2 years"},
        ],
    }
    value["requirements"] = [{
        "criterionType": "EXPERIENCE", "requirementCode": None,
        "assessmentIncluded": None, "mandatory": None, "publicVisible": None,
        "sourceUse": "REQUIREMENT_WORDING", "wording": conflict,
        "alternatives": {"value": None, "status": "UNKNOWN", "evidence": []},
        "payload": {"targetYears": None, "minimumYears": None,
                    "relevantRoleFamilyCodes": [], "statedYears": conflict},
    }]
    value["field_status"]["experience"] = "CONFLICT"
    result = validate_reply(json.dumps(value), schema, "job", text)
    assert result["requirements"][0]["wording"]["value"] is None
    assert result["requirements"][0]["payload"]["targetYears"] is None
    value["requirements"][0]["mandatory"] = True
    with pytest.raises(ExtractionError):
        validate_reply(json.dumps(value), schema, "job", text)


def test_masked_evidence_cannot_be_attached(schema):
    source = "Skills C++\nEmail: private@example.test\nSkills Python"
    text = redact_fields({"pdf_page_1": source})
    value = blank()
    value["professional"]["skills"] = [
        {"skillCode": None, "wording": observed("Skills C++", text.fields["pdf_page_1"])},
    ]
    value["field_status"]["skills"] = "OBSERVED"
    with pytest.raises(ExtractionError, match="EVIDENCE_DOES_NOT_MATCH_SOURCE"):
        validate_reply(json.dumps(value), schema, "resume", text)


def test_whitespace_alignment_preserves_original_excerpt_and_offsets(schema):
    text = redact_fields({"pdf_page_1": "Skills C++\n  development"})
    value = blank()
    value["professional"]["skills"] = [
        {"skillCode": None, "wording": observed("C++", "C++ development")},
    ]
    value["field_status"]["skills"] = "OBSERVED"
    result = validate_reply(json.dumps(value), schema, "resume", text)
    evidence = result["professional"]["skills"][0]["wording"]["evidence"][0]
    assert evidence["excerpt"] == "C++\n  development"
    assert evidence["alignment"] == "WHITESPACE_ONLY"
    assert evidence["locations"] == [{"start": 7, "end": 24}]


def test_whitespace_alignment_does_not_fix_spelling_or_punctuation(schema):
    text = redact_fields({"pdf_page_1": "Skills C++"})
    value = blank()
    value["professional"]["skills"] = [{"skillCode": None, "wording": observed("C + +")}]
    value["field_status"]["skills"] = "OBSERVED"
    with pytest.raises(ExtractionError):
        validate_reply(json.dumps(value), schema, "resume", text)


def test_partial_failure_keeps_supported_items_and_unknowns_without_approval(schema):
    text = redact_fields({"pdf_page_1": "Skills C++ development"})
    value = blank()
    value["professional"]["skills"] = [
        {"skillCode": None, "wording": observed("C++")},
        {"skillCode": None, "wording": observed("Invented skill")},
    ]
    value["field_status"]["skills"] = "OBSERVED"
    partial, held = supported_partial_reply(json.dumps(value), schema, "resume", text)
    assert len(partial["professional"]["skills"]) == 1
    assert partial["field_status"]["languages"] == "UNKNOWN"
    assert held[0]["code"] == "EVIDENCE_DOES_NOT_MATCH_SOURCE"
    assert "human_confirmed" not in partial


def test_whitespace_only_observed_value_is_rejected(schema):
    text = redact_fields({"pdf_page_1": "Skills C++"})
    value = blank()
    value["professional"]["skills"] = [
        {"skillCode": None, "wording": observed(" \n\t ", "C++")},
    ]
    value["field_status"]["skills"] = "OBSERVED"
    with pytest.raises(ExtractionError, match="FIELD_HAS_NO_EVIDENCE"):
        validate_reply(json.dumps(value), schema, "resume", text)


@pytest.mark.parametrize("content", ['{"x":1,"x":2}', '{"x":NaN}', '[]'])
def test_json_ambiguity_is_rejected(content):
    with pytest.raises(ExtractionError):
        parse_reply(content)
