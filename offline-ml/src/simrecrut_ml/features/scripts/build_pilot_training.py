"""Prepare development pairs or export the archived reviewed pilot."""

import hashlib
import json
import sys
from collections import Counter
from decimal import Decimal
from pathlib import Path

OFFLINE_ML_PATH = Path(__file__).resolve().parents[4]
BATCH = "training-500-multimodal-v3"
INCLUDE_ALL_COMPATIBLE = True
DEVELOPMENT_DIRECTORY = OFFLINE_ML_PATH / "datasets/clean" / BATCH
JOB_RULE_PATH = DEVELOPMENT_DIRECTORY / "job_rules.parquet"
SOURCE_REVIEW_PATH = DEVELOPMENT_DIRECTORY / "source_reviews.parquet"
RULE_CUTOFF = Decimal("0.70")
REFERRAL_EFFECT = Decimal("0.08")
PILOT_SOURCE_PATH = (
    OFFLINE_ML_PATH.parent.parent / "offline-ml-backup-20261014/datasets/clean/pilot/records.jsonl"
)
FEATURE_SCHEMA_PATH = (
    OFFLINE_ML_PATH.parent / "contracts/feature-schemas/professional-research-v2.json"
)
PILOT_DIRECTORY = OFFLINE_ML_PATH / "datasets/clean/pilot"
REVIEW_PATH = PILOT_DIRECTORY / "reviewed_pairs.json"
OUTPUT_PATH = PILOT_DIRECTORY / "training_cases.parquet"

sys.path.insert(0, str(OFFLINE_ML_PATH / "src"))

from simrecrut_ml.features.methods.pilot_training import (
    build_development_pairs,
    build_pilot_rows,
    load_pilot_records,
)
from simrecrut_ml.features.methods.objective_rule import (
    RULE_VERSION,
    assess_pair,
    feature_rows,
    map_job_requirements,
)


def main() -> None:
    """Write development assessments and complete rows to local Parquet tables."""
    import pyarrow as pa
    import pyarrow.parquet as parquet

    if BATCH in {
        "training-500-multimodal-v1",
        "training-500-multimodal-v2",
        "training-500-multimodal-v3",
    }:
        from simrecrut_ml.features.methods.pilot_training import (
            audit_multimodal_training_table,
            build_multimodal_training_table,
            refresh_development_cohort,
        )
        from simrecrut_ml.training.methods.faircv_training import cache_photo_features

        if BATCH != "training-500-multimodal-v1":
            if not (DEVELOPMENT_DIRECTORY / "manifest.json").exists():
                refresh_development_cohort(
                    OFFLINE_ML_PATH,
                    OFFLINE_ML_PATH / "datasets/clean/training-500-multimodal-v1",
                    DEVELOPMENT_DIRECTORY,
                )
        else:
            cache_photo_features(OFFLINE_ML_PATH)
        manifest = build_multimodal_training_table(
            OFFLINE_ML_PATH,
            directory=DEVELOPMENT_DIRECTORY,
            targeted=BATCH != "training-500-multimodal-v1",
        )
        print(
            json.dumps(
                audit_multimodal_training_table(OFFLINE_ML_PATH, directory=DEVELOPMENT_DIRECTORY),
                indent=2,
            )
        )
        print(json.dumps(manifest["acceptance"], indent=2))
        return

    if BATCH in {"development-100", "training-100-v1"}:
        source_path = DEVELOPMENT_DIRECTORY / "records.jsonl"
        with source_path.open(encoding="utf-8") as stream:
            records = [json.loads(line) for line in stream if line.strip()]
        output = DEVELOPMENT_DIRECTORY / "candidate_pairs.parquet"
        manifest = json.loads(
            (DEVELOPMENT_DIRECTORY / "manifest.json").read_text(encoding="utf-8")
        )
        frozen = {}
        if BATCH == "training-100-v1":
            frozen = {
                "profile_splits": manifest["profile_splits"],
                "held_out_job_ids": manifest["held_out_job_ids"],
            }
            test_ids = {key for key, split in frozen["profile_splits"].items() if split == "test"}
            if test_ids & set(manifest["previously_reviewed_profile_ids"]):
                raise ValueError("A previously reviewed profile entered the fresh test set.")
        rows, summary = build_development_pairs(
            records,
            include_all_compatible=INCLUDE_ALL_COMPATIBLE,
            **frozen,
        )
        if output.exists():
            previous_rows = parquet.read_table(output).to_pylist()
            manifest = json.loads(
                (DEVELOPMENT_DIRECTORY / "manifest.json").read_text(
                    encoding="utf-8",
                )
            )
            identity = lambda row: (
                row["base_profile_id"],
                row["job_id"],
                row["split"],
                row["job_partition"],
            )
            if not {identity(row) for row in previous_rows} <= {identity(row) for row in rows}:
                raise ValueError("The expanded pairing changes an existing pair or person split.")
            if set(summary["held_out_job_ids"]) != set(manifest["pairing"]["held_out_job_ids"]):
                raise ValueError("The reserved jobs changed during pairing expansion.")
        if JOB_RULE_PATH.exists():
            by_id = {row["source_record_id"]: row for row in records}
            rules, hashes, saved_rules = {}, {}, []
            for saved in parquet.read_table(JOB_RULE_PATH).to_pylist():
                rule = map_job_requirements(by_id[saved["job_id"]], json.loads(saved["rule_json"]))
                rules[saved["job_id"]] = rule
                encoded = json.dumps(rule, sort_keys=True)
                hashes[saved["job_id"]] = hashlib.sha256(encoded.encode()).hexdigest()
                saved_rules.append({**saved, "rule_json": encoded})
            feature_schema = json.loads(FEATURE_SCHEMA_PATH.read_text(encoding="utf-8"))
            if (
                feature_schema["input_width"] != 39
                or feature_schema["research_order"]["name_cue"]
                != feature_schema["research_order"]["ethnicity"]
            ):
                raise ValueError("The reviewed 39-position encoding no longer matches its schema.")
            assessed, training = [], []
            for pair in rows:
                profile_id = pair["base_profile_id"].partition(":")[2]
                profile, rule = by_id[profile_id], rules[pair["job_id"]]
                result = assess_pair(profile, rule)
                prepared = {**pair, "job_rule_sha256": hashes[pair["job_id"]]}
                training.extend(
                    feature_rows(
                        profile,
                        prepared,
                        result,
                        feature_schema,
                        RULE_CUTOFF,
                        REFERRAL_EFFECT,
                    )
                )
                assessed.append(
                    {
                        **{
                            key: value
                            for key, value in prepared.items()
                            if key not in {"features_39", "Y_N", "Y_B", "face_features_2048"}
                        },
                        "pair_status": "SOURCE_CHECKED",
                        "issues": result["issues"],
                        "assessment_status": result["status"],
                        "assessment_json": json.dumps(result),
                    }
                )
            if BATCH == "training-100-v1":
                complete = Counter(
                    row["base_profile_id"].partition(":")[2]
                    for row in assessed
                    if row["assessment_status"] == "READY"
                    and row["job_partition"] == "development"
                )
                if any(complete[key] < 2 for key in manifest["profile_splits"]):
                    raise ValueError("Every selected person needs two complete ordinary pairs.")
                for split in ("train", "validation"):
                    for label in ("Y_N", "Y_B"):
                        if {row[label] for row in training if row["split"] == split} != {0, 1}:
                            raise ValueError("Training and validation need both label classes.")
                manifest["minimum_complete_ordinary_pairs"] = min(complete.values())
            for filename, contents in (
                ("job_rules.parquet", saved_rules),
                ("candidate_pairs.parquet", assessed),
                ("training_cases.parquet", training),
            ):
                table = pa.Table.from_pylist(contents)
                if contents and filename == "training_cases.parquet":
                    index = table.schema.get_field_index("features_39")
                    table = table.set_column(
                        index,
                        "features_39",
                        pa.array(
                            [row["features_39"] for row in contents],
                            type=pa.list_(pa.float32()),
                        ),
                    )
                table = table.replace_schema_metadata(
                    {
                        b"protocol": b"PILOT_UNCALIBRATED",
                        b"rule_cutoff": b"0.70",
                        b"referral_effect": b"0.08",
                        b"training_run": b"false",
                        b"input_layout": b"16 professional + 16 research + 7 name cue positions",
                        b"feature_schema_version": feature_schema["schema_version"].encode(),
                        b"feature_schema_sha256": hashlib.sha256(FEATURE_SCHEMA_PATH.read_bytes())
                        .hexdigest()
                        .encode(),
                    }
                )
                target = DEVELOPMENT_DIRECTORY / filename
                pending = target.with_suffix(".pending.parquet")
                parquet.write_table(table, pending)
                pending.replace(target)
            manifest_path = DEVELOPMENT_DIRECTORY / "manifest.json"
            if BATCH == "development-100":
                manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["pairing"] = summary
            manifest["acceptance"] = {
                "protocol": "PILOT_UNCALIBRATED",
                "rule_version": RULE_VERSION,
                "cutoff": str(RULE_CUTOFF),
                "referral_yes_effect": str(REFERRAL_EFFECT),
                "other_effects": "0",
                "weight_policy": (
                    "Equal weights across reviewed active areas, for acceptance only."
                ),
                "source_reviews": (
                    parquet.read_metadata(SOURCE_REVIEW_PATH).num_rows
                    if SOURCE_REVIEW_PATH.exists()
                    else 0
                ),
                "job_rules": len(rules),
                "pairs": len(rows),
                "statuses": dict(Counter(row["assessment_status"] for row in assessed)),
                "training_rows": len(training),
                "labelled_profiles": len({row["base_profile_id"] for row in training}),
                "labelled_profile_splits": dict(
                    Counter(
                        split
                        for _, split in {
                            (row["base_profile_id"], row["split"]) for row in training
                        }
                    )
                ),
                "training_splits": dict(Counter(row["split"] for row in training)),
                "Y_N": dict(Counter(str(row["Y_N"]) for row in training)),
                "Y_B": dict(Counter(str(row["Y_B"]) for row in training)),
                "held_out_job_rows": sum(row["job_partition"] == "held_out" for row in training),
                "training_run": False,
            }
            manifest["pairing"]["pair_statuses"] = {"SOURCE_CHECKED": len(assessed)}
            manifest["pairing"]["labelled_training_rows"] = len(training)
            manifest["pairing"][
                "label_status"
            ] = "Only resolved pairs receive uncalibrated acceptance labels."
            manifest["extraction"]["status_counts"] = [
                {"kind": kind, "status": status, "count": count}
                for (kind, status), count in Counter(
                    (record["kind"], record["status"]) for record in records
                ).items()
            ]
            manifest["extraction"]["held_records"] = []
            manifest["extraction"]["failure_codes"] = {}
            manifest["extraction"]["privacy_held_ids"] = []
            manifest["extraction"]["records_sha256"] = hashlib.sha256(
                source_path.read_bytes()
            ).hexdigest()
            response_path = DEVELOPMENT_DIRECTORY / "responses.jsonl"
            if response_path.exists():
                with response_path.open(encoding="utf-8") as stream:
                    requests = Counter(
                        json.loads(line)["extractor_version"] for line in stream if line.strip()
                    )
                manifest["extraction"]["api_request_count"] = requests.get(
                    manifest["extraction"]["extractor_version"], 0
                )
            manifest["acceptance"]["artifact_sha256"] = {
                name: hashlib.sha256((DEVELOPMENT_DIRECTORY / name).read_bytes()).hexdigest()
                for name in (
                    "records.jsonl",
                    "source_reviews.parquet",
                    "job_rules.parquet",
                    "candidate_pairs.parquet",
                    "training_cases.parquet",
                )
                if (DEVELOPMENT_DIRECTORY / name).exists()
            }
            if BATCH == "training-100-v1":
                manifest["status"] = "ACCEPTANCE_READY_NOT_TRAINED"
            manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
            print(json.dumps(manifest["acceptance"], indent=2))
            return
        table = pa.Table.from_pylist(rows)
        table = table.set_column(
            table.schema.get_field_index("features_39"),
            "features_39",
            pa.array([None] * len(rows), type=pa.list_(pa.float32())),
        )
        table = table.replace_schema_metadata(
            {
                b"purpose": b"Candidate pairing; not a labelled training table.",
                b"records_sha256": hashlib.sha256(source_path.read_bytes())
                .hexdigest()
                .encode(
                    "ascii",
                ),
            }
        )
        pending = output.with_suffix(".pending.parquet")
        parquet.write_table(table, pending)
        pending.replace(output)
        manifest_path = DEVELOPMENT_DIRECTORY / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["pairing"] = summary
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(summary, indent=2))
        return
    if BATCH != "archived-20":
        raise ValueError("Select training-100-v1, development-100 or archived-20.")

    review = json.loads(REVIEW_PATH.read_text(encoding="utf-8"))
    schema = json.loads(FEATURE_SCHEMA_PATH.read_text(encoding="utf-8"))
    digest = hashlib.sha256(PILOT_SOURCE_PATH.read_bytes()).hexdigest()
    if digest != review["source_sha256"]:
        raise ValueError("The archived pilot has changed since the evidence review.")
    rows = build_pilot_rows(load_pilot_records(PILOT_SOURCE_PATH), review, schema)
    table = pa.Table.from_pylist(
        rows,
        schema=pa.schema(
            [
                ("base_profile_id", pa.string()),
                ("split", pa.string()),
                ("features_39", pa.list_(pa.float64(), 39)),
                ("biography", pa.string()),
                ("face_embedding", pa.list_(pa.float32())),
                ("Y_N", pa.int8()),
                ("Y_B", pa.int8()),
                ("variant", pa.string()),
                ("evidence_json", pa.string()),
            ]
        ),
    )
    table = table.replace_schema_metadata(
        {
            b"protocol": b"PILOT_UNCALIBRATED",
            b"source_sha256": digest.encode("ascii"),
            b"held_examples": json.dumps(review["held_examples"]).encode("utf-8"),
        }
    )
    with OUTPUT_PATH.open("xb") as stream:
        parquet.write_table(table, stream)
    print(f"Prepared {len(rows)} local pilot rows from {len(review['pairs'])} reviewed pairs.")


if __name__ == "__main__":
    main()
