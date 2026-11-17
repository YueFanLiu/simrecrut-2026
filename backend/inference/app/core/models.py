"""Load immutable Keras packages and enforce the shared 39-input contract."""

import hashlib
import json
import math
import threading
from pathlib import Path

import numpy as np


def sha256(path: Path) -> str:
    """Hash a package file without loading its full contents into memory."""
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


class ModelStore:
    """Keep verified models in memory; no training or database access."""

    def __init__(self, root: Path):
        import tensorflow as tf
        from tensorflow import keras
        from tensorflow.keras.preprocessing.text import tokenizer_from_json
        from simrecrut_ml.training.methods.faircv_training import load_face_encoder, validate_feature_schema

        tf.config.experimental.enable_tensor_float_32_execution(False)
        self.models, self.lock, self.encoder = {}, threading.Lock(), None
        root = root.resolve()
        for mode in ("neutral", "biased"):
            for path in sorted((root / mode).glob("*/manifest.json")):
                metadata = json.loads(path.read_text(encoding="utf-8"))
                if metadata["modelType"] != mode.upper() or metadata["inputWidth"] != 39:
                    raise ValueError("The package type or width is incompatible.")
                for name, expected in metadata["files"].items():
                    file = (path.parent / name).resolve()
                    if not file.is_relative_to(path.parent.resolve()) or sha256(file) != expected:
                        raise ValueError("A package file failed verification.")
                schema = json.loads((path.parent / "feature-schema.json").read_text(encoding="utf-8"))
                validate_feature_schema(schema)
                model = keras.models.load_model(path.parent / "model.keras", compile=False, safe_mode=True)
                if tuple(model.inputs[0].shape[1:]) != (39,):
                    raise ValueError("Unexpected model input shape.")
                model.get_layer("mandatory_gate")
                view = keras.Model(model.inputs, [model.output, model.get_layer("scoring_output").output])
                entry = {"metadata": metadata, "schema": schema, "model": view,
                    "tokenizer": tokenizer_from_json(json.dumps(metadata["tokenizer"])), "sha256": sha256(path)}
                if metadata["modelVersion"] in self.models:
                    raise ValueError("Duplicate model version.")
                for key in ("modelThreshold", "commonThreshold", "ruleThreshold"):
                    if not math.isfinite(metadata[key]) or not 0 <= metadata[key] <= 1:
                        raise ValueError("Invalid package threshold.")
                if metadata["sequenceLength"] != 270 or metadata["testEvaluated"] is not False:
                    raise ValueError("Unexpected preprocessing or evaluation status.")
                view([np.zeros((1, 39), dtype="float32"),
                      np.full((1, 270), 2, dtype="int32"),
                      np.zeros((1, 2048), dtype="float32")], training=False)
                self.models[metadata["modelVersion"]] = entry
                if mode == "biased":
                    self.encoder = load_face_encoder(path.parent / "face-encoder.h5")
        if not self.models or not all(any(e["metadata"]["modelType"] == mode for e in self.models.values())
                                      for mode in ("NEUTRAL", "BIASED")):
            raise ValueError("Both validated model packages are required.")

    def predict(self, mode: str, request: dict) -> dict:
        """Validate 39 ordered fractions and return version-bound finite scores.

        Optional text and RGB portraits follow the package preprocessing.
        Missing media use the trained missing-input paths; bad versions fail.
        """
        from simrecrut_ml.training.methods.faircv_training import encode_bios, extract_face_features
        from app.features.media import reviewed_crop

        entry = self.models.get(request["modelVersion"])
        if entry is None or entry["metadata"]["modelType"] != mode.upper():
            raise ValueError("Unknown model version.")
        metadata, schema = entry["metadata"], entry["schema"]
        if request["featureSchemaVersion"] != metadata["featureSchemaVersion"] or request["modelSha256"] != entry["sha256"]:
            raise ValueError("The model or feature schema digest does not match.")
        x = np.asarray(request["features"], dtype="float32")
        if x.shape != (39,) or not np.isfinite(x).all() or np.any((x < 0) | (x > 1)):
            raise ValueError("Expected 39 finite fractions.")
        if abs(float(x[5:10].sum()) - 1) > 1e-6 or x[15] not in (0, 1):
            raise ValueError("Invalid weights or mandatory flag.")
        if not np.allclose(x[:5] * x[5:10], x[10:15], atol=1.5e-7, rtol=0):
            raise ValueError("Contributions disagree with matches and weights.")
        if mode == "neutral":
            x[16:] = 0
        else:
            offset = 16
            for family in schema["research_families"]:
                width = len(schema["research_order"][family]); group = x[offset:offset+width]
                if not np.isin(group, (0, 1)).all() or group.sum() != 1:
                    raise ValueError("Each research family requires one category, including unknown.")
                offset += width
        text = request.get("biography") or ""
        face = np.zeros((1, 2048), dtype="float32")
        with self.lock:
            if mode == "neutral":
                text = ""
            elif request.get("portrait"):
                crop = reviewed_crop(request["portrait"]["dataBase64"])
                face = extract_face_features(self.encoder, crop[None])
            tokens = encode_bios(entry["tokenizer"], [text], metadata["sequenceLength"])
            public, raw = entry["model"]([x[None], tokens, face], training=False)
        score, probability = float(np.asarray(public)[0, 0]), float(np.asarray(raw)[0, 0])
        if not math.isfinite(score) or not math.isfinite(probability):
            raise ValueError("Non-finite model output.")
        probability = min(1-1e-7, max(1e-7, probability))
        def decision(cutoff):
            return "Accept" if x[15] == 1 and score >= cutoff else "Reject"
        return {key: request[key] for key in ("requestId", "caseId", "modelVersion", "featureSchemaVersion", "modelSha256")} | {
            "status": "SUCCESS", "logit": math.log(probability / (1-probability)),
            "acceptanceScore": score, "modelThreshold": metadata["modelThreshold"],
            "commonThreshold": metadata["commonThreshold"], "decision": decision(metadata["modelThreshold"]),
            "commonThresholdDecision": decision(metadata["commonThreshold"]),
        }
