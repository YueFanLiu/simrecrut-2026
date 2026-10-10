"""Define local research source records and preparation errors."""

from dataclasses import dataclass
from typing import Any


class PreparationError(ValueError):
    """Reject an invalid source, path, mapping, or review contract."""


@dataclass(frozen=True)
class SourceRecord:
    """Describe one publisher row before review or deduplication.

    ``values`` contains local source evidence, which may include personal
    data. It is never a professional feature vector or a training sample.
    The one-based row number locates the record without logging its text.
    """

    source_id: str
    kind: str
    source_record_id: str
    source_file: str
    row_number: int
    values: dict[str, Any]
    pdf_path: str | None = None
