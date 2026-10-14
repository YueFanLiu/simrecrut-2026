"""Prepare the category-selected full sources through the same cleaner."""

import json
import sys
from pathlib import Path

# Direct execution resolves the local namespace package from this script.
SOURCE_ROOT = Path(__file__).resolve().parents[3]
if str(SOURCE_ROOT) not in sys.path:
    sys.path.insert(0, str(SOURCE_ROOT))

from simrecrut_ml.extraction.methods.deepseek import DeepSeekFactProvider, load_settings
from simrecrut_ml.extraction.methods.extraction import (
    TesseractOcr, clean_records, iter_selected_records, read_records,
)


OFFLINE_ROOT = SOURCE_ROOT.parent
PROJECT_ROOT = OFFLINE_ROOT.parent
DATASETS = OFFLINE_ROOT / "datasets"
MANIFEST = DATASETS / "manifests/acquisition-manifest.json"
SELECTION = OFFLINE_ROOT / "configs/data-selection.json"
SCHEMA = PROJECT_ROOT / "contracts/fact-schemas/extraction-v1.json"
OUTPUT = DATASETS / "clean/full"
REFERENCE_MONTH = "2026-10"
OCR_MODE = "auto"
OCR_LANGUAGE = "eng"
LIMIT = None
ALLOW_API_CALLS = False


def selected_records():
    """Reuse source checks from the pilot by exact publisher identity.

    Remaining records stay held until their external-disclosure checks
    exist. This is separate from factual checks or training labels.
    """
    pilot = {(row["source_id"], row["source_record_id"]): row
             for row in read_records(DATASETS / "clean/pilot/records.jsonl")}
    for record, metadata in iter_selected_records(MANIFEST, SELECTION):
        previous = pilot.get((record.source_id, record.source_record_id))
        if previous is not None and metadata["source_fingerprint"] == previous[
            "source_fingerprint"
        ] and not metadata.get("source_issue"):
            metadata = {**metadata, **previous}
        yield record, metadata


def main() -> None:
    """Clean local full-source records with settings defined above.

    API calls start disabled so the first full pass exposes unresolved
    inputs before sending a large batch. Change the constant explicitly
    after checking the local results and privacy bindings.
    """
    provider = DeepSeekFactProvider(load_settings(PROJECT_ROOT / ".env"))
    summary = clean_records(
        selected_records(), MANIFEST, SCHEMA, OUTPUT, provider,
        ocr=TesseractOcr(DATASETS / "raw/ocr/tessdata", OCR_LANGUAGE),
        text_mode=OCR_MODE, reference_month=REFERENCE_MONTH,
        allow_api_calls=ALLOW_API_CALLS, limit=LIMIT,
        progress=lambda value: print(json.dumps(value), flush=True)
        if value["completed"] % 100 == 0 else None,
    )
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
