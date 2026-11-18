"""Check private-data exclusions and precise translation-resource exceptions."""

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "repository_checker", Path(__file__).with_name("check_repository.py")
)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class ResearchFilesTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.repository = Path(temporary.name)
        subprocess.run(["git", "init", "--quiet", str(self.repository)], check=True)
        layout = self.repository / ".github/project-layout.json"
        layout.parent.mkdir()
        layout.write_text(json.dumps({"directories": []}) + "\n", encoding="utf-8", newline="\n")
        gitignore = Path(__file__).resolve().parents[2] / ".gitignore"
        (self.repository / ".gitignore").write_bytes(gitignore.read_bytes())
        guide = self.repository / "docs/research/dataset-preparation.md"
        guide.parent.mkdir(parents=True)
        guide.write_text("# Dataset preparation\n", encoding="utf-8", newline="\n")

    def test_nested_payloads_and_local_notes_are_ignored(self):
        directory = self.repository / "offline-ml/datasets/raw/example/nested"
        directory.mkdir(parents=True)
        (directory / "records.jsonl").write_bytes(b"\xff private data")
        clean = self.repository / "offline-ml/datasets/clean/run"
        clean.mkdir(parents=True)
        (clean / "records.parquet").write_bytes(b"\xff private data")
        note = directory / "data-notes.md"
        note.write_text(
            "# Local files\n\nSee [the guide](../../../../../docs/research/dataset-preparation.md).\n",
            encoding="utf-8",
            newline="\n",
        )
        errors, _ = checker.check(self.repository)
        self.assertEqual([], errors)
        visible = checker.repository_files(self.repository)
        self.assertNotIn(note.relative_to(self.repository), visible)
        self.assertFalse(any(path.suffix in {".jsonl", ".parquet"} for path in visible))

    def test_force_added_dataset_note_is_not_project_documentation(self):
        note = self.repository / "offline-ml/datasets/clean/data-notes.md"
        note.parent.mkdir(parents=True)
        note.write_text("# Local notes\n", encoding="utf-8", newline="\n")
        subprocess.run(["git", "add", "--force", str(note)], cwd=self.repository, check=True)
        errors, _ = checker.check(self.repository)
        self.assertTrue(any("move project documentation under docs/" in error for error in errors))

    def test_exception_does_not_allow_other_module_documents(self):
        directory = self.repository / "offline-ml/datasets/raw/example"
        directory.mkdir(parents=True)
        note = directory / "README.md"
        note.write_text("# Local guide\n", encoding="utf-8", newline="\n")
        subprocess.run(["git", "add", "--force", str(note)], cwd=self.repository, check=True)
        errors, _ = checker.check(self.repository)
        self.assertTrue(any("move project documentation under docs/" in error for error in errors))

    def test_authored_document_language_and_links_are_checked(self):
        note = self.repository / "docs/research/dataset-preparation.md"
        note.write_text("# \u4e2d\u6587\n\n[Guide](missing.md)\n", encoding="utf-8", newline="\n")
        errors, _ = checker.check(self.repository)
        self.assertTrue(any("Han characters" in error for error in errors))
        self.assertTrue(any("missing local link" in error for error in errors))


class TranslationPolicyTest(unittest.TestCase):
    def test_chinese_is_allowed_only_in_zh_translation_values(self):
        good = '{"Hello":{"zh":"\\u4f60\\u597d","fr":"Bonjour"}}'
        self.assertEqual([], checker.language_errors(checker.FRONTEND_MESSAGES, good))
        self.assertTrue(
            checker.language_errors(checker.FRONTEND_MESSAGES, '{"Hello":{"zh":"Hi"}}')
        )
        self.assertTrue(
            checker.language_errors(
                checker.FRONTEND_MESSAGES, '{"Hello":{"zh":"Hi","fr":"\\u4f60"}}'
            )
        )
        self.assertTrue(
            checker.language_errors(
                checker.FRONTEND_MESSAGES, '{"\\u4f60":{"zh":"Hi","fr":"Bonjour"}}'
            )
        )

    def test_backend_exception_is_restricted_to_value_text(self):
        self.assertEqual(
            [], checker.language_errors(checker.CHINESE_API_MESSAGES, "api.key=\u4f60\u597d")
        )
        self.assertTrue(checker.language_errors(checker.CHINESE_API_MESSAGES, "\u4f60=Hello"))
        self.assertTrue(checker.language_errors(checker.CHINESE_API_MESSAGES, "# \u4f60"))
        self.assertTrue(
            checker.language_errors("backend/java/example.properties", "api.key=\u4f60")
        )

    def test_source_and_unknown_catalogue_paths_remain_checked(self):
        for path in ["frontend/src/example.js", "frontend/src/i18n/other.json", "docs/example.md"]:
            self.assertTrue(checker.language_errors(path, "\u4f60\u597d"))
        self.assertEqual(
            [], checker.language_errors("docs/development/page-api-integration.md", "\u4f60\u597d")
        )


if __name__ == "__main__":
    unittest.main()
