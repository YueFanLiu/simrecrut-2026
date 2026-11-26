"""Train the complete research cohort or inspect an archived no-photo batch."""

import hashlib
import json
import sys
from datetime import datetime, timezone
from importlib.metadata import version
from pathlib import Path
from uuid import uuid4

OFFLINE_ML_PATH = Path(__file__).resolve().parents[4]
COHORT_PATH = OFFLINE_ML_PATH / "datasets/clean/training-500-multimodal-v3"
TRAINING_TABLE_PATH = COHORT_PATH / "training_cases.parquet"
FEATURE_SCHEMA_PATH = (
    OFFLINE_ML_PATH.parent / "contracts/feature-schemas/professional-research-v2.json"
)
PRETRAINED_PATH = OFFLINE_ML_PATH / "datasets/raw/pretrained"
WORD_VECTOR_PATH = PRETRAINED_PATH / "fasttext/crawl-300d-2M.vec"
# Archived no-photo runs use this output path.
RUN_PATH = OFFLINE_ML_PATH / "runs/training-100-v1/no-photo"
MODES = ("neutral", "biased")
ALLOW_TRAINING = True
SEED = 42
LEARNING_RATE = 0.0003
LEARNING_RATES = (LEARNING_RATE, 0.0001, 0.001)
SEEDS = (42, 43, 44, 45, 46)
BATCH_SIZE = 32
MAX_EPOCHS = 100
PATIENCE = 10
REVISED_BATCH_SIZE = 64
WARMUP_EPOCHS = 60
MULTIMODAL_EPOCHS = 50
REVISED_PATIENCE = 8


def main() -> dict:
    """Use development rows only; save diagnostic models when training is disabled."""
    if COHORT_PATH.name in {"training-500-multimodal-v2", "training-500-multimodal-v3"}:
        if not ALLOW_TRAINING:
            raise ValueError("Enable training explicitly after development acceptance.")
        sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))
        from simrecrut_ml.training.methods.faircv_training import (
            evaluate_revised_experiment,
            run_revised_experiment,
        )

        summary = run_revised_experiment(
            OFFLINE_ML_PATH,
            COHORT_PATH,
            seeds=SEEDS,
            batch_size=REVISED_BATCH_SIZE,
            warmup_epochs=WARMUP_EPOCHS,
            max_epochs=MULTIMODAL_EPOCHS,
            patience=REVISED_PATIENCE,
        )
        summary["acceptance"] = evaluate_revised_experiment(
            OFFLINE_ML_PATH, Path(summary["report"])
        )
        print(json.dumps(summary, indent=2))
        return summary
    if COHORT_PATH.name == "training-500-multimodal-v1":
        if not ALLOW_TRAINING:
            raise ValueError("Enable training explicitly after the full data acceptance check.")
        sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))
        from simrecrut_ml.training.methods.faircv_training import (
            audit_trained_models,
            run_multimodal_experiment,
        )

        summary = run_multimodal_experiment(
            OFFLINE_ML_PATH,
            seeds=SEEDS,
            learning_rates=LEARNING_RATES,
            batch_size=BATCH_SIZE,
            max_epochs=MAX_EPOCHS,
            patience=PATIENCE,
        )
        summary["common_validation"] = audit_trained_models(
            OFFLINE_ML_PATH,
            Path(summary["report"]),
        )
        print(json.dumps(summary, indent=2))
        return summary
    import numpy as np
    from tensorflow import keras
    from tensorflow.keras.preprocessing.text import tokenizer_from_json

    sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))

    from simrecrut_ml.training.methods.faircv_training import (
        base_network,
        encode_bios,
        evaluate_scores,
        load_dataset,
        load_vectors,
        process_bios,
        select_cutoff,
        train_hiring_tool,
        validate_feature_schema,
        verify_roundtrip,
    )

    schema_bytes = FEATURE_SCHEMA_PATH.read_bytes()
    schema = json.loads(schema_bytes)
    validate_feature_schema(schema)
    manifest_path = COHORT_PATH / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    table_hash = _file_hash(TRAINING_TABLE_PATH)
    if table_hash != manifest["acceptance"]["artifact_sha256"][TRAINING_TABLE_PATH.name]:
        raise ValueError("The training table changed after the cohort acceptance check.")
    if set(MODES) != {"neutral", "biased"} or len(MODES) != 2:
        raise ValueError("Run each condition exactly once with an independent model.")
    source = load_dataset(TRAINING_TABLE_PATH, "biased", schema=schema)
    for split in ("train", "validation"):
        if len(source[split][0]) != manifest["acceptance"]["training_splits"][split]:
            raise ValueError("The development rows no longer match the frozen cohort.")
    bios_train, bios_val, tokenizer, length = process_bios(
        source["train"][1], source["validation"][1]
    )
    validation_sequences = tokenizer.texts_to_sequences(source["validation"][1])
    validation_tokens = sum(map(len, validation_sequences))
    validation_unknown = sum(sequence.count(1) for sequence in validation_sequences)
    assets = json.loads((PRETRAINED_PATH / "manifest.json").read_text(encoding="utf-8"))
    vector_asset = next(
        item for item in assets["assets"] if item["file"] == "fasttext/crawl-300d-2M.vec"
    )
    print("Loading local fastText vectors for the training vocabulary; no downloads.", flush=True)
    embedding, coverage = load_vectors(
        WORD_VECTOR_PATH, tokenizer.word_index, expected_sha256=vector_asset["sha256"]
    )
    run_kind = "training" if ALLOW_TRAINING else "preflight"
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = RUN_PATH / run_kind / f"{stamp}-seed{SEED}-{uuid4().hex[:6]}"
    output.mkdir(parents=True, exist_ok=False)
    report = {
        "status": "TRAINING_IN_PROGRESS" if ALLOW_TRAINING else "PREFLIGHT_NOT_TRAINED",
        "run_directory": str(output),
        "training_run": ALLOW_TRAINING,
        "test_data_loaded": False,
        "cohort": manifest["cohort"],
        "seed": SEED,
        "sequence_length": length,
        "training_table_sha256": table_hash,
        "cohort_manifest_sha256": _file_hash(manifest_path),
        "feature_schema": schema,
        "feature_schema_sha256": hashlib.sha256(schema_bytes).hexdigest(),
        "label_protocol": {
            key: manifest["acceptance"][key]
            for key in (
                "protocol",
                "cutoff",
                "referral_yes_effect",
                "other_effects",
                "rule_version",
            )
        },
        "capabilities": {
            "photos_enabled": False,
            "photo_projection_trained": False,
            "research_families_varied_in_data": ["referral"],
            "formal_bias_protocol_calibrated": False,
        },
        "learning_rate": LEARNING_RATE,
        "batch_size": BATCH_SIZE,
        "max_epochs": MAX_EPOCHS,
        "patience": PATIENCE,
        "fasttext": coverage,
        "faircvtest_revision": "0a12cdf061af68ea3fa3957c29c43683c1566fbd",
        "face_projection": "Frozen 2048-to-20 projection; output masked to zero inside the model.",
        "input_widths": {"structured": 39, "face": 20, "biography": 64, "combined": 123},
        "tokenizer": json.loads(tokenizer.to_json()),
        "text_preprocessing": {
            "padding": "post",
            "truncating": "post",
            "mask_zero": True,
            "unknown_index": 1,
            "validation_tokens": validation_tokens,
            "validation_unknown_tokens": validation_unknown,
            "validation_unknown_fraction": validation_unknown / max(1, validation_tokens),
            "validation_truncated_rows": sum(len(row) > length for row in validation_sequences),
        },
        "dependencies": {
            name: version(name) for name in ("tensorflow-cpu", "keras", "numpy", "pyarrow", "h5py")
        },
        "development_rows": {split: len(parts[0]) for split, parts in source.items()},
        "conditions": {},
    }
    for mode in MODES:
        print(f"Checking {mode}; training enabled: {ALLOW_TRAINING}.", flush=True)
        keras.backend.clear_session()
        keras.utils.set_random_seed(SEED)
        data = (
            source if mode == "biased" else load_dataset(TRAINING_TABLE_PATH, mode, schema=schema)
        )
        numeric_train, _, faces_train, labels_train = data["train"]
        numeric_val, _, faces_val, labels_val = data["validation"]
        tokens_train = bios_train if mode == "biased" else np.zeros_like(bios_train)
        tokens_val = bios_val if mode == "biased" else np.zeros_like(bios_val)
        model = base_network(embedding, length, mode, schema=schema)
        condition_path = output / mode
        if ALLOW_TRAINING:
            history = train_hiring_tool(
                model,
                (numeric_train, tokens_train, faces_train, labels_train),
                (numeric_val, tokens_val, faces_val, labels_val),
                learning_rate=LEARNING_RATE,
                batch_size=BATCH_SIZE,
                max_epochs=MAX_EPOCHS,
                patience=PATIENCE,
                output_directory=condition_path,
            )
            model.load_weights(condition_path / "best.weights.h5")
            train_scores = np.asarray(
                model([numeric_train, tokens_train, faces_train], training=False)
            )
            val_scores = np.asarray(model([numeric_val, tokens_val, faces_val], training=False))
            selected = select_cutoff(labels_val, val_scores)
            metrics = {
                "history": history.history,
                "epochs_completed": len(history.history["loss"]),
                "best_epoch": int(np.argmin(history.history["val_loss"])) + 1,
                "best_validation_loss": float(np.min(history.history["val_loss"])),
                "best_weights": str((condition_path / "best.weights.h5").relative_to(output)),
                "epoch_log": str((condition_path / "epochs.csv").relative_to(output)),
                "validation_cutoff": selected,
                "training_at_0_5": evaluate_scores(labels_train, train_scores),
                "validation_at_0_5": evaluate_scores(labels_val, val_scores),
                "mandatory_failure_acceptances": int(
                    np.sum(
                        (numeric_val[:, 15] == 0) & (val_scores.reshape(-1) >= selected["cutoff"])
                    )
                ),
            }
            model.save(condition_path / "model.keras")
        else:
            sample = [
                np.concatenate((a[:2], b[:2]))
                for a, b in (
                    (numeric_train, numeric_val),
                    (tokens_train, tokens_val),
                    (faces_train, faces_val),
                )
            ]
            metrics = verify_roundtrip(
                model, condition_path / "untrained-roundtrip.keras", sample, mode
            )
            restored_tokenizer = tokenizer_from_json(json.dumps(report["tokenizer"]))
            for texts in (source["train"][1][:2], source["validation"][1][:2]):
                np.testing.assert_array_equal(
                    encode_bios(tokenizer, texts, length),
                    encode_bios(restored_tokenizer, texts, length),
                )
            metrics["tokenizer_roundtrip_equal"] = True
        report["conditions"][mode] = metrics
        if ALLOW_TRAINING:
            (output / "run.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if ALLOW_TRAINING:
        report["status"] = "TRAINED_PILOT"
    name = "run.json" if ALLOW_TRAINING else "preflight.json"
    (output / name).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "status": report["status"],
                "report": str(output / name),
                "development_rows": report["development_rows"],
                "fasttext_matched_fraction": coverage["matched_fraction"],
                "conditions": (
                    report["conditions"] if not ALLOW_TRAINING else list(report["conditions"])
                ),
                "test_data_loaded": False,
            },
            indent=2,
        )
    )
    return report


def _file_hash(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


if __name__ == "__main__":
    main()
