"""Define the DeepSeek request boundary for offline fact extraction.

All functions are placeholders. Retry budgets, source validation and
storage belong to extraction.py. Provider settings will be bound to
extract so the shared flow needs only a request-to-response callable.
"""

from pathlib import Path


def load_settings(env_path: Path) -> dict:
    """Load DeepSeek settings from local .env without exposing credentials."""
    raise NotImplementedError


def build_request(request: dict, settings: dict) -> dict:
    """Build a fact-only request from masked fields and the supplied schema."""
    raise NotImplementedError


def send_request(body: dict, settings: dict) -> dict:
    """Send one HTTPS request without provider retries or content logging."""
    raise NotImplementedError


def parse_response(response: dict) -> dict:
    """Return proposed facts, the original reply and safe usage metadata."""
    raise NotImplementedError


def extract(request: dict, *, settings: dict) -> dict:
    """Build, send and parse one request; keep scoring outside extraction."""
    raise NotImplementedError
