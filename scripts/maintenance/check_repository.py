"""Check the authored repository's language, documentation links, and layout."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit


HAN = re.compile(
    "[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff"
    "\U00020000-\U0002fa1f\U00030000-\U000323af]"
)
MARKDOWN_LINK = re.compile(r"\[[^\]\n]*\]\(([^)\n]+)\)")
BINARY_SUFFIXES = {
    ".bin", ".docx", ".gif", ".ico", ".jar", ".jpeg", ".jpg", ".npz",
    ".onnx", ".pdf", ".png", ".pt", ".pth", ".safetensors", ".webp", ".zip",
}
MARKDOWN_EXCEPTIONS = {"README.md", "offline-ml/datasets/raw/data-sources.md"}
# User-facing integration reports are intentionally written in Chinese.
CHINESE_REPORTS = {
    "docs/development/page-api-integration.md",
    "docs/development/page-buttons-business-test.md",
}
FRONTEND_MESSAGES = "frontend/src/i18n/messages.json"
CHINESE_API_MESSAGES = "backend/java/src/main/resources/i18n/messages_zh.properties"


def language_errors(label: str, text: str) -> list[str]:
    """Allow Chinese in declared translation values, never in arbitrary source."""
    if label == FRONTEND_MESSAGES:
        try:
            entries = json.loads(text)
        except ValueError:
            return [f"{label}: invalid translation JSON"]
        if not isinstance(entries, dict):
            return [f"{label}: translations must be an object"]
        errors = []
        for source, translations in entries.items():
            if HAN.search(source):
                errors.append(f"{label}: translation source keys must be English")
            if not isinstance(translations, dict) or set(translations) != {"zh", "fr"}:
                errors.append(f"{label}: each entry requires zh and fr translations")
                continue
            for locale, value in translations.items():
                if not isinstance(value, str) or not value.strip():
                    errors.append(f"{label}: translation values must be nonempty text")
                elif locale != "zh" and HAN.search(value):
                    errors.append(f"{label}: Chinese text is only allowed in zh values")
        return errors
    errors = []
    for number, line in enumerate(text.splitlines(), 1):
        if not HAN.search(line) or label in CHINESE_REPORTS:
            continue
        if label == CHINESE_API_MESSAGES and "=" in line and not line.lstrip().startswith(("#", "!")):
            key = line.split("=", 1)[0]
            if key.strip() and not HAN.search(key):
                continue
        errors.append(f"{label}:{number}: text contains Han characters")
    return errors


def repository_files(root: Path) -> list[Path]:
    """Read Git's file list so private ignored files never enter check output."""
    result = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    names = result.stdout.decode("utf-8").split("\0")
    return sorted({Path(name) for name in names if name})


def local_link_errors(root: Path, relative: Path, text: str) -> list[str]:
    """Check file links without fetching URLs or requiring generated artifacts."""
    errors = []
    # Fenced examples may demonstrate syntax rather than reference real files.
    prose = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    for match in MARKDOWN_LINK.finditer(prose):
        target = match.group(1).strip()
        if target.startswith("<") and ">" in target:
            target = target[1:target.index(">")]
        else:
            target = target.split(' "', 1)[0].split(" '", 1)[0]
        parsed = urlsplit(target)
        if parsed.scheme or parsed.netloc or not parsed.path:
            continue
        decoded = unquote(parsed.path)
        destination = (
            root / decoded.lstrip("/")
            if decoded.startswith("/")
            else root / relative.parent / decoded
        ).resolve()
        if not destination.is_relative_to(root):
            errors.append(f"{relative.as_posix()}: local link leaves repository")
        elif not destination.exists():
            errors.append(f"{relative.as_posix()}: missing local link {decoded}")
    return errors


def layout_errors(root: Path) -> list[str]:
    manifest = root / ".github/project-layout.json"
    if not manifest.is_file():
        return ["Missing .github/project-layout.json"]
    try:
        directories = json.loads(manifest.read_text(encoding="utf-8"))["directories"]
    except (ValueError, KeyError, OSError) as error:
        return [f"Invalid layout manifest: {error}"]
    if not isinstance(directories, list) or any(
        not isinstance(directory, str) for directory in directories
    ):
        return ["The layout manifest must contain a list of directory strings"]
    errors = []
    for directory in directories:
        destination = (root / directory).resolve()
        if not destination.is_relative_to(root) or not destination.is_dir():
            errors.append(f"Missing or invalid scaffold directory: {directory}")
    return errors


def check(root: Path) -> tuple[list[str], int]:
    errors = layout_errors(root)
    files = repository_files(root)
    for relative in files:
        label = relative.as_posix()
        if HAN.search(label):
            errors.append(f"{label}: file path contains Han characters")
        absolute = root / relative
        if absolute.is_symlink():
            errors.append(f"{label}: symlinks are unsupported by repository checks")
            continue
        if not absolute.is_file():
            errors.append(f"{label}: tracked file is missing")
            continue
        if relative.suffix.lower() in BINARY_SUFFIXES:
            continue
        try:
            text = absolute.read_bytes().decode("utf-8")
        except UnicodeDecodeError:
            errors.append(f"{label}: project text must use UTF-8")
            continue
        if text.startswith("\ufeff"):
            errors.append(f"{label}: remove the UTF-8 byte order mark")
        if "\r" in text:
            errors.append(f"{label}: use LF line endings")
        errors.extend(language_errors(label, text))
        if relative.suffix.lower() == ".md":
            if (
                relative.parts[0] not in {"docs", ".github"}
                and label not in MARKDOWN_EXCEPTIONS
            ):
                errors.append(f"{label}: move project documentation under docs/")
            errors.extend(local_link_errors(root, relative, text))
    return errors, len(files)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root", type=Path, default=Path(__file__).resolve().parents[2],
        help="Repository root; defaults to this script's repository.",
    )
    arguments = parser.parse_args()
    try:
        errors, count = check(arguments.root.resolve())
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"Repository check could not run: {error}")
        return 1
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        print(f"Repository checks failed with {len(errors)} issue(s).")
        return 1
    print(f"Repository checks passed for {count} tracked or unignored files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
