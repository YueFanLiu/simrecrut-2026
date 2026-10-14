"""Set the input paths for full dataset cleaning."""

from pathlib import Path


RESUME_FILE_PATH = (
    Path(__file__).resolve().parents[4]
    / "datasets/raw/resume-dataset/Resume/Resume.csv"
)
DJINNI_FILE_PATH = (
    Path(__file__).resolve().parents[4]
    / "datasets/raw/djinni-jobs/data/train-00000-of-00001.parquet"
)
