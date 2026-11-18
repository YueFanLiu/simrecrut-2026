"""Prepare the frozen research cohort without running training.

The archived 20-CV pilot stays unchanged. Select profiles and jobs before
extraction; cache responses locally and retain source evidence. Settings
and paths belong here; no command-line interface is needed.
"""

import hashlib
import json
import sys
from collections import Counter
from functools import partial
from pathlib import Path

OFFLINE_ML_PATH = Path(__file__).resolve().parents[4]
PROFILE_FILE_PATH = (
    OFFLINE_ML_PATH / "datasets/raw/djinni-profiles/data/train-00000-of-00001.parquet"
)
JOB_FILE_PATH = OFFLINE_ML_PATH / "datasets/raw/djinni-jobs/data/train-00000-of-00001.parquet"
SCHEMA_FILE_PATH = OFFLINE_ML_PATH.parent / "contracts/fact-schemas/extraction-v1.json"
BATCH = "training-500-multimodal-v1"
OUTPUT_DIRECTORY = OFFLINE_ML_PATH / "datasets/clean" / BATCH
SOURCE_REVIEW_PATH = OUTPUT_DIRECTORY / "source_reviews.parquet"
ENV_FILE_PATH = OFFLINE_ML_PATH.parent / ".env"
PROFILE_TARGETS = {
    "Java": 20,
    ".NET": 20,
    "JavaScript": 20,
    "Python": 20,
    "QA": 10,
    "QA Automation": 10,
}
JOB_TARGETS = {category: 5 for category in PROFILE_TARGETS}
SEED = 42
TEMPERATURE = 0.0
MAX_TOKENS = 8192
THINKING = False
TIMEOUT_SECONDS = 60.0
MAX_INPUT_CHARS = 100_000
ALLOW_EXTRACTION_CALLS = True

sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))

from simrecrut_ml.extraction.methods.deepseek import extract, load_settings
from simrecrut_ml.extraction.methods.extraction import (
    apply_source_reviews,
    clean_records,
    load_djinni,
    save_results,
    select_development_records,
)


def prepare_sources() -> list[dict]:
    """Freeze selected IDs and source hashes without publishing source text."""
    if BATCH == "training-100-v1":
        manifest = json.loads((OUTPUT_DIRECTORY / "manifest.json").read_text(encoding="utf-8"))
        members = manifest.get("members", [])
        if len(members) != 130 or len({row["source_record_id"] for row in members}) != 130:
            raise ValueError("The curated cohort needs 100 frozen profiles and 30 frozen jobs.")
        wanted = {row["source_record_id"]: row for row in members}
        selected = []
        for path, kind in ((PROFILE_FILE_PATH, "profile"), (JOB_FILE_PATH, "job")):
            with path.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            key = path.name + ":" + path.parent.parent.name
            if manifest["source_sha256"][key] != digest:
                raise ValueError("A curated source dataset changed after selection.")
            for row in load_djinni(path, kind):
                member = wanted.get(row["source_record_id"])
                if member is None:
                    continue
                if (
                    row["source_id"] != member["source_id"]
                    or row["row_number"] != member["row_number"]
                ):
                    raise ValueError("A curated source row no longer matches its manifest.")
                row["cohort"] = {
                    "category": member["category"],
                    "seed": SEED,
                    "selection_partition": member["selection_partition"],
                }
                selected.append(row)
        if len(selected) != 130:
            raise ValueError("Some frozen cohort members are absent from the local sources.")
        return selected
    if BATCH != "development-100":
        raise ValueError("Select training-100-v1 or development-100.")
    profiles = select_development_records(
        load_djinni(PROFILE_FILE_PATH, "profile"),
        PROFILE_TARGETS,
        SEED,
    )
    jobs = select_development_records(load_djinni(JOB_FILE_PATH, "job"), JOB_TARGETS, SEED)
    selected = profiles + jobs
    hashes = {}
    for path in (PROFILE_FILE_PATH, JOB_FILE_PATH):
        with path.open("rb") as stream:
            hashes[path.name + ":" + path.parent.parent.name] = hashlib.file_digest(
                stream,
                "sha256",
            ).hexdigest()
    manifest = {
        "cohort": "development-100",
        "seed": SEED,
        "source_sha256": hashes,
        "profile_targets": PROFILE_TARGETS,
        "job_targets": JOB_TARGETS,
        "selection": "Publisher categories, role wording, text length and experience bands.",
        "limitations": "A selected profile is not yet a complete or scoreable training case.",
        "sources": {
            "djinni-profiles": "https://huggingface.co/datasets/lang-uk/"
            "recruitment-dataset-candidate-profiles-english",
            "djinni-jobs": "https://huggingface.co/datasets/lang-uk/"
            "recruitment-dataset-job-descriptions-english",
        },
        "members": [
            {
                "source_id": row["source_id"],
                "source_record_id": row["source_record_id"],
                "row_number": row["row_number"],
                **row["cohort"],
            }
            for row in selected
        ],
    }
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    path = OUTPUT_DIRECTORY / "manifest.json"
    if path.exists():
        existing = json.loads(path.read_text(encoding="utf-8"))
        if existing["members"] != manifest["members"] or existing["source_sha256"] != hashes:
            raise ValueError("The frozen development cohort no longer matches its sources.")
    else:
        path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return selected


def main() -> dict:
    """Prepare or extract the selected batch and print aggregate counts."""
    settings = load_settings(
        ENV_FILE_PATH,
        temperature=TEMPERATURE,
        max_tokens=MAX_TOKENS,
        thinking=THINKING,
        timeout_seconds=TIMEOUT_SECONDS,
        max_input_chars=MAX_INPUT_CHARS,
    )
    if BATCH == "training-500-multimodal-v1":
        if not ALLOW_EXTRACTION_CALLS:
            raise ValueError("Enable extraction explicitly for the new source cohort.")
        from simrecrut_ml.features.methods.pilot_training import prepare_multimodal_cohort
        from simrecrut_ml.extraction.methods.extraction import prepare_synthetic_photos

        photos = prepare_synthetic_photos(OFFLINE_ML_PATH)
        if not photos["visual_reviewed"]:
            raise ValueError("Inspect the portrait contact sheets before confirming their review.")
        return prepare_multimodal_cohort(OFFLINE_ML_PATH, settings, SEED)
    records = prepare_sources()
    summary = clean_records(
        records,
        schema=json.loads(SCHEMA_FILE_PATH.read_text(encoding="utf-8")),
        extractor=partial(extract, settings=settings),
        extractor_version=settings["extractor_version"],
        output_dir=OUTPUT_DIRECTORY,
        allow_calls=ALLOW_EXTRACTION_CALLS,
    )
    if SOURCE_REVIEW_PATH.exists():
        import pyarrow.parquet as parquet

        with (OUTPUT_DIRECTORY / "records.jsonl").open(encoding="utf-8") as stream:
            prepared = [json.loads(line) for line in stream]
        reviewed = apply_source_reviews(
            prepared,
            parquet.read_table(SOURCE_REVIEW_PATH).to_pylist(),
            json.loads(SCHEMA_FILE_PATH.read_text(encoding="utf-8")),
        )
        with (OUTPUT_DIRECTORY / "responses.jsonl").open(encoding="utf-8") as stream:
            responses = [json.loads(line) for line in stream]
        save_results(reviewed, responses, OUTPUT_DIRECTORY)
        summary["statuses"] = dict(Counter(row["status"] for row in reviewed))
        summary["source_reviews"] = len(parquet.read_table(SOURCE_REVIEW_PATH))
    print(json.dumps(summary, indent=2))
    return summary


if __name__ == "__main__":
    main()
