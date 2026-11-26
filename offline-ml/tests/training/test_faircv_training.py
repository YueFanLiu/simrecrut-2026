"""Protect multimodal training, validation-only selection and saved masks."""

import hashlib
import importlib.util
import json
from pathlib import Path

import pytest

np = pytest.importorskip("numpy")
pa = pytest.importorskip("pyarrow")
pq = pytest.importorskip("pyarrow.parquet")

from simrecrut_ml.training.methods.faircv_training import (
    OOV_TOKEN,
    encode_bios,
    evaluate_scores,
    load_dataset,
    load_vectors,
    process_bios,
    select_cutoff,
    evaluate_contrasts,
    validate_feature_schema,
)

ROOT = Path(__file__).resolve().parents[3]
SCHEMA_PATH = ROOT / "contracts/feature-schemas/professional-research-v2.json"


def test_missing_biography_has_a_reserved_nonpadding_token():
    from simrecrut_ml.training.methods.faircv_training import MISSING_BIOGRAPHY_TOKEN

    train, validation, tokenizer, length = process_bios(
        ["python developer", "java"], [""], reserve_missing=True
    )
    assert tokenizer.word_index[MISSING_BIOGRAPHY_TOKEN] == 2
    assert tokenizer.word_index[OOV_TOKEN] == 1 and validation[0, 0] == 2
    assert tokenizer.word_index["python"] >= 3
    assert np.array_equal(encode_bios(tokenizer, [""], length), validation)


def test_optional_media_masks_and_mandatory_gate_survive_reload(tmp_path, schema):
    keras = pytest.importorskip("tensorflow").keras
    from simrecrut_ml.training.methods.faircv_training import base_network

    embedding = np.ones((20000, 300), dtype="float32") * 0.01
    embedding[0] = embedding[2] = 0
    model = base_network(
        embedding,
        4,
        "biased",
        schema=schema,
        photos_enabled=True,
        face_statistics={"mean": [1.0] * 2048, "variance": [1.0] * 2048},
        robust_inputs=True,
        modality_dropout=0.5,
    )
    numeric = np.ones((2, 39), dtype="float32")
    numeric[0, 15] = 0
    tokens = np.array([[2, 0, 0, 0], [1, 3, 0, 0]])
    faces = np.ones((2, 2048), dtype="float32")
    faces[0] = 0
    values = [numeric, tokens, faces]
    output = np.asarray(model(values, training=False))
    assert output[0, 0] == 0 and np.isfinite(output).all()
    masked = keras.Model(
        model.inputs,
        [model.get_layer("biography_presence").output, model.get_layer("photo_presence").output],
    )(values, training=False)
    assert np.all(np.asarray(masked[0])[0] == 0) and np.all(np.asarray(masked[1])[0] == 0)
    model.save(tmp_path / "optional.keras")
    restored = keras.models.load_model(tmp_path / "optional.keras", compile=False, safe_mode=True)
    np.testing.assert_allclose(output, restored(values, training=False), atol=1e-6)


def test_pair_loss_rewards_correct_contrasts_and_penalizes_hard_failures():
    tf = pytest.importorskip("tensorflow")
    from simrecrut_ml.training.methods.faircv_training import _paired_loss

    truth = tf.constant([[0.0, 1.0, 1.0], [1.0, 1.0, 1.0]])
    wrong = tf.Variable([[0.8], [0.2]])
    with tf.GradientTape() as tape:
        value = tf.reduce_mean(_paired_loss(truth, wrong))
    gradient = tape.gradient(value, wrong).numpy()
    assert np.isfinite(gradient).all() and gradient[0, 0] > 0 and gradient[1, 0] < 0
    assert value > tf.reduce_mean(_paired_loss(truth, tf.constant([[0.05], [0.95]])))
    penalties = _paired_loss(
        tf.constant([[0.0, 0.0, 0.0], [0.0, 1.0, 0.0]]), tf.constant([[0.8], [0.8]])
    ).numpy()
    assert penalties[0] > penalties[1]


def test_training_shuffle_keeps_contrast_rows_together():
    from simrecrut_ml.training.methods.faircv_training import _pair_dataset

    numeric = np.repeat(np.arange(6, dtype="float32")[:, None], 39, axis=1)
    truth = np.zeros((6, 3), dtype="float32")
    truth[[0, 5], 2] = 1
    metadata = [{"contrast_pair_id": "pair" if i in (0, 5) else None} for i in range(6)]
    dataset = _pair_dataset([numeric], truth, np.ones(6), metadata, 4, 42)
    seen = []
    for features, targets, weights in dataset:
        ids = features.numpy()[:, 0].astype(int).tolist()
        for a, b in zip(ids[::2], ids[1::2]):
            if a in (0, 5) or b in (0, 5):
                assert {a, b} == {0, 5}
        seen.extend(ids)
    assert sorted(seen) == list(range(6))


def test_final_gate_rejects_even_at_zero_cutoff():
    result = evaluate_scores([0, 1], [0.99, 0.8], 0, mandatory=[0, 1])
    assert result["confusion_matrix"] == [[1, 0], [0, 1]]
    assert result["raw_mandatory_failure_acceptances"] == 1
    assert result["final_mandatory_failure_acceptances"] == 0
    assert result["binary_crossentropy"] > 2


@pytest.mark.parametrize("tamper", [None, "decision", "input", "missing_seed"])
def test_saved_experiment_audit_checks_integrity_and_complete_conditions(tmp_path, tamper):
    import csv
    from simrecrut_ml.training.methods.faircv_training import evaluate_revised_experiment

    cohort = tmp_path / "datasets/clean/synthetic"
    output = tmp_path / "runs/synthetic"
    cohort.mkdir(parents=True)
    output.mkdir(parents=True)
    rows = []
    for family in ("school", "referral", "gender", "ethnicity", "name_cue"):
        for neutral in (0, 1):
            for biased in (0, 1):
                features = [0.0] * 39
                features[0], features[5], features[15] = neutral, 1.0, 1.0
                rows.append(
                    {
                        "base_profile_id": f"person-{neutral}",
                        "split": "validation",
                        "features_39": features,
                        "job_id": "job",
                        "weight_code": family,
                        "variant": f"contrast-{biased}",
                        "contrast_family": family,
                        "contrast_pair_id": f"{family}-{neutral}",
                        "panel": "ordinary",
                        "Y_N": neutral,
                        "Y_B": biased,
                    }
                )
    table = cohort / "training_cases.parquet"
    pq.write_table(pa.Table.from_pylist(rows), table)
    report = {
        "status": "TRAINED_RESEARCH_MODELS_TEST_SEALED",
        "cohort": "synthetic",
        "seeds": [42],
        "representative_seed": 42,
        "input_sha256": {table.name: hashlib.sha256(table.read_bytes()).hexdigest()},
        "conditions": {},
    }
    for mode, label in (("neutral", "Y_N"), ("biased", "Y_B")):
        path = output / mode / "seed-42"
        path.mkdir(parents=True)
        for name in ("model.keras", "best.weights.h5", "validation.json"):
            (path / name).write_bytes(b"synthetic audit fixture")
        predictions = []
        for media_mode in ("full", "no_photo", "no_biography", "no_media"):
            for row in rows:
                score = 0.95 if row[label] else 0.05
                predictions.append(
                    {
                        **{key: value for key, value in row.items() if key != "features_39"},
                        "media_mode": media_mode,
                        "raw_score": score,
                        "score": score,
                        "decision": row[label],
                    }
                )
        if tamper == "decision" and mode == "biased":
            predictions[0]["decision"] = 1 - predictions[0]["decision"]
        with (path / "validation_predictions.csv").open(
            "w", newline="", encoding="utf-8"
        ) as stream:
            writer = csv.DictWriter(stream, fieldnames=list(predictions[0]))
            writer.writeheader()
            writer.writerows(predictions)
        report["conditions"][mode] = {
            "seeds": [
                {
                    "seed": 42,
                    "directory": path.relative_to(output).as_posix(),
                    "validation": {"cutoff": 0.5},
                }
            ]
        }
    if tamper == "input":
        table.write_bytes(table.read_bytes() + b"changed")
    elif tamper == "missing_seed":
        report["seeds"].append(43)
    report_path = output / "run.json"
    report_path.write_text(json.dumps(report), encoding="utf-8")
    if tamper:
        with pytest.raises(
            ValueError,
            match={
                "decision": "saved decision",
                "input": "input changed",
                "missing_seed": "seed is missing",
            }[tamper],
        ):
            evaluate_revised_experiment(tmp_path, report_path)
    else:
        result = evaluate_revised_experiment(tmp_path, report_path)
        assert result["passed"] and result["representative_models_passed"]
        assert not result["test_evaluated"] and result["validation_people"] == 2
        saved = json.loads(report_path.read_text(encoding="utf-8"))
        assert saved["validation_summary"]["biased"]["no_media"]["macro_f1"]["mean"] == 1
        assert len(saved["artifact_sha256"]) == 8


def test_reversed_contrast_is_not_a_correct_bias_flip():
    rows = [
        {
            "contrast_pair_id": "case",
            "contrast_family": "referral",
            "variant": "contrast-a",
            "Y_B": 0,
            "final_decision": 1,
            "score": 0.7,
        },
        {
            "contrast_pair_id": "case",
            "contrast_family": "referral",
            "variant": "contrast-b",
            "Y_B": 1,
            "final_decision": 0,
            "score": 0.3,
        },
    ]
    result = evaluate_contrasts(rows, label="Y_B")["referral"]
    assert result["correct_flips"] == 0
    assert result["wrong_direction_flips"] == 1
    assert result["false_flips"] == result["missed_flips"] == 1


def test_complete_reader_joins_only_development_portraits(tmp_path, schema):
    path = tmp_path / "training_cases.parquet"
    rows = write_table(path, schema)
    for row in rows:
        row["photo_id"] = row["base_profile_id"]
    table = pa.Table.from_pylist(rows).replace_schema_metadata(
        {
            b"feature_schema_version": schema["schema_version"].encode(),
        }
    )
    pq.write_table(table, path)
    faces = [
        {
            "photo_id": row["photo_id"],
            "split": row["split"],
            "features": [1.0] * 2048 if row["split"] != "test" else [float("nan")],
        }
        for row in rows
    ]
    pq.write_table(pa.Table.from_pylist(faces), tmp_path / "face_features.parquet")
    data = load_dataset(path, "biased", schema=schema, photos_enabled=True)
    assert data["train"][2].shape == (2, 2048)
    assert np.all(data["validation"][2] == 1)
    faces[0]["split"] = "validation"
    pq.write_table(pa.Table.from_pylist(faces), tmp_path / "face_features.parquet")
    with pytest.raises(ValueError, match="crosses a person split"):
        load_dataset(path, "biased", schema=schema, photos_enabled=True)


def test_photo_network_updates_both_media_branches(tmp_path, schema):
    from tensorflow import keras
    from simrecrut_ml.training.methods.faircv_training import base_network, verify_roundtrip

    keras.utils.set_random_seed(42)
    embedding = np.random.default_rng(42).normal(size=(20000, 300)).astype("float32") * 0.1
    model = base_network(
        embedding,
        4,
        "biased",
        schema=schema,
        photos_enabled=True,
        face_statistics={"mean": [0.0] * 2048, "variance": [1.0] * 2048},
    )
    inputs = [
        np.ones((2, 39), dtype="float32"),
        np.array([[1, 2, 0, 0], [2, 3, 4, 0]]),
        np.ones((2, 2048), dtype="float32"),
    ]
    check = verify_roundtrip(model, tmp_path / "full.keras", inputs, "biased", photos_enabled=True)
    assert check["photo_projection_trainable"]
    before = [a.copy() for a in model.get_layer("face_projection").get_weights()]
    text_before = [a.copy() for a in model.get_layer("biography_encoder").get_weights()]
    frozen_before = model.get_layer("fasttext").get_weights()[0].copy()
    model.compile(optimizer="adam", loss="binary_crossentropy")
    model.train_on_batch(inputs, np.array([[0.0], [1.0]], dtype="float32"))
    assert any(
        not np.array_equal(a, b)
        for a, b in zip(before, model.get_layer("face_projection").get_weights())
    )
    assert any(
        not np.array_equal(a, b)
        for a, b in zip(text_before, model.get_layer("biography_encoder").get_weights())
    )
    np.testing.assert_array_equal(frozen_before, model.get_layer("fasttext").get_weights()[0])


def test_neutral_training_view_shares_head_and_keeps_media_frozen(schema):
    keras = pytest.importorskip("tensorflow").keras
    from simrecrut_ml.training.methods.faircv_training import base_network, _neutral_training_view

    keras.utils.set_random_seed(42)
    embedding = np.random.default_rng(42).normal(size=(20000, 300)).astype("float32") * 0.1
    model = base_network(
        embedding,
        4,
        "neutral",
        schema=schema,
        photos_enabled=True,
        face_statistics={"mean": [1.0] * 2048, "variance": [2.0] * 2048},
    )
    inputs = [
        np.ones((2, 39), dtype="float32"),
        np.array([[1, 2, 0, 0], [2, 3, 4, 0]]),
        np.ones((2, 2048), dtype="float32") * 3,
    ]
    view = _neutral_training_view(model)
    frozen = {
        name: [value.copy() for value in model.get_layer(name).get_weights()]
        for name in ("face_projection", "biography_encoder", "fasttext")
    }
    before = np.asarray(model(inputs, training=False))
    np.testing.assert_allclose(before, view(inputs, training=False), atol=1e-6)
    assert model.get_layer("scoring_output") is view.get_layer("scoring_output")
    view.compile(optimizer="adam", loss="binary_crossentropy")
    view.train_on_batch(inputs, np.array([[0.0], [0.0]], dtype="float32"))
    after = np.asarray(model(inputs, training=False))
    assert not np.array_equal(before, after)
    np.testing.assert_allclose(after, view(inputs, training=False), atol=1e-6)
    for name, values in frozen.items():
        for old, current in zip(values, model.get_layer(name).get_weights()):
            np.testing.assert_array_equal(old, current)


@pytest.fixture
def schema():
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


def write_table(path, schema, mutate=None):
    numeric = [1, 1, 0, 1, 0, 1 / 3, 1 / 3, 0, 1 / 3, 0, 1 / 3, 1 / 3, 0, 1 / 3, 0, 1]
    numeric += [0] * 23
    for index in (18, 21, 24, 31, 38):
        numeric[index] = 1
    rows = []
    for split in ("train", "validation"):
        for label in (0, 1):
            values = list(numeric)
            values[15] = label
            rows.append(
                {
                    "base_profile_id": f"{split}-{label}",
                    "split": split,
                    "features_39": values,
                    "biography": "python developer",
                    "face_embedding": None,
                    "face_features_2048": None,
                    "Y_N": label,
                    "Y_B": label,
                }
            )
    rows.append(
        {
            "base_profile_id": "reserved-test-person",
            "split": "test",
            "features_39": None,
            "biography": "reserved content",
            "face_embedding": None,
            "face_features_2048": None,
            "Y_N": 9,
            "Y_B": 9,
        }
    )
    if mutate:
        mutate(rows)
    table = pa.Table.from_pylist(rows).replace_schema_metadata(
        {
            b"feature_schema_version": schema["schema_version"].encode(),
        }
    )
    pq.write_table(table, path)
    return rows


def test_schema_declares_the_reviewed_name_extension(schema):
    validate_feature_schema(schema)
    assert schema["input_order"][19] == "referral_yes"
    assert schema["input_order"][32] == "name_cue_east_asian"
    assert schema["input_order"][-1] == "name_cue_unknown"
    schema["input_order"][32], schema["input_order"][33] = (
        schema["input_order"][33],
        schema["input_order"][32],
    )
    with pytest.raises(ValueError, match="input order"):
        validate_feature_schema(schema)


def test_reader_filters_before_materialising_features(tmp_path, schema, monkeypatch):
    path = tmp_path / "cases.parquet"
    write_table(path, schema)
    read = pq.read_table
    calls = []

    def filtered(*args, **kwargs):
        calls.append(kwargs.get("filters"))
        return read(*args, **kwargs)

    monkeypatch.setattr(pq, "read_table", filtered)
    biased = load_dataset(path, "biased", schema=schema)
    neutral = load_dataset(path, "neutral", schema=schema)
    assert calls == [[("split", "in", ["train", "validation"])]] * 2
    assert set(biased) == {"train", "validation"}
    assert biased["train"][0].shape == (2, 39)
    assert not neutral["train"][0][:, 16:].any()
    assert neutral["train"][1] == ["", ""]
    assert not biased["validation"][2].any()


@pytest.mark.parametrize(
    "change,message",
    [
        (lambda rows: rows[0]["features_39"].__setitem__(18, 0), "categorical encoding"),
        (lambda rows: rows[0].__setitem__("face_features_2048", [0.0] * 2048), "null photo"),
        (lambda rows: rows[0].__setitem__("Y_B", 1), "failed mandatory"),
        (lambda rows: rows[0]["features_39"].__setitem__(10, 0.8), "contributions"),
        (lambda rows: rows[2].__setitem__("base_profile_id", "train-0"), "split boundaries"),
    ],
)
def test_invalid_development_rows_are_rejected(tmp_path, schema, change, message):
    path = tmp_path / "cases.parquet"
    write_table(path, schema, change)
    with pytest.raises(ValueError, match=message):
        load_dataset(path, "biased", schema=schema)


def test_unknown_words_survive_without_expanding_the_training_vocabulary():
    pytest.importorskip("tensorflow")
    train, validation, tokenizer, length = process_bios(
        ["python developer python", "java"], ["unseenword python"]
    )
    assert length == 3 and tokenizer.word_index[OOV_TOKEN] == 1
    assert "unseenword" not in tokenizer.word_index
    assert validation[0].tolist() == [1, tokenizer.word_index["python"], 0]
    assert train[1].tolist() == [tokenizer.word_index["java"], 0, 0]


def test_fasttext_fallback_uses_only_matched_training_vectors(tmp_path):
    path = tmp_path / "vectors.vec"
    path.write_text(
        "2 300\npython " + " ".join(["1"] * 300) + "\njava " + " ".join(["3"] * 300) + "\n",
        encoding="utf-8",
    )
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    index = {OOV_TOKEN: 1, "python": 2, "java": 3, "missing": 4}
    matrix, report = load_vectors(path, index, expected_sha256=digest)
    assert not matrix[0].any()
    assert np.all(matrix[2] == 1) and np.all(matrix[3] == 3)
    assert np.all(matrix[1] == 2) and np.all(matrix[4] == 2)
    assert report["matched_words"] == 2 and report["missing_words"] == 1
    with pytest.raises(ValueError, match="source manifest"):
        load_vectors(path, index, expected_sha256="0" * 64)


def test_binary_metrics_and_validation_cutoff():
    metrics = evaluate_scores([0, 0, 1, 1], [0.1, 0.8, 0.4, 0.9])
    assert metrics["confusion_matrix"] == [[1, 1], [1, 1]]
    assert metrics["accuracy"] == metrics["balanced_accuracy"] == metrics["macro_f1"] == 0.5
    selected = select_cutoff([0, 0, 1, 1], [0.1, 0.3, 0.7, 0.9])
    assert selected["cutoff"] == 0.5 and selected["balanced_accuracy"] == 1
    assert selected["source"] == "validation"
    assert evaluate_scores([0, 0], [0.2, 0.3])["balanced_accuracy"] is None
    with pytest.raises(ValueError, match="both validation classes"):
        select_cutoff([1, 1], [0.2, 0.4])
    with pytest.raises(ValueError, match="finite scores"):
        evaluate_scores([0, 1], [0.5, np.nan])


def test_training_keeps_best_checkpoint_and_epoch_log(tmp_path):
    keras = pytest.importorskip("tensorflow").keras
    from types import SimpleNamespace
    from simrecrut_ml.training.methods.faircv_training import train_hiring_tool

    captured = {}

    class Model:
        def get_layer(self, name):
            return SimpleNamespace(trainable=False)

        def compile(self, **kwargs):
            captured["compile"] = kwargs

        def fit(self, *args, **kwargs):
            captured.update(kwargs)
            return "recorded"

    batch = (np.zeros((2, 39)), np.zeros((2, 3)), np.zeros((2, 2048)), np.array([[0], [1]]))
    result = train_hiring_tool(
        Model(),
        batch,
        batch,
        learning_rate=0.001,
        batch_size=32,
        max_epochs=100,
        patience=10,
        output_directory=tmp_path / "condition",
    )
    assert result == "recorded" and captured["verbose"] == 2
    checkpoint = next(
        c for c in captured["callbacks"] if isinstance(c, keras.callbacks.ModelCheckpoint)
    )
    assert checkpoint.save_best_only and checkpoint.save_weights_only
    assert checkpoint.monitor == "val_loss" and checkpoint.filepath.endswith("best.weights.h5")
    assert any(isinstance(c, keras.callbacks.CSVLogger) for c in captured["callbacks"])
    assert any(
        isinstance(c, keras.callbacks.EarlyStopping) and c.restore_best_weights
        for c in captured["callbacks"]
    )


def test_preflight_never_calls_training_and_roundtrips_both_masks(tmp_path, schema, monkeypatch):
    tensorflow = pytest.importorskip("tensorflow")
    from simrecrut_ml.training.methods import faircv_training

    module_path = ROOT / "offline-ml/src/simrecrut_ml/training/scripts/train_faircv.py"
    spec = importlib.util.spec_from_file_location("pilot_training_operation", module_path)
    operation = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(operation)
    table_path = tmp_path / "training_cases.parquet"
    write_table(table_path, schema)
    manifest = {
        "cohort": "synthetic-no-photo",
        "acceptance": {
            "artifact_sha256": {
                table_path.name: hashlib.sha256(table_path.read_bytes()).hexdigest()
            },
            "training_splits": {"train": 2, "validation": 2},
            "protocol": "PILOT_UNCALIBRATED",
            "cutoff": "0.70",
            "referral_yes_effect": "0.08",
            "other_effects": "0",
            "rule_version": "synthetic",
        },
    }
    (tmp_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    pretrained = tmp_path / "pretrained"
    pretrained.mkdir()
    vector_path = pretrained / "small.vec"
    vector_path.write_text(
        "2 300\npython "
        + " ".join(["0.1"] * 300)
        + "\ndeveloper "
        + " ".join(["0.2"] * 300)
        + "\n",
        encoding="utf-8",
    )
    (pretrained / "manifest.json").write_text(
        json.dumps(
            {
                "assets": [
                    {
                        "file": "fasttext/crawl-300d-2M.vec",
                        "sha256": hashlib.sha256(vector_path.read_bytes()).hexdigest(),
                    }
                ]
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(operation, "COHORT_PATH", tmp_path)
    monkeypatch.setattr(operation, "TRAINING_TABLE_PATH", table_path)
    monkeypatch.setattr(operation, "PRETRAINED_PATH", pretrained)
    monkeypatch.setattr(operation, "WORD_VECTOR_PATH", vector_path)
    monkeypatch.setattr(operation, "RUN_PATH", tmp_path / "runs")
    monkeypatch.setattr(operation, "ALLOW_TRAINING", False)

    def forbidden(*args, **kwargs):
        pytest.fail("The preflight must never invoke model fitting.")

    monkeypatch.setattr(faircv_training, "train_hiring_tool", forbidden)
    monkeypatch.setattr(tensorflow.keras.Model, "fit", forbidden)
    report = operation.main()
    assert report["status"] == "PREFLIGHT_NOT_TRAINED" and not report["test_data_loaded"]
    assert report["conditions"]["neutral"]["trainable_parameters"] == 5801
    assert report["conditions"]["biased"]["trainable_parameters"] == 91049
    for metrics in report["conditions"].values():
        assert metrics["weights_unchanged"] and metrics["gradient_updates"] == 0
        assert metrics["photo_input_invariant"] and metrics["tokenizer_roundtrip_equal"]
    assert len(list((tmp_path / "runs").rglob("untrained-roundtrip.keras"))) == 2
