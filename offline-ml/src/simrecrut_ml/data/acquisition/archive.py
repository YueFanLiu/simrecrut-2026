"""Extract publisher archives without accepting executable or unsafe entries."""

from __future__ import annotations

import os
import re
import shutil
import stat
import tempfile
from pathlib import Path, PurePosixPath
from zipfile import ZipFile

from .download import sha256_file


class ArchiveError(ValueError):
    """Reject an archive whose contents cannot be safely imported."""


def _safe_parts(name: str) -> tuple[str, ...]:
    path = PurePosixPath(name)
    reserved = re.compile(r"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)", re.IGNORECASE)
    if (path.is_absolute() or "\\" in name or not path.parts
            or any(part in (".", "..") for part in name.split("/"))):
        raise ArchiveError("Archive entry has an invalid relative path.")
    for part in path.parts:
        if (any(character in part for character in ':*?"<>|')
                or any(ord(character) < 32 for character in part)
                or part.endswith((".", " "))
                or reserved.match(part)):
            raise ArchiveError("Archive entry has an unsafe path component.")
    return path.parts


def extract_dataset_archive(
    archive_path: Path,
    destination: Path,
    *,
    max_files: int = 10_000,
    max_uncompressed_bytes: int = 2_000_000_000,
) -> list[dict[str, object]]:
    """Import only CSV/PDF data after validating the complete ZIP inventory.

    Reject traversal, symlinks, duplicate paths, encrypted members and size
    limits before writing. CSV and PDF files are copied as bytes, never
    executed. Existing files must have identical contents. Return relative
    paths, sizes and SHA-256 values for provenance.
    """

    destination = Path(destination).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    with ZipFile(archive_path) as archive:
        members = []
        seen: set[str] = set()
        total = 0
        for entry in archive.infolist():
            parts = _safe_parts(entry.orig_filename)
            key = "/".join(parts).casefold()
            if key in seen:
                raise ArchiveError("Archive contains duplicate paths.")
            seen.add(key)
            mode = entry.external_attr >> 16
            if stat.S_ISLNK(mode) or (stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR)):
                raise ArchiveError("Archive contains a non-regular entry.")
            if entry.flag_bits & 1:
                raise ArchiveError("Encrypted archive members are unsupported.")
            if entry.is_dir():
                continue
            if PurePosixPath(entry.filename).suffix.lower() not in (".csv", ".pdf"):
                raise ArchiveError("Dataset archive contains a file other than CSV or PDF.")
            total += entry.file_size
            members.append((entry, parts))
        if len(members) > max_files or total > max_uncompressed_bytes:
            raise ArchiveError("Dataset archive exceeds the extraction limits.")
        inventory: list[dict[str, object]] = []
        with tempfile.TemporaryDirectory(prefix=".dataset-extract-", dir=destination) as staging:
            stage = Path(staging)
            for entry, parts in members:
                staged = stage.joinpath(*parts)
                staged.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(entry) as source, staged.open("wb") as output:
                    shutil.copyfileobj(source, output, length=1024 * 1024)
                if staged.stat().st_size != entry.file_size:
                    raise ArchiveError("Extracted member size differs from the ZIP inventory.")
                inventory.append({"path": "/".join(parts), "bytes": entry.file_size,
                                  "sha256": sha256_file(staged)})
            for entry in inventory:
                relative = str(entry["path"])
                target = destination.joinpath(*PurePosixPath(relative).parts)
                if not target.resolve().is_relative_to(destination):
                    raise ArchiveError("Extraction destination escapes the dataset directory.")
                if target.is_symlink() or any(parent.is_symlink() for parent in target.parents
                                             if parent != destination.parent):
                    raise ArchiveError("Extraction destination contains a symbolic link.")
                if target.exists():
                    if not target.is_file() or sha256_file(target) != entry["sha256"]:
                        raise ArchiveError("Existing dataset file differs from the archive.")
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                os.replace(stage.joinpath(*PurePosixPath(relative).parts), target)
        return inventory
