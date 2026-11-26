"""Export immutable serving packages from completed development experiments."""

import hashlib
import json
import shutil
from pathlib import Path


def file_hash(path: Path) -> str:
    """Hash artifact bytes with bounded memory use."""
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def export_models(root: Path, run: Path, destination: Path) -> list[dict]:
    """Copy selected weights and frozen preprocessing; never fit or read test cases.

    Existing version directories must contain identical bytes. Keep the
    original experiment report unchanged, including its representative seed.
    """
    report = json.loads((run / "run.json").read_text(encoding="utf-8"))
    if report["status"] != "TRAINED_RESEARCH_MODELS_TEST_SEALED" or report["test_evaluated"]:
        raise ValueError("Export only the completed, test-sealed development run.")
    outcomes = []
    for mode, seed in (("neutral", 45), ("biased", 46)):
        result = next(item for item in report["conditions"][mode]["seeds"] if item["seed"] == seed)
        if any(item["seed"] == seed and item["mode"] == mode for item in report["acceptance"]["failures"]):
            raise ValueError("The selected model failed development acceptance.")
        version = f"{mode}-20261117-s{seed}-v1"
        target = destination / mode / version
        target.mkdir(parents=True, exist_ok=True)
        source = run / result["directory"] / "model.keras"
        expected = report["artifact_sha256"][result["directory"] + "/model.keras"]
        if file_hash(source) != expected:
            raise ValueError("The selected checkpoint changed after validation.")
        copies = {"model.keras": source, "feature-schema.json": root.parent / "contracts/feature-schemas/professional-research-v2.json"}
        if mode == "biased":
            copies["face-encoder.h5"] = root / "datasets/raw/pretrained/vggface2/rcmalli_vggface_tf_notop_resnet50.h5"
        for name, original in copies.items():
            final = target / name
            if final.exists() and file_hash(final) != file_hash(original):
                raise FileExistsError("Do not overwrite an existing model version.")
            if not final.exists():
                shutil.copyfile(original, final)
        manifest = {
            "formatVersion": 1, "modelVersion": version, "modelType": mode.upper(),
            "featureSchemaVersion": report["feature_schema"]["schema_version"],
            "inputWidth": 39, "sequenceLength": report["sequence_length"],
            "modelThreshold": result["validation"]["cutoff"], "commonThreshold": 0.5,
            "ruleThreshold": float(report["label_protocol"]["rule_cutoff"]),
            "tokenizer": report["tokenizer"], "preprocessing": report["preprocessing"],
            "labelProtocol": report["label_protocol"], "training": report["training"],
            "sourceRun": run.name, "sourceCohort": report["cohort"], "seed": seed,
            "validation": result["validation"], "testEvaluated": False,
            "scope": "LOCAL_RESEARCH_SIMULATION_UNCALIBRATED",
            "files": {name: file_hash(target / name) for name in copies},
        }
        encoded = json.dumps(manifest, indent=2) + "\n"
        path = target / "manifest.json"
        if path.exists() and path.read_text(encoding="utf-8") != encoded:
            raise FileExistsError("The serving manifest is immutable within a version.")
        path.write_text(encoded, encoding="utf-8", newline="\n")
        outcomes.append({"version": version, "directory": str(target), "manifestSha256": file_hash(path)})
    return outcomes
