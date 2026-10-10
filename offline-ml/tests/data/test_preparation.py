"""Check source preparation and review boundaries with synthetic rows."""

import csv
import json
from pathlib import Path

import pytest

from simrecrut_ml.data.adapters import file_sha256, safe_dataset_path
from simrecrut_ml.data.models import PreparationError, SourceRecord
from simrecrut_ml.data.normalization import content_fingerprint, normalize_text, observation
from simrecrut_ml.data.pipeline import prepare_dataset
from simrecrut_ml.data.review import apply_review_batch, export_review_batch


def write_json(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False) + "\n", encoding="utf-8")


def read_rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


def fixture_source(tmp_path, rows, source_id="djinni-profiles"):
    root = tmp_path / "datasets"
    data = root / "raw" / source_id / "rows.csv"
    data.parent.mkdir(parents=True)
    keys = list(dict.fromkeys(key for row in rows for key in row))
    with data.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=keys)
        writer.writeheader()
        writer.writerows(rows)
    metadata = data.with_name("publisher-metadata.json")
    write_json(metadata, {"description": "Synthetic publisher card."})
    entries = [{
        "path": file.relative_to(root).as_posix(), "bytes": file.stat().st_size,
        "sha256": file_sha256(file), "role": role,
    } for file, role in ((data, "data"), (metadata, "publisher_metadata"))]
    manifest = root / "manifests" / "acquisition-manifest.json"
    write_json(manifest, {"sources": [{
        "source_id": source_id, "source_url": "https://example.invalid/dataset",
        "declared_license": "Synthetic test fixture", "revision": "synthetic-1",
        "downloaded_at": "2026-10-10T12:00:00Z", "files": entries,
    }]})
    selection = tmp_path / "selection.json"
    write_json(selection, {"version": "selection-test-v1", "sources": {
        source_id: {"category_field": "Primary Keyword", "categories": {
            "Python": "software-development",
        }},
    }})
    return root, manifest, selection


def mapping_file(tmp_path, status="APPROVED"):
    path = tmp_path / "reviewed-mappings.json"
    write_json(path, {
        "version": "mapping-test-v1", "status": status,
        "reviewer": "test-reviewer", "reviewed_at": "2026-10-10T12:00:00Z",
        "skill_aliases": {"Python": "PYTHON"},
        "degree_levels": {"Bachelor": "BACHELOR"}, "degree_order": ["BACHELOR"],
        "language_levels": {"B2": "B2"}, "language_level_order": ["B2"],
        "language_codes": ["EN"], "subject_codes": ["COMPUTER_SCIENCE"],
        "job_family_codes": ["software-development"], "project_condition_codes": ["API"],
    })
    return path


def prepared_review(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [{
        "id": "profile-1", "Primary Keyword": "Python", "Position": "Developer",
        "Moreinfo": "Python services.", "English Level": "Fluent",
        "Experience Years": "", "Name": "Synthetic Identifier",
    }])
    clean = root / "clean" / "prepared-v1"
    prepare_dataset(manifest, clean, selection)
    review = clean / "review-batch.jsonl"
    export_review_batch(clean, review)
    row = read_rows(review)[0]
    row.update({
        "status": "APPROVED", "reviewer": "test-reviewer",
        "reviewed_at": "2026-10-10T13:00:00+02:00",
        "notes": "Checked the explicit Python evidence; other areas remain unknown.",
        "job_family": "software-development", "mapping_version": "mapping-test-v1",
    })
    row["professional"]["skills"] = [{
        "skillCode": "PYTHON", "evidence": [{"source_field": "Moreinfo", "start": 0, "end": 6}],
    }]
    row["field_status"]["skills"] = "CONFIRMED"
    review.write_text(json.dumps(row) + "\n", encoding="utf-8")
    return clean, review, mapping_file(tmp_path), row


def test_normalization_preserves_programming_names_and_unicode():
    value = "  Cafe\u0301\tC++  C# .NET\r\n Go  "
    cleaned = normalize_text(value)
    assert cleaned == "Café C++ C# .NET\nGo"
    assert normalize_text(cleaned) == cleaned


def test_invalid_duration_is_conflicting_not_zero():
    invalid = observation("three-ish", "Experience Years", numeric=True)
    missing = observation(None, "Experience Years", numeric=True)
    assert invalid["state"] == "CONFLICTING" and invalid["normalized"] is None
    assert missing["state"] == "MISSING" and missing["normalized"] is None
    assert not invalid["confirmed"]


@pytest.mark.parametrize("kind", ["profile", "job"])
def test_title_only_records_do_not_collapse_distinct_people_or_jobs(kind):
    records = [SourceRecord(
        "djinni-profiles" if kind == "profile" else "djinni-jobs",
        kind, identity, "raw/rows.csv", 1,
        {"Position": "Software Engineer", "Primary Keyword": "Python"},
    ) for identity in ("one", "two")]
    assert content_fingerprint(records[0]) != content_fingerprint(records[1])


def test_dedup_preserves_all_source_references_and_selection_counts(tmp_path):
    rows = [
        {"id": "p1", "Moreinfo": "Python services.", "Primary Keyword": "Python"},
        {"id": "p2", "Moreinfo": "  Python  services. ", "Primary Keyword": "Python"},
        {"id": "p3", "Moreinfo": "Accounting.", "Primary Keyword": "Accounting"},
        {"id": "p4", "Moreinfo": "C++ and C#.", "Primary Keyword": "Python"},
    ]
    root, manifest, selection = fixture_source(tmp_path, rows)
    clean = root / "clean" / "prepared-v1"
    result = prepare_dataset(manifest, clean, selection)
    counts = result["sources"][0]["counts"]
    assert counts == {"read": 4, "selected": 3, "retained": 2, "duplicates": 1,
                      "excluded": 1, "source_identity_conflicts": 0}
    prepared = read_rows(clean / "prepared-records.jsonl")
    duplicate = read_rows(clean / "duplicate-records.jsonl")[0]
    assert duplicate["source_record_id"] == "p2"
    assert duplicate["representative_record_id"] == prepared[0]["record_id"]
    assert result["training_samples"] == 0
    assert all(item["review_status"] == "REVIEW_REQUIRED" for item in prepared)
    assert all(item["field_status"]["skills"] == "UNKNOWN" for item in prepared)
    assert all(value["value"] == "unknown"
               for value in prepared[0]["research_attributes"].values())
    assert read_rows(clean / "excluded-records.jsonl")[0]["source_record_id"] == "p3"


def test_dedup_group_identity_is_independent_of_source_row_order(tmp_path):
    rows = [
        {"id": "p1", "Moreinfo": "Python services.", "Primary Keyword": "Python"},
        {"id": "p2", "Moreinfo": "Python services.", "Primary Keyword": "Python"},
    ]
    outputs = []
    for index, source_rows in enumerate((rows, list(reversed(rows)))):
        root, manifest, selection = fixture_source(tmp_path / str(index), source_rows)
        clean = root / "clean" / "prepared-v1"
        prepare_dataset(manifest, clean, selection)
        outputs.append(read_rows(clean / "prepared-records.jsonl")[0])
    assert outputs[0]["record_id"] == outputs[1]["record_id"]
    assert outputs[0]["base_profile_key"] == outputs[1]["base_profile_key"]


def test_conflicting_source_identity_is_not_auto_selected_for_review(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [
        {"id": "same", "Moreinfo": "Python services.", "Primary Keyword": "Python"},
        {"id": "same", "Moreinfo": "Different Python projects.", "Primary Keyword": "Python"},
    ])
    clean = root / "clean" / "prepared-v1"
    result = prepare_dataset(manifest, clean, selection)
    assert result["sources"][0]["counts"]["source_identity_conflicts"] == 1
    assert len(read_rows(clean / "prepared-records.jsonl")) == 2
    assert export_review_batch(clean, clean / "review.jsonl")["profiles"] == 0


@pytest.mark.parametrize("relative", ["../raw/file.csv", "C:/file.csv", "/file.csv"])
def test_rejects_escaping_source_paths(tmp_path, relative):
    with pytest.raises(PreparationError, match="inside"):
        safe_dataset_path(tmp_path, relative)


def test_integrity_and_output_boundaries(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [{
        "id": "p1", "Moreinfo": "Python services.", "Primary Keyword": "Python",
    }])
    with pytest.raises(PreparationError, match="datasets/clean"):
        prepare_dataset(manifest, root / "raw" / "overwritten", selection)
    (root / "raw" / "djinni-profiles" / "rows.csv").write_text("changed", encoding="utf-8")
    with pytest.raises(PreparationError, match="integrity"):
        prepare_dataset(manifest, root / "clean" / "prepared-v1", selection)


def test_review_batch_does_not_invent_counts_or_confirm_facts(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [{
        "id": "p1", "Moreinfo": "Python services.", "Primary Keyword": "Python",
    }])
    clean = root / "clean" / "prepared-v1"
    prepare_dataset(manifest, clean, selection)
    path = clean / "review.jsonl"
    counts = export_review_batch(clean, path)
    assert counts == {"profiles": 1, "resumes": 0, "jobs": 0}
    row = read_rows(path)[0]
    assert row["status"] == "REVIEW_REQUIRED" and row["reviewer"] == ""
    assert row["pdf_inspection"]["ocr_needed"] is None
    with pytest.raises(PreparationError, match="overwrite"):
        export_review_batch(clean, path)


def test_reviewed_facts_exclude_original_identifiers_and_keep_unknown_states(tmp_path):
    clean, review, mapping, _ = prepared_review(tmp_path)
    output = clean / "reviewed.jsonl"
    assert apply_review_batch(clean, review, mapping, output)["approved"] == 1
    approved = read_rows(output)[0]
    assert approved["professional"]["skills"][0]["skillCode"] == "PYTHON"
    assert approved["field_status"]["education"] == "UNKNOWN"
    assert "source_values" not in approved and "Name" not in json.dumps(approved)
    assert "Fluent" not in json.dumps(approved)
    assert all(value["value"] == "unknown" for value in approved["research_attributes"].values())


@pytest.mark.parametrize(
    "change", ["name", "label", "bad_code", "bad_span", "identifier_evidence", "conflict"]
)
def test_review_rejects_leakage_or_unreviewed_corrections(tmp_path, change):
    clean, review, mapping, row = prepared_review(tmp_path)
    if change in {"name", "label"}:
        row["professional"][change] = "not a professional field"
    elif change == "bad_code":
        row["professional"]["skills"][0]["skillCode"] = "INFERRED_SKILL"
    elif change == "bad_span":
        row["professional"]["skills"][0]["evidence"][0]["end"] = 9999
    elif change == "identifier_evidence":
        row["professional"]["skills"][0]["evidence"][0]["source_field"] = "Name"
    else:
        row["field_status"]["education"] = "CONFLICTING"
    review.write_text(json.dumps(row) + "\n", encoding="utf-8")
    with pytest.raises(PreparationError):
        apply_review_batch(clean, review, mapping, clean / "reviewed.jsonl")


def test_unapproved_mapping_cannot_confirm_professional_facts(tmp_path):
    clean, review, mapping, _ = prepared_review(tmp_path)
    payload = json.loads(mapping.read_text(encoding="utf-8"))
    payload["status"] = "DRAFT"
    write_json(mapping, payload)
    with pytest.raises(PreparationError, match="approved"):
        apply_review_batch(clean, review, mapping, clean / "reviewed.jsonl")


def test_review_fingerprint_and_preparation_hash_are_checked(tmp_path):
    clean, review, mapping, row = prepared_review(tmp_path)
    row["source_fingerprint"] = "changed"
    review.write_text(json.dumps(row) + "\n", encoding="utf-8")
    with pytest.raises(PreparationError, match="evidence"):
        apply_review_batch(clean, review, mapping, clean / "reviewed.jsonl")
    with (clean / "prepared-records.jsonl").open("a", encoding="utf-8") as stream:
        stream.write("{}\n")
    with pytest.raises(PreparationError, match="changed"):
        export_review_batch(clean, clean / "another-review.jsonl")


def test_rejected_review_is_kept_separate_from_approved_records(tmp_path):
    clean, review, mapping, row = prepared_review(tmp_path)
    row["status"] = "NEEDS_CORRECTION"
    review.write_text(json.dumps(row) + "\n", encoding="utf-8")
    output = clean / "reviewed.jsonl"
    counts = apply_review_batch(clean, review, mapping, output)
    assert counts["approved"] == 0 and counts["needs_correction"] == 1
    assert read_rows(output) == []
    assert read_rows(clean / "reviewed-decisions.jsonl")[0]["status"] == "NEEDS_CORRECTION"


def test_parquet_adapter_streams_native_djinni_rows_without_reading_metadata(tmp_path):
    import pyarrow as arrow
    import pyarrow.parquet as parquet

    root, manifest, selection = fixture_source(tmp_path, [{
        "id": "p1", "Moreinfo": "Python services.", "Primary Keyword": "Python",
    }])
    payload = json.loads(manifest.read_text(encoding="utf-8"))
    entry = payload["sources"][0]["files"][0]
    csv_path = root / entry["path"]
    parquet_path = csv_path.with_suffix(".parquet")
    parquet.write_table(arrow.Table.from_pylist([{
        "id": "p1", "Moreinfo": "Python services.", "Primary Keyword": "Python",
        "Experience Years": 2.5, "English Level": None,
    }]), parquet_path)
    entry.update({"path": parquet_path.relative_to(root).as_posix(),
                  "bytes": parquet_path.stat().st_size, "sha256": file_sha256(parquet_path)})
    write_json(manifest, payload)
    clean = root / "clean" / "parquet-v1"
    result = prepare_dataset(manifest, clean, selection)
    record = read_rows(clean / "prepared-records.jsonl")[0]
    assert result["sources"][0]["counts"]["read"] == 1
    assert record["observations"]["experience"]["normalized"] == "2.5"
    assert record["observations"]["english_level"]["state"] == "MISSING"
    assert record["field_status"]["experience"] == "UNKNOWN"


def test_resume_adapter_indexes_original_pdf_without_claiming_ocr(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [{
        "ID": "123", "Category": "INFORMATION-TECHNOLOGY",
        "Resume_str": "Python services.", "Resume_html": "<p>Python services.</p>",
    }], source_id="resume-dataset")
    pdf = root / "raw" / "resume-dataset" / "data" / "data" / "INFORMATION-TECHNOLOGY" / "123.pdf"
    pdf.parent.mkdir(parents=True)
    pdf.write_bytes(b"%PDF-1.4\nSynthetic indexing fixture.\n")
    write_json(selection, {"version": "selection-test-v1", "sources": {
        "resume-dataset": {"category_field": "Category", "categories": {
            "INFORMATION-TECHNOLOGY": "information-technology",
        }},
    }})
    clean = root / "clean" / "resume-v1"
    prepare_dataset(manifest, clean, selection)
    record = read_rows(clean / "prepared-records.jsonl")[0]
    assert record["pdf"]["path"] == pdf.relative_to(root).as_posix()
    assert record["pdf"]["ocr_needed"] is None and not record["pdf"]["inspected"]
    counts = export_review_batch(clean, clean / "review.jsonl")
    assert counts["resumes"] == 1 and counts["profiles"] == 0


def test_company_description_is_not_auto_converted_to_job_requirements(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [{
        "id": "job-1", "Primary Keyword": "Python", "Position": "Developer",
        "Long Description": "Our company uses Python.", "Exp Years": "3",
    }], source_id="djinni-jobs")
    clean = root / "clean" / "jobs-v1"
    prepare_dataset(manifest, clean, selection)
    review = clean / "review.jsonl"
    export_review_batch(clean, review)
    row = read_rows(review)[0]
    assert row["requirements"] == []
    assert row["field_status"]["skills"] == "UNKNOWN"


def test_job_review_keeps_requirement_flags_and_explicit_evidence(tmp_path):
    root, manifest, selection = fixture_source(tmp_path, [{
        "id": "job-1", "Primary Keyword": "Python", "Position": "Developer",
        "Long Description": "Use Python.",
    }], source_id="djinni-jobs")
    clean = root / "clean" / "jobs-v1"
    prepare_dataset(manifest, clean, selection)
    review = clean / "review.jsonl"
    export_review_batch(clean, review)
    row = read_rows(review)[0]
    row.update({"status": "APPROVED", "reviewer": "test-reviewer",
                "reviewed_at": "2026-10-10T13:00:00Z", "notes": "Explicit assessed skill.",
                "job_family": "software-development", "mapping_version": "mapping-test-v1"})
    row["requirements"] = [{
        "criterionType": "SKILLS", "requirementCode": "REQ_PYTHON",
        "assessmentIncluded": True, "mandatory": False, "publicVisible": True,
        "payload": {"assessedSkillCodes": ["PYTHON"]},
        "evidence": [{"source_field": "Long Description", "start": 4, "end": 10}],
    }]
    row["field_status"]["skills"] = "CONFIRMED"
    review.write_text(json.dumps(row) + "\n", encoding="utf-8")
    output = clean / "reviewed.jsonl"
    apply_review_batch(clean, review, mapping_file(tmp_path), output)
    result = read_rows(output)[0]
    assert result["professional"] is None
    assert result["requirements"][0]["assessmentIncluded"]
    assert not result["requirements"][0]["mandatory"]
    assert result["audit_sources"]["review_file_sha256"] == file_sha256(review)
    assert result["source_locator"]["row"] == 1
