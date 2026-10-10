"""Define the provider boundary without exposing source paths or identities."""

from dataclasses import dataclass, field
from typing import Any, Protocol

from ..data.models import PreparationError


class ExtractionError(PreparationError):
    """Report a safe stage code without vendor bodies or personal values."""

    def __init__(self, code: str, retryable: bool = False) -> None:
        super().__init__(code)
        self.code = code
        self.retryable = retryable


@dataclass(frozen=True)
class ExtractionRequest:
    """Carry only redacted fields, kind and the versioned fact contract.

    Source identifiers, original paths, labels and job weights have no
    place in this request. Repair codes contain validation categories,
    never the rejected response or copied personal values.
    """

    kind: str
    fields: dict[str, str] = field(repr=False)
    schema: dict[str, Any] = field(repr=False)
    repair_codes: tuple[str, ...] = ()


@dataclass(frozen=True)
class ProviderReply:
    """Retain provider content locally and separate safe usage metadata."""

    content: str = field(repr=False)
    model: str
    usage: dict[str, int]


class FactExtractionProvider(Protocol):
    """Extract unconfirmed facts; callers own privacy, validation and review.

    Implementations receive redacted professional text only and return a
    JSON string. A local rule algorithm can implement this same boundary
    without changing evidence checks, draft storage or review import.
    Raise ExtractionError with a safe code on unavailable extraction.
    """

    @property
    def version(self) -> str:
        """Identify implementation and settings for cache invalidation."""
        ...

    def extract(self, request: ExtractionRequest) -> ProviderReply:
        """Return proposed facts without choosing scores or HR flags."""
        ...
