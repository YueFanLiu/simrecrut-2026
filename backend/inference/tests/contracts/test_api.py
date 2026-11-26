"""Check the internal HTTP contract without private weights or CV fixtures."""

import base64
import io
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from app.api.main import create_app
from app.features.media import reviewed_crop, portrait_candidates


class Store:
    """Expose an explicit test double; production never selects it."""

    models = {"test": {"sha256": "a" * 64, "metadata": {"featureSchemaVersion": "S"}}}

    def predict(self, mode, body):
        if body["modelVersion"] != "test":
            raise ValueError("Unknown version.")
        return {"mode": mode, "status": "SUCCESS"}


@pytest.fixture
def client():
    with TestClient(create_app(Store, "x" * 32)) as client:
        yield client


def payload():
    """Return synthetic input with no personal data."""
    return {"requestId": "request", "caseId": "case", "modelVersion": "test",
            "featureSchemaVersion": "S", "modelSha256": "a" * 64, "features": [0] * 39}


def test_credentials_are_required(client):
    assert client.post("/internal/predict/neutral", json=payload()).status_code == 401
    assert client.get("/internal/models").status_code == 401
    assert client.get("/health/ready").json()["status"] == "UP"


@pytest.mark.parametrize("field,value", [("features", [0] * 38), ("features", [0] * 40),
                                         ("features", [float("inf")] * 39),
                                         ("modelSha256", "bad"), ("unexpected", "private")])
def test_invalid_inputs_do_not_echo_content(client, field, value):
    import json
    body = payload() | {field: value}
    response = client.post("/internal/predict/biased", content=json.dumps(body),
                           headers={"Authorization": "Bearer " + "x" * 32,
                                    "Content-Type": "application/json"})
    assert response.status_code == 422
    assert response.json() == {"errorCode": "INVALID_MODEL_INPUT"}


def test_unknown_versions_fail_and_missing_media_are_valid(client):
    headers = {"Authorization": "Bearer " + "x" * 32}
    assert client.post("/internal/predict/biased", json=payload(), headers=headers).status_code == 200
    assert client.post("/internal/predict/biased", json=payload() | {"modelVersion": "absent"},
                       headers=headers).status_code == 422
    assert client.post("/internal/predict/other", json=payload(), headers=headers).status_code == 404


def test_size_limits_apply_before_parsing(client):
    assert client.post("/internal/predict/biased", content=b"x",
                       headers={"Content-Length": str(13 * 1024 * 1024)}).status_code == 413
    assert client.post("/internal/predict/biased", content=iter([b"{}"])).status_code == 411


def test_missing_credentials_fail_startup():
    with pytest.raises(RuntimeError):
        with TestClient(create_app(Store, "")):
            pass


def test_portrait_pixels_and_image_limits():
    out = io.BytesIO()
    Image.new("RGB", (300, 400), (20, 40, 60)).save(out, format="PNG")
    encoded = base64.b64encode(out.getvalue()).decode()
    crop = reviewed_crop(encoded)
    assert crop.shape == (224, 224, 3)
    assert tuple(crop[0, 0]) == (20, 40, 60)
    assert portrait_candidates([encoded]) == []
    with pytest.raises(ValueError):
        reviewed_crop("invalid-base64!")
