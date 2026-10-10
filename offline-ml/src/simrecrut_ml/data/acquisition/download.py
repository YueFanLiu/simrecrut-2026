"""Download pinned source files without exposing partial files to readers."""

from __future__ import annotations

import hashlib
import http.client
import json
import os
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

Progress = Callable[[str, int, int | None], None]
CHUNK_SIZE = 1024 * 1024
USER_AGENT = "simrecrut-2026-dataset-acquisition/1.0"


class DownloadError(RuntimeError):
    """Report an incomplete or invalid source download without its URL."""


@dataclass(frozen=True)
class DownloadSpec:
    """Describe a stable publisher URL and its available integrity checks."""

    url: str
    expected_bytes: int | None = None
    expected_sha256: str | None = None


def sha256_file(path: Path) -> str:
    """Hash a local file in bounded memory."""

    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(CHUNK_SIZE), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: object) -> None:
    """Replace a UTF-8 JSON file only after the new contents are written."""

    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_name(path.name + ".tmp")
    with pending.open("w", encoding="utf-8", newline="\n") as destination:
        json.dump(value, destination, ensure_ascii=False, indent=2)
        destination.write("\n")
        destination.flush()
        os.fsync(destination.fileno())
    pending.replace(path)


def fetch_json(url: str, timeout: float = 60) -> object:
    """Read publisher metadata; reject non-JSON responses and hide URLs on errors."""

    try:
        with urlopen(Request(url, headers={"User-Agent": USER_AGENT}), timeout=timeout) as reply:
            return json.load(reply)
    except (HTTPError, URLError, ValueError) as error:
        status = error.code if isinstance(error, HTTPError) else "unavailable"
        raise DownloadError(f"Publisher metadata request failed ({status}).") from None


def _verified(path: Path, spec: DownloadSpec, identity: dict[str, object]) -> bool:
    if not path.is_file() or (spec.expected_bytes is not None
                              and path.stat().st_size != spec.expected_bytes):
        return False
    digest = sha256_file(path)
    if spec.expected_sha256 is not None:
        return digest == spec.expected_sha256
    receipt = path.with_name(path.name + ".download.json")
    try:
        saved = json.loads(receipt.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return False
    return (saved.get("identity") == identity and saved.get("sha256") == digest
            and saved.get("bytes") == path.stat().st_size)


def download_file(
    spec: DownloadSpec,
    destination: Path,
    *,
    progress: Progress | None = None,
    label: str = "source",
    attempts: int = 3,
    timeout: float = 60,
) -> dict[str, object]:
    """Resume and verify a file before atomically replacing its destination.

    A server must confirm the requested byte range. Resume uses its ETag
    or Last-Modified validator, or a pinned expected SHA-256. Failed
    transfers retain their partial file; failed integrity checks remove it.
    Progress receives only the caller's label and byte counts. A publisher
    redirect may contain a temporary credential and is never persisted.
    """

    if attempts < 1:
        raise ValueError("Download attempts must be positive.")
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    identity = {"url": spec.url, "bytes": spec.expected_bytes, "sha256": spec.expected_sha256}
    if _verified(destination, spec, identity):
        return {"bytes": destination.stat().st_size, "sha256": sha256_file(destination)}
    partial = destination.with_name(destination.name + ".part")
    state_path = destination.with_name(destination.name + ".part.json")
    for attempt in range(attempts):
        state: dict[str, object] = {}
        if state_path.is_file():
            try:
                state = json.loads(state_path.read_text(encoding="utf-8"))
            except (ValueError, OSError):
                pass
        valid_state = all(state.get(key) == value for key, value in identity.items())
        etag = state.get("etag")
        validator = (etag if etag and not str(etag).startswith("W/")
                     else state.get("last_modified"))
        offset = partial.stat().st_size if partial.exists() and valid_state else 0
        if not validator and not spec.expected_sha256:
            offset = 0
        headers = {"User-Agent": USER_AGENT, "Accept-Encoding": "identity"}
        if offset:
            headers["Range"] = f"bytes={offset}-"
            if validator:
                headers["If-Range"] = str(validator)
        try:
            with urlopen(Request(spec.url, headers=headers), timeout=timeout) as reply:
                content_type = reply.headers.get("Content-Type", "").lower()
                if "text/html" in content_type or "application/json" in content_type:
                    raise DownloadError("Publisher returned a page instead of a dataset file.")
                status = getattr(reply, "status", 200)
                expected_total = spec.expected_bytes
                if status == 206:
                    match = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+)",
                                         reply.headers.get("Content-Range", ""))
                    if not match or int(match[1]) != offset:
                        raise DownloadError("Publisher returned an inconsistent byte range.")
                    total = int(match[3])
                    if expected_total is not None and total != expected_total:
                        raise DownloadError(
                            "Publisher file size changed from its pinned metadata."
                        )
                    expected_total = total
                elif status == 200:
                    offset = 0
                    length = reply.headers.get("Content-Length")
                    if length:
                        total = int(length)
                        if expected_total is not None and total != expected_total:
                            raise DownloadError("Publisher file size differs from its metadata.")
                        expected_total = total
                else:
                    raise DownloadError(f"Unexpected download response ({status}).")
                write_json(state_path, {**identity, "etag": reply.headers.get("ETag"),
                                        "last_modified": reply.headers.get("Last-Modified")})
                completed = offset
                last_notice = time.monotonic()
                with partial.open("ab" if offset else "wb") as output:
                    while chunk := reply.read(CHUNK_SIZE):
                        output.write(chunk)
                        completed += len(chunk)
                        if expected_total is not None and completed > expected_total:
                            raise DownloadError("Download exceeds the declared file size.")
                        if progress and time.monotonic() - last_notice >= 5:
                            progress(label, completed, expected_total)
                            last_notice = time.monotonic()
                    output.flush()
                    os.fsync(output.fileno())
                if expected_total is not None and completed != expected_total:
                    raise OSError("Incomplete dataset response.")
                digest = sha256_file(partial)
                if spec.expected_sha256 and digest != spec.expected_sha256:
                    partial.unlink(missing_ok=True)
                    state_path.unlink(missing_ok=True)
                    raise DownloadError("Download checksum differs from the publisher SHA-256.")
                partial.replace(destination)
                write_json(destination.with_name(destination.name + ".download.json"),
                           {"identity": identity, "bytes": completed, "sha256": digest})
                state_path.unlink(missing_ok=True)
                if progress:
                    progress(label, completed, expected_total)
                return {"bytes": completed, "sha256": digest}
        except HTTPError as error:
            if error.code == 416:
                partial.unlink(missing_ok=True)
                state_path.unlink(missing_ok=True)
            elif error.code not in (408, 429, 500, 502, 503, 504):
                raise DownloadError(f"Publisher refused the download ({error.code}).") from None
        except (URLError, OSError, TimeoutError, http.client.IncompleteRead):
            pass
        if attempt + 1 < attempts:
            time.sleep(min(2 ** attempt, 5))
    raise DownloadError(f"Dataset transfer did not complete after {attempts} attempts.")
