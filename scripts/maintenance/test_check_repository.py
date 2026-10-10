"""Check dataset-note exceptions without admitting local research payloads."""

import importlib.util
import json
import subprocess
from pathlib import Path

import pytest


spec = importlib.util.spec_from_file_location(
    "check_repository", Path(__file__).with_name("check_repository.py"),
)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


@pytest.fixture
def repository(tmp_path):
    subprocess.run(["git", "init", "--quiet", str(tmp_path)], check=True)
    layout = tmp_path / ".github/project-layout.json"
    layout.parent.mkdir()
    layout.write_text(json.dumps({"directories": []}) + "\n", encoding="utf-8", newline="\n")
    gitignore = Path(__file__).resolve().parents[2] / ".gitignore"
    (tmp_path / ".gitignore").write_bytes(gitignore.read_bytes())
    guide = tmp_path / "docs/research/dataset-preparation.md"
    guide.parent.mkdir(parents=True)
    guide.write_text("# Dataset preparation\n", encoding="utf-8", newline="\n")
    return tmp_path


def test_nested_payloads_ignored_but_small_notes_checked(repository):
    directory = repository / "offline-ml/datasets/raw/example/nested"
    directory.mkdir(parents=True)
    (directory / "records.jsonl").write_bytes(b"\xff private data")
    clean = repository / "offline-ml/datasets/clean/run"
    clean.mkdir(parents=True)
    (clean / "records.parquet").write_bytes(b"\xff private data")
    note = directory / "data-notes.md"
    note.write_text(
        "# Local files\n\nSee [the guide](../../../../../docs/research/dataset-preparation.md).\n",
        encoding="utf-8", newline="\n",
    )
    errors, _ = checker.check(repository)
    assert errors == []
    visible = checker.repository_files(repository)
    assert note.relative_to(repository) in visible
    assert not any(path.suffix in {".jsonl", ".parquet"} for path in visible)


def test_dataset_note_size_limit(repository):
    note = repository / "offline-ml/datasets/clean/data-notes.md"
    note.parent.mkdir(parents=True)
    note.write_text("# Notes\n" + "a" * 4096 + "\n", encoding="utf-8", newline="\n")
    errors, _ = checker.check(repository)
    assert any("within 4096 bytes" in error for error in errors)


def test_exception_does_not_allow_other_module_documents(repository):
    directory = repository / "offline-ml/datasets/raw/example"
    directory.mkdir(parents=True)
    note = directory / "README.md"
    note.write_text("# Local guide\n", encoding="utf-8", newline="\n")
    subprocess.run(["git", "add", "--force", str(note)], cwd=repository, check=True)
    errors, _ = checker.check(repository)
    assert any("move project documentation under docs/" in error for error in errors)


def test_dataset_note_language_and_links_still_checked(repository):
    note = repository / "offline-ml/datasets/manifests/data-notes.md"
    note.parent.mkdir(parents=True)
    note.write_text("# \u4e2d\u6587\n\n[Guide](missing.md)\n", encoding="utf-8", newline="\n")
    errors, _ = checker.check(repository)
    assert any("Han characters" in error for error in errors)
    assert any("missing local link" in error for error in errors)
