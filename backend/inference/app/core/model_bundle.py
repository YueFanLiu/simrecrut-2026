"""Install the pinned research model packages before starting inference."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
from tempfile import TemporaryDirectory
from urllib.error import URLError
from urllib.request import Request, urlopen
from zipfile import BadZipFile, ZipFile


BUNDLE_URL = (
    "https://huggingface.co/petitlang/simrecrut-model-artifacts/resolve/"
    "85b340b47e3fed3308b5e6ffed82f0f4255b90d7/simrecrut-models-20261117.zip"
)
BUNDLE_BYTES = 93_769_505
BUNDLE_SHA256 = "7b3089b43a09d71f24acfa61eb908522fcf63e457df7a22e27df161e454fac93"
PACKAGE_MANIFESTS = {
    Path("neutral/neutral-20261117-s45-v1"):
        "76a3d2be030d81cb41e9fdf726bd3381c82239aecc85659827b96251d5ee5b1d",
    Path("biased/biased-20261117-s46-v1"):
        "9cb0ab77561a32a76bacf5ec8fc32ad6da2cedae588464324443c2fc82f95f86",
}


class ModelBundleError(RuntimeError):
    """Report a model installation error without exposing credentials."""


def _sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def _verify_package(package: Path, expected_manifest: str) -> None:
    manifest_path = package / "manifest.json"
    if not manifest_path.is_file() or _sha256(manifest_path) != expected_manifest:
        raise ModelBundleError(f"Manifest verification failed for {package.name}.")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for name, expected in manifest["files"].items():
        path = package / name
        if not path.is_file() or _sha256(path) != expected:
            raise ModelBundleError(f"Checksum verification failed for {package.name}/{name}.")


def _download_bundle(destination: Path, token: str) -> None:
    request = Request(BUNDLE_URL)
    # Hugging Face may redirect to a signed storage URL.
    request.add_unredirected_header("Authorization", f"Bearer {token}")
    try:
        with urlopen(request, timeout=45) as response, destination.open("xb") as output:
            remaining = BUNDLE_BYTES
            while chunk := response.read(min(remaining + 1, 1024 * 1024)):
                remaining -= len(chunk)
                if remaining < 0:
                    raise ModelBundleError("The model download exceeds the expected size.")
                output.write(chunk)
    except URLError as error:
        status = getattr(error, "code", None)
        if status in (401, 403, 404):
            raise ModelBundleError(
                "The private model bundle is unavailable. Check MODEL_BUNDLE_TOKEN access."
            ) from error
        raise ModelBundleError("The model bundle download failed. Retry the stack startup.") from error
    if remaining or _sha256(destination) != BUNDLE_SHA256:
        raise ModelBundleError("The model bundle failed size or checksum verification.")


def install_models(root: Path, token: str) -> None:
    """Verify installed models and fetch any missing packages from the private bundle.

    A package becomes visible only after all of its files have been verified.
    An existing package must match its pinned manifest before it is reused.
    """
    missing = {}
    for relative, digest in PACKAGE_MANIFESTS.items():
        directory = root / relative
        if directory.exists():
            _verify_package(directory, digest)
        else:
            missing[relative] = digest
    if not missing:
        return
    if not token:
        raise ModelBundleError("Set MODEL_BUNDLE_TOKEN in the root .env to download the models.")

    cache = root / "neutral"
    cache.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".bundle-", dir=cache) as temporary:
        staging = Path(temporary)
        bundle = staging / "models.zip"
        _download_bundle(bundle, token)
        with ZipFile(bundle) as archive:
            for relative, digest in missing.items():
                package = staging / relative
                package.mkdir(parents=True)
                member_prefix = f"model_artifacts/{relative.as_posix()}"
                manifest_path = package / "manifest.json"
                manifest_path.write_bytes(archive.read(f"{member_prefix}/manifest.json"))
                if _sha256(manifest_path) != digest:
                    raise ModelBundleError(f"Manifest verification failed for {package.name}.")
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
                for name in manifest["files"]:
                    with archive.open(f"{member_prefix}/{name}") as source:
                        with (package / name).open("xb") as output:
                            shutil.copyfileobj(source, output)
                _verify_package(package, digest)

        for relative, digest in missing.items():
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            try:
                (staging / relative).rename(target)
            except OSError:
                if not target.is_dir():
                    raise
                _verify_package(target, digest)


def main() -> int:
    """Return success once both packages are ready for the integration stack."""
    root = Path(os.environ.get("MODEL_ARTIFACTS_PATH", "/app/model_artifacts"))
    try:
        install_models(root, os.environ.get("MODEL_BUNDLE_TOKEN", "").strip())
    except (ModelBundleError, OSError, BadZipFile, KeyError) as error:
        print(f"Model setup failed. {error}", file=sys.stderr)
        return 1
    print("Neutral and Biased model packages are ready.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
