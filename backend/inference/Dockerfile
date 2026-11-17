FROM python:3.12-slim-bookworm
WORKDIR /app
COPY backend/inference/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt
COPY offline-ml/src/simrecrut_ml /app/offline/src/simrecrut_ml
COPY backend/inference/app /app/app
ENV PYTHONPATH=/app/offline/src:/app \
    PYTHONDONTWRITEBYTECODE=1 \
    CUDA_VISIBLE_DEVICES=-1 \
    MODEL_ARTIFACTS_PATH=/app/model_artifacts
RUN useradd --system --uid 10002 --home-dir /app simrecrut
USER simrecrut
EXPOSE 8000
CMD ["uvicorn", "app.api.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
