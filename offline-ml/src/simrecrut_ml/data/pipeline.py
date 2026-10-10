"""Coordinate reproducible source preparation and local review evidence."""

import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .adapters import (
    file_sha256, iter_source_records, load_acquisition_manifest, verify_source_files,
)
from .mappings import load_reviewed_mappings, load_selection
from .models import PreparationError
from .normalization import content_fingerprint, prepare_record, source_identity


def write_json_line(stream: Any, payload: dict[str, Any]) -> None:
    """Write one UTF-8 record with stable JSON keys and finite numbers."""
    stream.write(json.dumps(payload, ensure_ascii=False, sort_keys=True, allow_nan=False) + "\n")


def clean_output_path(dataset_root: Path, output: Path) -> Path:
    """Restrict derived data and review files to datasets/clean."""
    root = (dataset_root / "clean").resolve()
    result = Path(output).resolve()
    if not result.is_relative_to(root):
        raise PreparationError("Derived data must be written inside datasets/clean.")
    return result


def prepare_dataset(
    manifest_path: Path,
    output_dir: Path,
    selection_path: Path,
    mapping_path: Path | None = None,
) -> dict[str, Any]:
    """Filter and deduplicate sources into unconfirmed local review drafts.

    Read the acquisition manifest, verify original hashes, and stream rows
    with an explicit category filter. Return aggregate counts and a local
    preparation manifest. All outputs remain REVIEW_REQUIRED; optional
    approved mappings are recorded but do not auto-approve any text.

    A new output directory is required. Reject paths outside clean data,
    existing generated files, missing provenance, and changed source files.
    No CV text is returned in diagnostics or in the aggregate manifest.
    """
    dataset_root, sources = load_acquisition_manifest(Path(manifest_path))
    output = clean_output_path(dataset_root, Path(output_dir))
    selection = load_selection(Path(selection_path))
    mapping = load_reviewed_mappings(mapping_path) if mapping_path else None
    for source in sources:
        if source["source_id"] not in selection["sources"]:
            raise PreparationError("Selection is missing an acquired source filter.")
        verify_source_files(dataset_root, source)
    if output.exists() and any(
        item.name not in {".gitkeep", "data-notes.md"} for item in output.iterdir()
    ):
        raise PreparationError(
            "Use a new clean output directory; existing data is not overwritten."
        )
    output.mkdir(parents=True, exist_ok=True)
    paths = {
        name: output / filename for name, filename in {
            "prepared": "prepared-records.jsonl",
            "duplicates": "duplicate-records.jsonl",
            "excluded": "excluded-records.jsonl",
            "issues": "issues.jsonl",
        }.items()
    }
    summaries = []
    with (
        paths["prepared"].open("w", encoding="utf-8", newline="\n") as prepared,
        paths["duplicates"].open("w", encoding="utf-8", newline="\n") as duplicates,
        paths["excluded"].open("w", encoding="utf-8", newline="\n") as excluded,
        paths["issues"].open("w", encoding="utf-8", newline="\n") as issues,
    ):
        for source in sources:
            source_id = source["source_id"]
            policy = selection["sources"][source_id]
            counts = Counter()
            categories = Counter()
            retained_categories = Counter()
            identity_fingerprints: dict[str, tuple[str, str]] = {}
            content_records: dict[str, str] = {}
            for record in iter_source_records(dataset_root, source):
                counts["read"] += 1
                category = str(record.values.get(policy["category_field"], ""))
                categories[category] += 1
                family = policy["categories"].get(category)
                if family is None:
                    counts["excluded"] += 1
                    write_json_line(excluded, {
                        "source_id": source_id, "source_record_id": record.source_record_id,
                        "source_locator": {"file": record.source_file, "row": record.row_number},
                        "category": category, "reason": "CATEGORY_NOT_SELECTED",
                    })
                    continue
                counts["selected"] += 1
                fingerprint = content_fingerprint(record)
                identity = source_identity(record)
                draft = prepare_record(record, family, selection["version"])
                previous = identity_fingerprints.get(identity)
                if previous and previous[0] != fingerprint:
                    counts["source_identity_conflicts"] += 1
                    write_json_line(issues, {
                        "reason": "SOURCE_ID_CONFLICT", "source_id": source_id,
                        "source_record_id": record.source_record_id,
                        "record_ids": [previous[1], draft["record_id"]],
                        "fingerprints": [previous[0], fingerprint],
                    })
                identity_fingerprints.setdefault(identity, (fingerprint, draft["record_id"]))
                representative = content_records.get(fingerprint)
                if representative:
                    counts["duplicates"] += 1
                    write_json_line(duplicates, {
                        "source_id": source_id, "source_record_id": record.source_record_id,
                        "source_locator": {"file": record.source_file, "row": record.row_number},
                        "source_fingerprint": fingerprint,
                        "representative_record_id": representative,
                        "reason": "EXACT_NORMALIZED_EVIDENCE",
                    })
                    continue
                content_records[fingerprint] = draft["record_id"]
                counts["retained"] += 1
                retained_categories[category] += 1
                write_json_line(prepared, draft)
            summaries.append({
                key: source[key] for key in (
                    "source_id", "source_url", "declared_license", "revision", "downloaded_at"
                )
            } | {
                "counts": {key: counts[key] for key in (
                    "read", "selected", "retained", "duplicates", "excluded",
                    "source_identity_conflicts",
                )},
                "observed_categories": dict(sorted(categories.items())),
                "retained_categories": dict(sorted(retained_categories.items())),
            })
    result = {
        "schema_version": "preparation-manifest-v1",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "acquisition_manifest_sha256": file_sha256(Path(manifest_path)),
        "selection_version": selection["version"],
        "selection_sha256": file_sha256(Path(selection_path)),
        "mapping_version": mapping["version"] if mapping else None,
        "mapping_sha256": file_sha256(mapping_path) if mapping_path else None,
        "review_status": "REVIEW_REQUIRED",
        "sources": summaries,
        "files": {
            key: {"path": path.name, "bytes": path.stat().st_size,
                  "sha256": file_sha256(path)} for key, path in paths.items()
        },
        "training_samples": 0,
    }
    manifest = output / "preparation-manifest.json"
    manifest.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return result
