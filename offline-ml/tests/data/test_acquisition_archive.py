"""Check that ZIP import cannot write unapproved or escaping files."""

import stat
import tempfile
import unittest
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from simrecrut_ml.data.acquisition.archive import ArchiveError, extract_dataset_archive


class ArchiveTests(unittest.TestCase):
    """Validate the complete inventory before extracting source data."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.archive = self.root / "synthetic.zip"
        self.destination = self.root / "raw"

    def tearDown(self):
        self.temporary.cleanup()

    def _write_archive(self, entries):
        with ZipFile(self.archive, "w", ZIP_DEFLATED) as output:
            for name, content in entries:
                if isinstance(name, str):
                    entry = ZipInfo("placeholder.csv")
                    # ZipInfo normalises Windows separators before writing.
                    entry.filename = name
                    entry.orig_filename = name
                    entry.compress_type = ZIP_DEFLATED
                else:
                    entry = name
                output.writestr(entry, content)

    def test_valid_csv_pdf_contents_and_idempotent_reimport(self):
        self._write_archive([("Resume/Resume.csv", "ID,Category\n1,TEST\n"),
                             ("data/data/TEST/1.pdf", b"%PDF-synthetic")])
        first = extract_dataset_archive(self.archive, self.destination)
        second = extract_dataset_archive(self.archive, self.destination)
        self.assertEqual(first, second)
        self.assertEqual(len(first), 2)
        self.assertTrue(all(len(entry["sha256"]) == 64 for entry in first))

    def test_traversal_rejected_before_valid_member_is_written(self):
        self._write_archive([("safe.csv", "synthetic"), ("../outside.csv", "synthetic")])
        with self.assertRaises(ArchiveError):
            extract_dataset_archive(self.archive, self.destination)
        self.assertFalse((self.destination / "safe.csv").exists())
        self.assertFalse((self.root / "outside.csv").exists())

    def test_symlink_member_is_rejected(self):
        symlink = ZipInfo("linked.csv")
        symlink.create_system = 3
        symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
        self._write_archive([(symlink, "../outside.csv")])
        with self.assertRaisesRegex(ArchiveError, "non-regular"):
            extract_dataset_archive(self.archive, self.destination)

    def test_windows_path_collisions_and_reserved_names_are_rejected(self):
        for unsafe in ["C:/outside.csv", "data\\outside.csv", "CON.csv", "trailing .csv.",
                       "unsafe?.csv", "data/./source.csv"]:
            with self.subTest(path=unsafe):
                self._write_archive([(unsafe, "synthetic")])
                with self.assertRaises(ArchiveError):
                    extract_dataset_archive(self.archive, self.destination)
        self._write_archive([("data/Source.csv", "one"), ("data/source.csv", "two")])
        with self.assertRaisesRegex(ArchiveError, "duplicate"):
            extract_dataset_archive(self.archive, self.destination)

    def test_size_limit_and_executable_entries_are_rejected(self):
        self._write_archive([("large.csv", "synthetic-data")])
        with self.assertRaisesRegex(ArchiveError, "limits"):
            extract_dataset_archive(self.archive, self.destination, max_uncompressed_bytes=3)
        self._write_archive([("source.py", "print('synthetic')")])
        with self.assertRaisesRegex(ArchiveError, "other than CSV or PDF"):
            extract_dataset_archive(self.archive, self.destination)

    def test_existing_file_must_match_archive(self):
        self._write_archive([("data.csv", "source")])
        self.destination.mkdir()
        (self.destination / "data.csv").write_text("different", encoding="utf-8")
        with self.assertRaisesRegex(ArchiveError, "differs"):
            extract_dataset_archive(self.archive, self.destination)
        self.assertEqual((self.destination / "data.csv").read_text(), "different")
