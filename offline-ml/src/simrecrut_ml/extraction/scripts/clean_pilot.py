"""Check the selected 20 CVs and 20 jobs using the shared extraction flow."""

import json
import sys
from pathlib import Path

# Direct execution resolves the local namespace package from this script.
SOURCE_ROOT = Path(__file__).resolve().parents[3]
if str(SOURCE_ROOT) not in sys.path:
    sys.path.insert(0, str(SOURCE_ROOT))

from simrecrut_ml.extraction.methods.deepseek import DeepSeekFactProvider, load_settings
from simrecrut_ml.extraction.methods.extraction import (
    TesseractOcr, clean_records, load_pilot_records, prepare_images, run_image_trial,
)


OFFLINE_ROOT = SOURCE_ROOT.parent
PROJECT_ROOT = OFFLINE_ROOT.parent
DATASETS = OFFLINE_ROOT / "datasets"
MANIFEST = DATASETS / "manifests/acquisition-manifest.json"
OUTPUT = DATASETS / "clean/pilot"
SCHEMA = PROJECT_ROOT / "contracts/fact-schemas/extraction-v1.json"
SELECTION = OFFLINE_ROOT / "configs/data-selection.json"
REFERENCE_MONTH = "2026-10"
OCR_MODE = "compare"
OCR_LANGUAGE = "eng"
ALLOW_API_CALLS = False
RUN_MODE = "clean"


def main() -> None:
    """Run source-bound checks and print aggregate progress only."""
    records = load_pilot_records(MANIFEST, OUTPUT / "records.jsonl", SELECTION)
    if sum(record.kind == "resume" for record, _ in records) != 20 or (
        sum(record.kind == "job" for record, _ in records) != 20
    ):
        raise ValueError("The pilot must contain exactly 20 CVs and 20 jobs.")
    provider = DeepSeekFactProvider(load_settings(PROJECT_ROOT / ".env"))
    progress = lambda value: print(json.dumps(value), flush=True)
    if RUN_MODE == "prepare_images":
        print(json.dumps(prepare_images(OUTPUT / "records.jsonl", DATASETS, progress=progress)))
        return
    if RUN_MODE == "compare_images":
        if not ALLOW_API_CALLS:
            raise ValueError("Enable API calls explicitly for the image comparison.")
        print(json.dumps(run_image_trial(OUTPUT / "records.jsonl", SCHEMA, provider,
                                         progress=progress)))
        return
    if RUN_MODE != "clean":
        raise ValueError("Use clean, prepare_images or compare_images as RUN_MODE.")
    ocr = TesseractOcr(DATASETS / "raw/ocr/tessdata", OCR_LANGUAGE)
    summary = clean_records(
        records, MANIFEST, SCHEMA, OUTPUT, provider, ocr=ocr, text_mode=OCR_MODE,
        reference_month=REFERENCE_MONTH, allow_api_calls=ALLOW_API_CALLS,
        progress=progress,
    )
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
