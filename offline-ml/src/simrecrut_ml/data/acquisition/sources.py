"""Acquire the three publisher sources named by Lark specification revision 221."""

from __future__ import annotations

import csv
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode

from .archive import extract_dataset_archive
from .download import DownloadError, DownloadSpec, Progress, download_file, sha256_file, write_json
from .download import fetch_json

HF_SOURCES = {
    "djinni-jobs": "lang-uk/recruitment-dataset-job-descriptions-english",
    "djinni-profiles": "lang-uk/recruitment-dataset-candidate-profiles-english",
}
KAGGLE_SOURCE = "snehaanbhawal/resume-dataset"
SOURCE_IDS = (*HF_SOURCES, "resume-dataset")
MAX_CSV_FIELD_BYTES = 10_000_000


def _record_file(root: Path, path: Path, *, role: str, expected_hash: str | None = None) -> dict:
    return {"path": path.relative_to(root).as_posix(), "role": role,
            "bytes": path.stat().st_size, "sha256": sha256_file(path),
            "expected_sha256": expected_hash}


def _hugging_face(source_id: str, root: Path, progress: Progress | None) -> dict:
    repository = HF_SOURCES[source_id]
    api_url = f"https://huggingface.co/api/datasets/{repository}"
    metadata = fetch_json(api_url)
    revision = metadata["sha"]
    card = metadata["cardData"]
    if metadata.get("private") or metadata.get("gated") or card.get("license") != "mit":
        raise DownloadError("Djinni source is restricted or its declared licence has changed.")
    tree_url = f"{api_url}/tree/{revision}/data"
    tree = fetch_json(tree_url)
    entries = [entry for entry in tree if entry.get("type") == "file"]
    declared_paths = {entry["rfilename"] for entry in metadata["siblings"]
                      if entry["rfilename"].startswith("data/")}
    if {entry["path"] for entry in entries} != declared_paths:
        raise DownloadError("Publisher data-file inventory could not be resolved completely.")
    if not entries or any(not entry["path"].endswith(".parquet") for entry in entries):
        raise DownloadError("Djinni source no longer uses the expected Parquet layout.")
    raw = root / "raw" / source_id
    raw.mkdir(parents=True, exist_ok=True)
    files = []
    for entry in entries:
        relative = entry["path"]
        digest = entry.get("lfs", {}).get("oid")
        if not digest or len(digest) != 64:
            raise DownloadError("Publisher did not provide an expected data-file SHA-256.")
        url = (f"https://huggingface.co/datasets/{repository}/resolve/"
               f"{revision}/{relative}?download=true")
        path = raw / relative
        download_file(DownloadSpec(url, entry["size"], digest), path,
                      progress=progress, label=source_id)
        files.append(_record_file(root, path, role="data", expected_hash=digest))
    card_path = raw / "publisher-card.md"
    card_url = f"https://huggingface.co/datasets/{repository}/raw/{revision}/README.md"
    download_file(DownloadSpec(card_url), card_path, label=f"{source_id}-card")
    files.append(_record_file(root, card_path, role="publisher_card"))
    metadata_path = raw / "publisher-metadata.json"
    write_json(metadata_path, {"repository": metadata, "data_files": tree})
    files.append(_record_file(root, metadata_path, role="publisher_metadata"))
    info = card["dataset_info"]
    splits = info["splits"]
    manifest = {"source_id": source_id,
                "source_url": f"https://huggingface.co/datasets/{repository}",
                "metadata_url": api_url, "declared_license": "MIT", "revision": revision,
                "publisher_updated_at": metadata["lastModified"],
                "downloaded_at": datetime.now(timezone.utc).isoformat(),
                "columns": [feature["name"] for feature in info["features"]],
                "column_types": {feature["name"]: feature["dtype"]
                                 for feature in info["features"]},
                "published_row_count": sum(split["num_examples"] for split in splits),
                "published_splits": splits,
                "selection_criteria": "Complete publisher source; no training selection.",
                "retained_record_count": None, "review_status": "NOT_REVIEWED",
                "files": files, "extracted_files": []}
    return manifest


def _kaggle(root: Path, progress: Progress | None) -> dict:
    api_url = f"https://www.kaggle.com/api/v1/datasets/view/{KAGGLE_SOURCE}"
    metadata = fetch_json(api_url)
    if metadata.get("ref") != KAGGLE_SOURCE or metadata.get("isPrivate"):
        raise DownloadError("Kaggle source is restricted or its identity has changed.")
    if metadata.get("licenseName") != "CC0: Public Domain":
        raise DownloadError("Resume Dataset's publisher licence declaration has changed.")
    revision = int(metadata["currentVersionNumber"])
    url = (f"https://www.kaggle.com/api/v1/datasets/download/{KAGGLE_SOURCE}?"
           + urlencode({"datasetVersionNumber": revision}))
    raw = root / "raw" / "resume-dataset"
    raw.mkdir(parents=True, exist_ok=True)
    archive = raw / "resume-dataset.zip"
    download_file(DownloadSpec(url), archive, progress=progress, label="resume-dataset")
    with archive.open("rb") as source:
        if source.read(4) != b"PK\x03\x04":
            raise DownloadError("Resume Dataset response is not a ZIP archive.")
    files = [_record_file(root, archive, role="archive")]
    extracted = extract_dataset_archive(archive, raw)
    for entry in extracted:
        entry["path"] = (raw.relative_to(root) / str(entry["path"])).as_posix()
    csv_files = [entry for entry in extracted if str(entry["path"]).lower().endswith(".csv")]
    if len(csv_files) != 1:
        raise DownloadError("Resume Dataset must contain exactly one source CSV.")
    previous_limit = csv.field_size_limit(MAX_CSV_FIELD_BYTES)
    try:
        with (root / csv_files[0]["path"]).open(encoding="utf-8-sig", newline="") as source:
            reader = csv.DictReader(source)
            columns = reader.fieldnames
            if columns != ["ID", "Resume_str", "Resume_html", "Category"]:
                raise DownloadError(
                    "Resume Dataset CSV columns differ from the declared source schema."
                )
            rows = sum(1 for _ in reader)
    finally:
        csv.field_size_limit(previous_limit)
    metadata_path = raw / "publisher-metadata.json"
    write_json(metadata_path, metadata)
    files.append(_record_file(root, metadata_path, role="publisher_metadata"))
    card_path = raw / "publisher-card.md"
    with card_path.open("w", encoding="utf-8", newline="\n") as output:
        output.write("# Resume Dataset publisher description\n\n")
        output.write(f"Source: https://www.kaggle.com/datasets/{KAGGLE_SOURCE}\n\n")
        output.write(f"Publisher version: {revision}\n\nDeclared licence: CC0: Public Domain\n\n")
        output.write(metadata["description"].rstrip() + "\n")
    files.append(_record_file(root, card_path, role="publisher_card"))
    manifest = {"source_id": "resume-dataset",
                "source_url": f"https://www.kaggle.com/datasets/{KAGGLE_SOURCE}",
                "metadata_url": api_url, "declared_license": "CC0: Public Domain",
                "revision": str(revision),
                "publisher_updated_at": metadata.get("lastUpdated"),
                "downloaded_at": datetime.now(timezone.utc).isoformat(),
                "columns": columns, "published_row_count": None,
                "observed_row_count": rows,
                "observed_pdf_count": sum(str(entry["path"]).lower().endswith(".pdf")
                                          for entry in extracted),
                "selection_criteria": "Complete publisher source; no training selection.",
                "retained_record_count": None, "review_status": "NOT_REVIEWED",
                "files": files, "extracted_files": extracted,
                "integrity_note": ("Publisher supplies no archive SHA-256; saved hash records "
                                   "the downloaded bytes.")}
    return manifest


def acquire_dataset(
    source_id: str,
    datasets_root: Path,
    *,
    progress: Progress | None = None,
) -> dict:
    """Download one complete publisher source and save its local provenance.

    Files go under datasets_root/raw/source_id. A manifest under
    datasets_root/manifests records pinned revisions, licence declarations,
    checksums, sizes and observed schemas. No records are approved for
    training by acquisition. Unknown source IDs raise ValueError; transfer
    and source-layout failures raise DownloadError or ArchiveError.
    """

    if source_id not in SOURCE_IDS:
        raise ValueError(f"Unknown dataset source: {source_id}.")
    root = Path(datasets_root).resolve()
    manifest = (_hugging_face(source_id, root, progress) if source_id in HF_SOURCES
                else _kaggle(root, progress))
    manifest["specification_revision"] = 221
    write_json(root / "manifests" / f"{source_id}.json", manifest)
    return manifest


def acquire_all(datasets_root: Path, *, progress: Progress | None = None) -> dict:
    """Acquire the three independent sources and write a combined manifest.

    Downloads run in parallel; a failed source prevents a new combined
    manifest. Already verified files are reused on a subsequent invocation.
    The manifest contains provenance, never CV text or temporary signed URLs.
    """

    root = Path(datasets_root).resolve()
    with ThreadPoolExecutor(max_workers=3) as executor:
        pending = [executor.submit(acquire_dataset, source, root, progress=progress)
                   for source in SOURCE_IDS]
        sources = [future.result() for future in pending]
    manifest = {"manifest_version": 1, "specification_revision": 221,
                "created_at": datetime.now(timezone.utc).isoformat(), "sources": sources}
    write_json(root / "manifests" / "acquisition-manifest.json", manifest)
    return manifest
