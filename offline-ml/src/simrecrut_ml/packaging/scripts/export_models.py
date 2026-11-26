"""Export the selected, validated local weights without running training."""

import json
import sys
from pathlib import Path

OFFLINE_ML_PATH = Path(__file__).resolve().parents[4]
RUN_PATH = OFFLINE_ML_PATH / "runs/training-500-multimodal-v3/20261117T140541Z-bd3e28"
PACKAGE_PATH = OFFLINE_ML_PATH.parent / "model_artifacts"
sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))

from simrecrut_ml.packaging.methods.model_package import export_models


if __name__ == "__main__":
    print(json.dumps(export_models(OFFLINE_ML_PATH, RUN_PATH, PACKAGE_PATH), indent=2))
