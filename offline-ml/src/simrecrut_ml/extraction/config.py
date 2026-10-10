"""Read only extraction settings from a literal local environment file."""

import os
import re
from dataclasses import dataclass, field
from pathlib import Path

from .models import ExtractionError


@dataclass(frozen=True)
class DeepSeekSettings:
    """Keep credentials out of repr and fix Lark's network limits."""

    api_key: str = field(repr=False)
    base_url: str = "https://api.deepseek.com"
    model: str = "deepseek-flash"
    connect_timeout: float = 5.0
    read_timeout: float = 20.0
    max_tokens: int = 10000


def load_settings(env_path: Path) -> DeepSeekSettings:
    """Load the three permitted names without executing shell expressions.

    The explicit local file overrides these process variables. Other
    backend credentials are neither returned nor injected into os.environ.
    Reject duplicates, multiline values and nonofficial network endpoints.
    """
    names = {"DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"}
    values = {key: os.environ[key] for key in names if key in os.environ}
    seen = set()
    if env_path.is_file():
        for line in env_path.read_text(encoding="utf-8-sig").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if "=" not in line:
                raise ExtractionError("INVALID_ENV_FILE")
            name, value = line.split("=", 1)
            name, value = name.strip(), value.strip()
            if name not in names:
                continue
            if name in seen:
                raise ExtractionError("DUPLICATE_EXTRACTION_SETTING")
            seen.add(name)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[name] = value
    key = values.get("DEEPSEEK_API_KEY", "")
    if not key or any(character.isspace() for character in key):
        raise ExtractionError("MISSING_OR_INVALID_API_KEY")
    url = values.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
    if url not in {"https://api.deepseek.com", "https://api.deepseek.com/v1"}:
        raise ExtractionError("UNSUPPORTED_EXTRACTION_ENDPOINT")
    model = values.get("DEEPSEEK_MODEL", "deepseek-flash")
    if not re.fullmatch(r"[a-zA-Z0-9._-]{1,80}", model):
        raise ExtractionError("INVALID_EXTRACTION_MODEL")
    return DeepSeekSettings(key, url, model)
