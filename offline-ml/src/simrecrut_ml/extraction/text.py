"""Read embedded PDF text and isolate the optional OCR boundary."""

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from .models import ExtractionError


@dataclass(frozen=True)
class DocumentText:
    """Keep exact page strings so evidence offsets remain reproducible."""

    pages: tuple[str, ...]
    methods: tuple[str, ...]


class OcrAdapter(Protocol):
    """Read one image page locally; never send its PDF to the fact provider."""

    def read_page(self, path: Path, page_number: int) -> str:
        """Return page text, using a one-based original page number."""
        ...


def usable_text(text: str) -> bool:
    """Reject empty/image text and conspicuous broken character decoding.

    This gate is conservative and does not replace visual layout review.
    A short page can still contain valid qualifications; no fixed page
    length is used to discard its evidence.
    """
    letters = sum(character.isalpha() for character in text)
    broken = text.count("\ufffd") + text.count("\x00")
    return letters >= 12 and broken / max(len(text), 1) <= 0.01


def read_pdf(path: Path, ocr: OcrAdapter | None = None) -> DocumentText:
    """Read native text before requesting OCR for unusable pages only.

    Preserve pages separately. Reject encrypted, empty or unreadable PDFs.
    If OCR is needed but no adapter is configured, report a pending stage
    instead of using the publisher CSV as if it were verified PDF text.
    """
    from pypdf import PdfReader

    try:
        reader = PdfReader(path)
        if reader.is_encrypted or not reader.pages:
            raise ExtractionError("PDF_UNREADABLE")
        pages, methods = [], []
        for number, page in enumerate(reader.pages, 1):
            text = page.extract_text() or ""
            method = "EMBEDDED"
            if not usable_text(text):
                if ocr is None:
                    raise ExtractionError("OCR_REQUIRED")
                text = ocr.read_page(path, number)
                method = "OCR"
                if not usable_text(text):
                    raise ExtractionError("OCR_TEXT_UNUSABLE")
            pages.append(text)
            methods.append(method)
        return DocumentText(tuple(pages), tuple(methods))
    except ExtractionError:
        raise
    except Exception:
        raise ExtractionError("PDF_UNREADABLE") from None
