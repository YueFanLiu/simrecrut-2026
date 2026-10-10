"""Check publisher provenance and refusal of changed source declarations."""

import csv
import hashlib
import json
import tempfile
import unittest
from io import StringIO
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile

from simrecrut_ml.data.acquisition import acquire_dataset
from simrecrut_ml.data.acquisition.download import DownloadError


class SourceTests(unittest.TestCase):
    """Record complete raw sources without approving their training use."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def test_hub_manifest_uses_pinned_revision_and_publisher_hash(self):
        payload = b"PAR1synthetic-sourcePAR1"
        digest = hashlib.sha256(payload).hexdigest()
        relative = "data/train-00000-of-00001.parquet"
        metadata = {
            "sha": "synthetic-revision", "private": False, "gated": False,
            "lastModified": "2024-06-02T10:25:58Z", "siblings": [{"rfilename": relative}],
            "cardData": {"license": "mit", "dataset_info": {
                "features": [{"name": "id", "dtype": "string"}],
                "splits": [{"name": "train", "num_examples": 2}],
            }},
        }
        tree = [{"type": "file", "path": relative, "size": len(payload),
                 "lfs": {"oid": digest}}]
        calls = []

        def copy_source(spec, destination, **kwargs):
            calls.append(spec)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload if destination.suffix == ".parquet" else b"MIT\n")

        with patch("simrecrut_ml.data.acquisition.sources.fetch_json",
                   side_effect=[metadata, tree]):
            with patch("simrecrut_ml.data.acquisition.sources.download_file", copy_source):
                result = acquire_dataset("djinni-jobs", self.root)
        self.assertIn("/synthetic-revision/", calls[0].url)
        self.assertEqual(calls[0].expected_sha256, digest)
        self.assertEqual(result["review_status"], "NOT_REVIEWED")
        self.assertIsNone(result["retained_record_count"])
        self.assertEqual(result["files"][0]["sha256"], digest)
        saved = json.loads((self.root / "manifests/djinni-jobs.json").read_text())
        self.assertEqual(saved, result)

    def test_changed_hub_license_is_refused_before_download(self):
        metadata = {"sha": "synthetic-revision", "cardData": {"license": "changed"}}
        with patch("simrecrut_ml.data.acquisition.sources.fetch_json", return_value=metadata):
            with patch("simrecrut_ml.data.acquisition.sources.download_file") as download:
                with self.assertRaisesRegex(DownloadError, "licence"):
                    acquire_dataset("djinni-jobs", self.root)
        download.assert_not_called()

    def test_resume_manifest_counts_csv_pdf_without_claiming_verified_labels(self):
        metadata = {"ref": "snehaanbhawal/resume-dataset", "currentVersionNumber": 1,
                    "licenseName": "CC0: Public Domain", "description": "Synthetic fixture."}
        csv_text = StringIO()
        writer = csv.writer(csv_text)
        writer.writerow(["ID", "Resume_str", "Resume_html", "Category"])
        writer.writerow(["1", "Synthetic text", "<p>Synthetic text</p>", "TEST"])

        def copy_archive(spec, destination, **kwargs):
            destination.parent.mkdir(parents=True, exist_ok=True)
            with ZipFile(destination, "w") as archive:
                archive.writestr("Resume/Resume.csv", csv_text.getvalue())
                archive.writestr("data/data/TEST/1.pdf", b"%PDF-synthetic")

        with patch("simrecrut_ml.data.acquisition.sources.fetch_json", return_value=metadata):
            with patch("simrecrut_ml.data.acquisition.sources.download_file", copy_archive):
                result = acquire_dataset("resume-dataset", self.root)
        self.assertEqual(result["observed_row_count"], 1)
        self.assertEqual(result["observed_pdf_count"], 1)
        self.assertEqual(result["review_status"], "NOT_REVIEWED")
        self.assertIsNone(result["files"][0]["expected_sha256"])
        self.assertIn("no archive SHA-256", result["integrity_note"])
        self.assertTrue(all(entry["path"].startswith("raw/resume-dataset/")
                            for entry in result["extracted_files"]))

    def test_changed_kaggle_license_is_refused_before_download(self):
        metadata = {"ref": "snehaanbhawal/resume-dataset", "licenseName": "changed"}
        with patch("simrecrut_ml.data.acquisition.sources.fetch_json", return_value=metadata):
            with patch("simrecrut_ml.data.acquisition.sources.download_file") as download:
                with self.assertRaisesRegex(DownloadError, "licence"):
                    acquire_dataset("resume-dataset", self.root)
        download.assert_not_called()
