"""Protect exported versions and reject experiments with opened test data."""

import json
from pathlib import Path

import pytest

from simrecrut_ml.packaging.methods.model_package import export_models, file_hash


def experiment(tmp_path):
    """Create tiny synthetic files; this fixture contains no actual weights."""
    root = tmp_path / "offline-ml"
    run = root / "runs/example"
    schema = tmp_path / "contracts/feature-schemas/professional-research-v2.json"
    schema.parent.mkdir(parents=True)
    schema.write_text("{}", encoding="utf-8")
    backbone = root / "datasets/raw/pretrained/vggface2/rcmalli_vggface_tf_notop_resnet50.h5"
    backbone.parent.mkdir(parents=True)
    backbone.write_bytes(b"synthetic-backbone")
    report = {"status": "TRAINED_RESEARCH_MODELS_TEST_SEALED", "test_evaluated": False,
              "conditions": {}, "acceptance": {"failures": []}, "artifact_sha256": {},
              "feature_schema": {"schema_version": "professional-research-v2"},
              "sequence_length": 270, "label_protocol": {"rule_cutoff": ".70"},
              "tokenizer": {}, "preprocessing": {}, "training": {}, "cohort": "synthetic"}
    for mode, seed in (("neutral", 45), ("biased", 46)):
        path = run / mode / "model.keras"
        path.parent.mkdir(parents=True)
        path.write_bytes(mode.encode())
        report["conditions"][mode] = {"seeds": [
            {"seed": seed, "directory": mode, "validation": {"cutoff": .45 if mode == "neutral" else .35}}
        ]}
        report["artifact_sha256"][mode + "/model.keras"] = file_hash(path)
    (run / "run.json").write_text(json.dumps(report), encoding="utf-8")
    return root, run, tmp_path / "model_artifacts"


def test_exports_are_repeatable_and_do_not_overwrite_versions(tmp_path):
    paths = experiment(tmp_path)
    first = export_models(*paths)
    assert export_models(*paths) == first
    exported = Path(first[0]["directory"]) / "model.keras"
    exported.write_bytes(b"changed")
    with pytest.raises(FileExistsError):
        export_models(*paths)


def test_test_evaluation_or_changed_source_prevents_export(tmp_path):
    root, run, destination = experiment(tmp_path)
    report = json.loads((run / "run.json").read_text())
    report["test_evaluated"] = True
    (run / "run.json").write_text(json.dumps(report))
    with pytest.raises(ValueError):
        export_models(root, run, destination)
    report["test_evaluated"] = False
    (run / "run.json").write_text(json.dumps(report))
    (run / "neutral/model.keras").write_bytes(b"changed")
    with pytest.raises(ValueError):
        export_models(root, run, destination)
