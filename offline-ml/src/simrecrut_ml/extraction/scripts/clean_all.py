"""Prepare all selected CV, job and profile rows using shared methods.

Set paths and the extractor here; no command-line arguments are needed.
Load DeepSeek settings once; calls remain disabled until enabled here.
Deduplication buffers selected rows to detect late source ID conflicts.
"""

import json
import sys
from functools import partial
from itertools import chain
from pathlib import Path


OFFLINE_ML_PATH = Path(__file__).resolve().parents[4]
RESUME_FILE_PATH = (
    Path(__file__).resolve().parents[4]
    / "datasets/raw/resume-dataset/Resume/Resume.csv"
)
DJINNI_FILE_PATH = (
    Path(__file__).resolve().parents[4]
    / "datasets/raw/djinni-jobs/data/train-00000-of-00001.parquet"
)
DJINNI_PROFILE_FILE_PATH = (
    OFFLINE_ML_PATH / "datasets/raw/djinni-profiles/data/train-00000-of-00001.parquet"
)
SELECTION_FILE_PATH = OFFLINE_ML_PATH / "configs/data-selection.json"
SCHEMA_FILE_PATH = OFFLINE_ML_PATH.parent / "contracts/fact-schemas/extraction-v1.json"
OUTPUT_DIRECTORY = OFFLINE_ML_PATH / "datasets/clean/full"
ENV_FILE_PATH = OFFLINE_ML_PATH.parent / ".env"
TEMPERATURE = 0.0
MAX_TOKENS = 8192
THINKING = False
TIMEOUT_SECONDS = 60.0
MAX_INPUT_CHARS = 100_000
ALLOW_EXTRACTION_CALLS = False

sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))

from simrecrut_ml.extraction.methods.deepseek import extract, load_settings
from simrecrut_ml.extraction.methods.extraction import (
    clean_records,
    deduplicate_records,
    load_djinni,
    load_resume_dataset,
    select_records,
)


def main() -> dict:
    """Prepare each selected source and print aggregate counts only."""
    settings = load_settings(
        ENV_FILE_PATH, temperature=TEMPERATURE, max_tokens=MAX_TOKENS,
        thinking=THINKING, timeout_seconds=TIMEOUT_SECONDS, max_input_chars=MAX_INPUT_CHARS,
    )
    extractor = partial(extract, settings=settings)
    selection = json.loads(SELECTION_FILE_PATH.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA_FILE_PATH.read_text(encoding="utf-8"))
    records = chain(
        select_records(load_resume_dataset(RESUME_FILE_PATH), selection),
        select_records(load_djinni(DJINNI_FILE_PATH, "job"), selection),
        select_records(load_djinni(DJINNI_PROFILE_FILE_PATH, "profile"), selection),
    )
    summary = clean_records(
        deduplicate_records(records), schema=schema,
        extractor=extractor, extractor_version=settings["extractor_version"],
        output_dir=OUTPUT_DIRECTORY, allow_calls=ALLOW_EXTRACTION_CALLS,
    )
    print(json.dumps(summary, indent=2))
    return summary


if __name__ == "__main__":
    main()
