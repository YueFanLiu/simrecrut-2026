"""Internal serving API for case-frozen recruitment simulation models."""

import hmac
import os
from contextlib import asynccontextmanager
from pathlib import Path

os.environ.setdefault("CUDA_VISIBLE_DEVICES", "-1")
os.environ.setdefault("TF_NUM_INTRAOP_THREADS", "4")
os.environ.setdefault("TF_NUM_INTEROP_THREADS", "2")

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field, FiniteFloat
from app.core.models import ModelStore
from app.features.media import portrait_candidates


class Portrait(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    mimeType: str = Field(pattern=r"^image/(jpeg|png)$")
    dataBase64: str = Field(max_length=3_000_000)


class Prediction(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    requestId: str = Field(min_length=1, max_length=128)
    caseId: str = Field(min_length=1, max_length=128)
    modelVersion: str = Field(min_length=1, max_length=64)
    featureSchemaVersion: str = Field(min_length=1, max_length=64)
    modelSha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    features: list[FiniteFloat] = Field(min_length=39, max_length=39)
    biography: str | None = Field(default=None, max_length=100_000)
    portrait: Portrait | None = None


class Pages(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    pages: list[str] = Field(max_length=2)


def create_app(factory=None, token=None) -> FastAPI:
    """Inject test providers explicitly; missing real packages never produce fake predictions."""
    secret = token if token is not None else os.environ.get("ML_INTERNAL_TOKEN", "")
    bearer = HTTPBearer(auto_error=False)

    @asynccontextmanager
    async def lifespan(app):
        if len(secret) < 32:
            raise RuntimeError("Configure a nonempty internal token of at least 32 characters.")
        app.state.store = factory() if factory else ModelStore(Path(os.environ.get("MODEL_ARTIFACTS_PATH", "model_artifacts")))
        yield

    app = FastAPI(title="SimRecrut internal inference", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)

    def authenticate(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
        if credentials is None or not hmac.compare_digest(credentials.credentials, secret):
            raise HTTPException(401, "Invalid internal credentials.")

    @app.exception_handler(RequestValidationError)
    async def invalid(request: Request, exception):
        return JSONResponse(status_code=422, content={"errorCode": "INVALID_MODEL_INPUT"})

    @app.middleware("http")
    async def bounded(request: Request, call_next):
        try:
            length = int(request.headers.get("content-length", "0"))
        except ValueError:
            return JSONResponse(status_code=400, content={"errorCode": "INVALID_LENGTH"})
        if length > 12 * 1024 * 1024:
            return JSONResponse(status_code=413, content={"errorCode": "REQUEST_TOO_LARGE"})
        if request.method == "POST":
            if "content-length" not in request.headers:
                return JSONResponse(status_code=411, content={"errorCode": "LENGTH_REQUIRED"})
            chunks, received = [], 0
            async for chunk in request.stream():
                received += len(chunk)
                if received > 12 * 1024 * 1024:
                    return JSONResponse(status_code=413, content={"errorCode": "REQUEST_TOO_LARGE"})
                chunks.append(chunk)
            request._body = b"".join(chunks)
        return await call_next(request)

    @app.get("/health/ready")
    def ready():
        return {"status": "UP", "models": len(app.state.store.models)}

    @app.get("/internal/models", dependencies=[Depends(authenticate)])
    def models():
        return [{"modelVersion": version, "modelSha256": entry["sha256"],
                 "featureSchemaVersion": entry["metadata"]["featureSchemaVersion"]}
                for version, entry in app.state.store.models.items()]

    @app.post("/internal/media/portraits", dependencies=[Depends(authenticate)])
    def portraits(body: Pages):
        try:
            return {"candidates": portrait_candidates(body.pages)}
        except (ValueError, OSError):
            raise HTTPException(422, "Invalid rendered page.")

    @app.post("/internal/predict/{mode}", dependencies=[Depends(authenticate)])
    def predict(mode: str, body: Prediction):
        if mode not in {"neutral", "biased"}:
            raise HTTPException(404, "Unknown model type.")
        try:
            return app.state.store.predict(mode, body.model_dump())
        except (ValueError, OSError):
            raise HTTPException(422, "Model version or input is incompatible.")

    return app


app = create_app()
