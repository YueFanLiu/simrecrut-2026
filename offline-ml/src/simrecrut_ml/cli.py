"""Expose local acquisition, review and training-preparation inspection."""

import argparse
import json
import sys
from pathlib import Path

from .data.acquisition import acquire_all
from .data.acquisition.archive import ArchiveError
from .data.acquisition.download import DownloadError
from .data.models import PreparationError
from .data.pipeline import prepare_dataset
from .data.review import apply_review_batch, export_review_batch
from .training.readiness import inspect_preparation


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    download = commands.add_parser("download", help="Download all three declared source datasets.")
    download.add_argument("--datasets-root", type=Path, required=True)
    prepare = commands.add_parser(
        "prepare", help="Create unconfirmed, deduplicated review drafts.",
    )
    prepare.add_argument("--manifest", type=Path, required=True)
    prepare.add_argument("--selection", type=Path, required=True)
    prepare.add_argument("--mappings", type=Path)
    prepare.add_argument("--output", type=Path, required=True)
    export = commands.add_parser(
        "export-review", help="Select a local 20-CV and 20-job review batch.",
    )
    export.add_argument("--clean-dir", type=Path, required=True)
    export.add_argument("--output", type=Path, required=True)
    export.add_argument("--profiles", type=int, default=20)
    export.add_argument("--jobs", type=int, default=20)
    review = commands.add_parser(
        "apply-review", help="Validate and import recorded human reviews.",
    )
    review.add_argument("--clean-dir", type=Path, required=True)
    review.add_argument("--reviews", type=Path, required=True)
    review.add_argument("--mappings", type=Path, required=True)
    review.add_argument("--output", type=Path, required=True)
    readiness = commands.add_parser(
        "audit-readiness", help="Report source-audit counts and unmet training prerequisites.",
    )
    readiness.add_argument("--audit-dir", type=Path, required=True)
    readiness.add_argument("--settings", type=Path, required=True)
    extract = commands.add_parser(
        "extract-facts", help="Extract audited sources as local evidence-backed drafts.",
    )
    extract.add_argument("--manifest", type=Path, required=True)
    extract.add_argument("--audit-dir", type=Path, required=True)
    extract.add_argument("--schema", type=Path, required=True)
    extract.add_argument("--output", type=Path, required=True)
    extract.add_argument("--env-file", type=Path, default=Path(".env"))
    extract.add_argument("--limit", type=int)
    extract.add_argument("--privacy-review", type=Path)
    compare = commands.add_parser(
        "audit-extraction", help="Compare accepted excerpts with the earlier source audit.",
    )
    compare.add_argument("--audit-dir", type=Path, required=True)
    compare.add_argument("--run", type=Path, required=True)
    compare.add_argument("--output", type=Path, required=True)
    recheck = commands.add_parser(
        "recheck-extraction", help="Revalidate saved replies locally, without new API calls.",
    )
    recheck.add_argument("--manifest", type=Path, required=True)
    recheck.add_argument("--audit-dir", type=Path, required=True)
    recheck.add_argument("--run", type=Path, required=True)
    recheck.add_argument("--schema", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    """Run one local command and print aggregate counts without source text.

    Paths are explicit so execution does not depend on an installation
    directory. Return zero on success or one for a source, contract, or
    filesystem failure. Argparse reports invalid command syntax itself.
    Download writes raw data and manifests. Preparation, review and
    extraction write below datasets/clean and preserve earlier drafts.
    Audit-extraction replaces its selected comparison report.
    Audit-readiness only reads local files and grants no training approval.
    """
    arguments = _parser().parse_args(argv)
    try:
        if arguments.command == "download":
            result = acquire_all(arguments.datasets_root)
            summary = {
                "specification_revision": result["specification_revision"],
                "sources": [
                    {key: source.get(key) for key in (
                        "source_id", "revision", "published_row_count", "observed_row_count",
                        "observed_pdf_count", "review_status",
                    )} for source in result["sources"]
                ],
            }
        elif arguments.command == "prepare":
            result = prepare_dataset(
                arguments.manifest, arguments.output, arguments.selection, arguments.mappings,
            )
            summary = {
                "review_status": result["review_status"],
                "training_samples": result["training_samples"],
                "sources": [
                    {"source_id": source["source_id"], "counts": source["counts"]}
                    for source in result["sources"]
                ],
            }
        elif arguments.command == "export-review":
            summary = export_review_batch(
                arguments.clean_dir, arguments.output, arguments.profiles, arguments.jobs,
            )
        elif arguments.command == "apply-review":
            summary = apply_review_batch(
                arguments.clean_dir, arguments.reviews, arguments.mappings, arguments.output,
            )
        elif arguments.command == "extract-facts":
            from .extraction.batch import extract_audited_batch
            from .extraction.config import load_settings
            from .extraction.deepseek import DeepSeekFactProvider

            summary = extract_audited_batch(
                arguments.manifest, arguments.audit_dir, arguments.schema,
                arguments.output, DeepSeekFactProvider(load_settings(arguments.env_file)),
                arguments.limit, progress=_extraction_progress,
                privacy_review_path=arguments.privacy_review,
            )
        elif arguments.command == "audit-extraction":
            from .extraction.audit import compare_extraction

            summary = compare_extraction(arguments.audit_dir, arguments.run, arguments.output)
        elif arguments.command == "recheck-extraction":
            from .extraction.recheck import recheck_saved_batch

            summary = recheck_saved_batch(
                arguments.manifest, arguments.audit_dir, arguments.run, arguments.schema,
            )
        else:
            summary = inspect_preparation(arguments.audit_dir, arguments.settings)
    except (PreparationError, DownloadError, ArchiveError) as error:
        print(f"Command rejected: {error}", file=sys.stderr)
        return 1
    except (ValueError, OSError, KeyError):
        # Parser and source errors may carry personal values; keep logs terse.
        print(
            "Command failed. Check local paths, source manifests, network access, "
            "and the documented review contract. Existing data is not overwritten "
            "by preparation or review commands.",
            file=sys.stderr,
        )
        return 1
    print(json.dumps(summary, indent=2, allow_nan=False))
    return 1 if summary.get("stopped_on_provider_configuration") else 0


def _extraction_progress(value: dict) -> None:
    print(json.dumps(value, allow_nan=False), flush=True)
