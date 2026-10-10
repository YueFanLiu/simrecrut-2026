"""Keep privacy approvals and source identities separate from response caching."""

import hashlib
import json
from pathlib import Path

import pytest

from simrecrut_ml.data.models import SourceRecord
from simrecrut_ml.extraction import batch
from simrecrut_ml.extraction.models import ExtractionError

from test_provider_boundary import LocalProvider
from test_validation import blank


def test_privacy_gate_rejects_missing_review_or_changed_bytes():
    fields = {"Position": "Software engineer"}
    hashes = {key: hashlib.sha256(value.encode()).hexdigest() for key, value in fields.items()}
    review = {"record_id": "draft-fixture", "status": "PRIVACY_CHECKED",
              "original_field_sha256": hashes, "identifier_literals": [], "identifier_spans": {}}
    assert batch.verify_privacy_review(fields, "draft-fixture", review) == ((), {})
    with pytest.raises(ExtractionError, match="PRIVACY_REVIEW_REQUIRED"):
        batch.verify_privacy_review(fields, "draft-fixture", {})
    with pytest.raises(ExtractionError, match="PRIVACY_REVIEW_TEXT_CHANGED"):
        batch.verify_privacy_review({"Position": "Changed"}, "draft-fixture", review)


def test_cached_content_never_reuses_another_source_identity(tmp_path, monkeypatch):
    root = tmp_path / "datasets"
    audit = root / "clean/pilot-audit"
    audit.mkdir(parents=True)
    records = [SourceRecord("fixture", "job", str(index), "raw/fixture.json", index,
                            {"Position": "Software engineer"}) for index in (1, 2)]
    rows = [{"record_id": f"draft-{index}", "audit_index": index} for index in (1, 2)]
    monkeypatch.setattr(batch, "load_acquisition_manifest", lambda path: (root, []))
    monkeypatch.setattr(batch, "audited_records", lambda *args: iter(zip(records, rows)))
    monkeypatch.setattr(batch, "source_text_fields", lambda *args: (
        {"Position": "Software engineer"}, {"page_methods": []},
    ))
    hashes = {"Position": hashlib.sha256(b"Software engineer").hexdigest()}
    privacy = {"schema_version": "extraction-privacy-review-v1", "records": [
        {"record_id": row["record_id"], "status": "PRIVACY_CHECKED",
         "original_field_sha256": hashes, "identifier_literals": [], "identifier_spans": {}}
        for row in rows
    ]}
    path = audit / "privacy.json"
    path.write_text(json.dumps(privacy))
    schema = Path(__file__).resolve().parents[3] / "contracts/fact-schemas/extraction-v1.json"
    provider = LocalProvider([json.dumps(blank("job"))])
    output = audit / "extraction"
    summary = batch.extract_audited_batch(
        tmp_path / "manifest.json", audit, schema, output, provider, privacy_review_path=path,
    )
    assert summary["counts"]["cache_hits"] == 1 and len(provider.requests) == 1
    drafts = [json.loads(item.read_text()) for item in sorted((output / "drafts").glob("*.json"))]
    assert [value["record_id"] for value in drafts] == ["draft-1", "draft-2"]
    assert [value["source_record_id"] for value in drafts] == ["1", "2"]
    assert [value["source_locator"]["row"] for value in drafts] == [1, 2]
    cache = json.loads(next((output / "cache").glob("*.json")).read_text())
    assert "record_id" not in cache and "original_text" not in cache


def test_response_cache_cannot_grant_human_approval(tmp_path):
    path = tmp_path / "cache.json"
    path.write_text(json.dumps({"status": "REVIEW_REQUIRED", "facts": {},
                               "human_confirmed": True, "training_ready": True}),
                    encoding="utf-8")
    cached = batch._cached_reply(path)
    assert cached["human_confirmed"] is False
    assert cached["training_ready"] is False
