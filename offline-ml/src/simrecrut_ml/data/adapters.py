"""Read publisher files without deciding their professional meaning."""

import csv
import hashlib
import json
import math
from pathlib import Path
from typing import Any, Iterator

from .models import PreparationError, SourceRecord


SOURCE_KINDS = {
    "djinni-jobs": "job",
    "djinni-profiles": "profile",
    "resume-dataset": "resume",
}


def safe_dataset_path(dataset_root: Path, relative_path: str) -> Path:
    """Resolve a relative file inside the datasets tree.

    Reject absolute paths, parent traversal, and escaping symlinks before
    reading publisher files or resolving a resume PDF index.
    """
    portable = relative_path.replace("\\", "/")
    relative = Path(portable)
    if (
        relative.is_absolute() or portable.startswith("/")
        or ":" in portable or ".." in relative.parts
    ):
        raise PreparationError("Dataset paths must stay inside the datasets tree.")
    root = dataset_root.resolve()
    result = (root / relative).resolve()
    if not result.is_relative_to(root):
        raise PreparationError("Dataset path resolves outside the datasets tree.")
    return result


def file_sha256(path: Path) -> str:
    """Hash a file in chunks without loading it all into memory."""
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_acquisition_manifest(path: Path) -> tuple[Path, list[dict[str, Any]]]:
    """Read a single-source or combined acquisition manifest.

    The manifest must live in ``datasets/manifests``. File paths are
    relative to ``datasets``. Source metadata records the publisher URL,
    declared license, actual revision, and acquisition time.
    """
    path = Path(path).resolve()
    if path.parent.name != "manifests":
        raise PreparationError("Acquisition manifests must be in datasets/manifests.")
    payload = json.loads(path.read_text(encoding="utf-8"))
    sources = payload.get("sources", [payload])
    if not isinstance(sources, list) or not sources:
        raise PreparationError("The acquisition manifest has no source entries.")
    seen = set()
    for source in sources:
        required = ("source_id", "source_url", "declared_license", "revision", "downloaded_at")
        if not isinstance(source, dict) or any(not source.get(key) for key in required):
            raise PreparationError("A source is missing acquisition provenance.")
        if source["source_id"] not in SOURCE_KINDS or source["source_id"] in seen:
            raise PreparationError("Unsupported or repeated dataset source.")
        seen.add(source["source_id"])
        if not isinstance(source.get("files"), list) or not source["files"]:
            raise PreparationError("A source has no acquired files.")
    return path.parent.parent, sources


def verify_source_files(dataset_root: Path, source: dict[str, Any]) -> None:
    """Check every acquired file against its recorded size and SHA-256."""
    entries = source["files"] + source.get("extracted_files", [])
    for entry in entries:
        path = safe_dataset_path(dataset_root, entry["path"])
        if not path.is_file():
            raise PreparationError("An acquired dataset file is missing.")
        if path.stat().st_size != entry["bytes"] or file_sha256(path) != entry["sha256"]:
            raise PreparationError("An acquired dataset file failed its integrity check.")


def _json_value(value: Any) -> Any:
    if value is None or isinstance(value, (str, bool, int)):
        return value
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, dict):
        return {str(key): _json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_value(item) for item in value]
    if hasattr(value, "isoformat"):
        return value.isoformat()
    raise PreparationError("A publisher field has an unsupported value type.")


def _rows(path: Path) -> Iterator[dict[str, Any]]:
    if path.suffix.lower() == ".csv":
        csv.field_size_limit(16 * 1024 * 1024)
        with path.open(encoding="utf-8-sig", newline="") as stream:
            yield from csv.DictReader(stream)
    elif path.suffix.lower() in {".jsonl", ".ndjson"}:
        with path.open(encoding="utf-8") as stream:
            for line in stream:
                if line.strip():
                    row = json.loads(line)
                    if not isinstance(row, dict):
                        raise PreparationError("Publisher rows must be JSON objects.")
                    yield row
    elif path.suffix.lower() == ".json":
        payload = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(payload, list) or any(not isinstance(row, dict) for row in payload):
            raise PreparationError("A publisher JSON file must contain a row array.")
        yield from payload
    elif path.suffix.lower() == ".parquet":
        try:
            import pyarrow.parquet as parquet
        except ImportError as error:
            raise PreparationError("Parquet preparation requires pyarrow.") from error
        for batch in parquet.ParquetFile(path).iter_batches(batch_size=1024):
            for row in batch.to_pylist():
                yield {key: _json_value(value) for key, value in row.items()}
    else:
        raise PreparationError("Unsupported publisher data format.")


def iter_source_records(
    dataset_root: Path, source: dict[str, Any]
) -> Iterator[SourceRecord]:
    """Stream identified rows and link original resume PDFs when present.

    Djinni uses ``id``; Resume Dataset uses ``ID``. Missing identities are
    rejected rather than replaced by row positions. PDF availability and
    OCR need are separate: this adapter never claims that OCR is needed.
    """
    source_id = source["source_id"]
    kind = SOURCE_KINDS[source_id]
    entries = source.get("extracted_files", []) or source["files"]
    data_files = [
        entry for entry in entries
        if entry.get("role", "data") == "data"
        if Path(entry["path"]).suffix.lower() in {".csv", ".parquet", ".jsonl", ".ndjson", ".json"}
    ]
    if not data_files:
        raise PreparationError("The acquired source has no supported row file.")
    for entry in sorted(data_files, key=lambda item: item["path"]):
        path = safe_dataset_path(dataset_root, entry["path"])
        for number, row in enumerate(_rows(path), start=1):
            identity = row.get("ID" if kind == "resume" else "id")
            if identity is None or str(identity).strip() == "":
                raise PreparationError("A publisher record has no source identity.")
            pdf_path = None
            if kind == "resume":
                category = str(row.get("Category", ""))
                candidate = (
                    f"raw/resume-dataset/data/data/{category}/{identity}.pdf"
                )
                pdf = safe_dataset_path(dataset_root, candidate)
                if pdf.is_file():
                    pdf_path = candidate
            yield SourceRecord(
                source_id, kind, str(identity), entry["path"], number,
                {key: _json_value(value) for key, value in row.items()}, pdf_path,
            )
