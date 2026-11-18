"""Prepare source-backed cohorts and controlled offline training tables."""

import hashlib
import json
import re
from collections import Counter
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

AREAS = ("skills", "experience", "education", "languages", "projects")
STEP = Decimal("0.0000001")


def refresh_development_cohort(root: Path, source: Path, destination: Path) -> dict:
    """Reassess development evidence while keeping the archived test sealed.

    Reuse source-backed extraction results, original people splits and
    development jobs. Unknown comparisons stay held. No provider calls
    or test profile contents are needed for this revision.
    """
    import copy
    import pyarrow as pa
    import pyarrow.parquet as pq
    from .objective_rule import RULE_VERSION, assess_pair, map_job_requirements

    if destination.exists():
        raise FileExistsError("Keep the prior revision and choose a new cohort directory.")
    parent = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    splits = {
        key: value
        for key, value in parent["profile_splits"].items()
        if value in {"train", "validation"}
    }
    jobs = [
        row
        for row in pq.read_table(source / "job_rules.parquet").to_pylist()
        if row["job_id"] not in parent["held_out_job_ids"]
    ]
    job_ids = {row["job_id"] for row in jobs}
    job_records = {}
    for path in (
        root / "datasets/clean/training-100-v1/records.jsonl",
        source / "pool_records.jsonl",
    ):
        with path.open(encoding="utf-8") as stream:
            for line in stream:
                identity = re.search(r'"source_record_id":\s*"([^"]+)"', line[:1000])
                if identity and identity[1] in job_ids:
                    row = json.loads(line)
                    if row["kind"] == "job":
                        job_records[identity[1]] = row
    if set(job_records) != job_ids:
        raise ValueError("A development job lost its original extraction evidence.")
    rules = {}
    for row in jobs:
        rules[row["job_id"]] = map_job_requirements(
            job_records[row["job_id"]],
            copy.deepcopy(json.loads(row["rule_json"])),
        )
    people = []
    with (source / "admitted_profiles.jsonl").open(encoding="utf-8") as stream:
        for line in stream:
            identity = re.search(r'"source_record_id":\s*"([^"]+)"', line[:1000])
            if identity and identity[1] in splits:
                people.append(json.loads(line))
    if len(people) != len(splits):
        raise ValueError("A development profile lost its original extraction evidence.")
    destination.mkdir(parents=True)
    audits, admitted = [], []
    for number, person in enumerate(people, 1):
        record = person["record"]
        category = record["cohort"]["category"]
        complete = []
        for job in jobs:
            if job["category"] != category:
                continue
            result = assess_pair(record, rules[job["job_id"]])
            audits.append(
                {
                    "source_record_id": record["source_record_id"],
                    "job_id": job["job_id"],
                    "split": person["split"],
                    "status": result["status"],
                    "issues": json.dumps(result["issues"]),
                    "mandatory_pass": result.get("mandatory_pass"),
                }
            )
            if result["status"] == "READY":
                complete.append({"job_id": job["job_id"], "assessment": result})
        if len(complete) < 2:
            raise ValueError(
                "A development person lacks two complete pairs; retain the source batch."
            )
        preferred = {pair["job_id"] for pair in person["pairs"]}
        retained = [pair for pair in complete if pair["job_id"] in preferred]
        if len(retained) < 3:
            for pair in (select_complete_pairs(complete) if len(complete) >= 3 else complete):
                if pair["job_id"] not in {row["job_id"] for row in retained}:
                    retained.append(pair)
                if len(retained) == 3:
                    break
        admitted.append({"record": record, "split": person["split"], "pairs": retained[:3]})
        if number % 40 == 0:
            print("EVIDENCE_REFRESH", number, len(people), flush=True)
    with (destination / "admitted_profiles.jsonl").open(
        "w", encoding="utf-8", newline="\n"
    ) as stream:
        for person in admitted:
            stream.write(json.dumps(person, ensure_ascii=False) + "\n")
    with (destination / "job_records.jsonl").open("w", encoding="utf-8", newline="\n") as stream:
        for record in job_records.values():
            stream.write(json.dumps(record, ensure_ascii=False) + "\n")
    pq.write_table(
        pa.Table.from_pylist(
            [{**row, "rule_json": json.dumps(rules[row["job_id"]])} for row in jobs]
        ),
        destination / "job_rules.parquet",
    )
    pq.write_table(pa.Table.from_pylist(audits), destination / "selection_audit.parquet")
    faces = pq.read_table(
        source / "face_features.parquet", filters=[("split", "in", ["train", "validation"])]
    )
    pq.write_table(faces, destination / "face_features.parquet")
    manifest = {
        "cohort": destination.name,
        "status": "DEVELOPMENT_EVIDENCE_READY",
        "source_cohort": source.name,
        "rule_version": RULE_VERSION,
        "profile_splits": splits,
        "held_out_job_ids": parent["held_out_job_ids"],
        "source_label_protocol": parent["label_protocol"],
        "sealed_test": {
            "source_cohort": source.name,
            "profiles": 100,
            "source_table_sha256": parent["acceptance"]["artifact_sha256"][
                "training_cases.parquet"
            ],
            "features_or_labels_read": False,
            "evaluated": False,
        },
        "development_people": len(admitted),
        "development_splits": dict(Counter(splits.values())),
        "held_pairs": sum(row["status"] != "READY" for row in audits),
    }
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest


def prepare_multimodal_cohort(
    root: Path,
    settings: dict,
    seed: int = 42,
    *,
    fact_extractor=None,
    job_mapper=None,
) -> dict:
    """Select 500 evidence-complete people and 60 source-backed jobs.

    Preserve archived batches. Fix jobs before extracting unused test
    profiles. Persist proposals and admission reasons locally so a
    restart does not repeat completed provider requests.
    """
    import copy
    from concurrent.futures import ThreadPoolExecutor
    from functools import partial

    import pyarrow as pa
    import pyarrow.parquet as pq

    from simrecrut_ml.extraction.methods.deepseek import extract
    from simrecrut_ml.extraction.methods.extraction import (
        clean_record,
        load_djinni,
        select_development_records,
    )
    from .objective_rule import assess_pair, map_job_requirements, propose_job_rule

    directory = root / "datasets/clean/training-500-multimodal-v1"
    directory.mkdir(parents=True, exist_ok=True)
    prior_path = root / "datasets/clean/training-100-v1"
    prior = json.loads((prior_path / "manifest.json").read_text(encoding="utf-8"))
    sealed = {key for key, split in prior["profile_splits"].items() if split == "test"}
    reviewed = set(prior["previously_reviewed_profile_ids"]) | set(prior["profile_splits"])
    old = {}
    for line in (prior_path / "records.jsonl").open(encoding="utf-8"):
        identity = re.search(r'"source_record_id":\s*"([^"]+)"', line[:800])
        if identity and identity[1] in sealed:
            continue
        row = json.loads(line)
        if row["kind"] == "job" and row["source_record_id"] in prior["held_out_job_ids"]:
            continue
        old[row["source_record_id"]] = row
    targets = {
        "Java": 100,
        ".NET": 100,
        "JavaScript": 100,
        "Python": 100,
        "QA": 50,
        "QA Automation": 50,
    }
    schema = json.loads(
        (root.parent / "contracts/fact-schemas/extraction-v1.json").read_text(encoding="utf-8")
    )
    extractor = fact_extractor or partial(extract, settings=settings)
    mapper = job_mapper or propose_job_rule
    record_path = directory / "pool_records.jsonl"
    cached = dict(old)
    if record_path.exists():
        for line in record_path.open(encoding="utf-8"):
            row = json.loads(line)
            cached[row["source_record_id"]] = row

    def clean(raw):
        identity = raw["source_record_id"]
        if identity in cached:
            return copy.deepcopy(cached[identity]), {}
        cache = {}
        result = clean_record(
            raw,
            schema=schema,
            extractor=extractor,
            extractor_version=settings["extractor_version"],
            cache=cache,
            allow_calls=True,
        )
        if any(
            code in {"DEEPSEEK_AUTHENTICATION_FAILED", "DEEPSEEK_INSUFFICIENT_BALANCE"}
            for code in result["validation_codes"]
        ):
            raise RuntimeError("DeepSeek credentials or account balance prevent extraction.")
        return result, cache

    def store(result, cache):
        identity = result["source_record_id"]
        if identity not in cached:
            with record_path.open("a", encoding="utf-8", newline="\n") as stream:
                stream.write(json.dumps(result, ensure_ascii=False) + "\n")
            with (directory / "responses.jsonl").open(
                "a", encoding="utf-8", newline="\n"
            ) as stream:
                for reply in cache.values():
                    stream.write(json.dumps(reply, ensure_ascii=False) + "\n")
            cached[identity] = result

    job_file = directory / "job_rules.parquet"
    if job_file.exists():
        saved_rules = pq.read_table(job_file).to_pylist()
        rules = {
            r["job_id"]: map_job_requirements(cached[r["job_id"]], json.loads(r["rule_json"]))
            for r in saved_rules
        }
        jobs = {identity: cached[identity] for identity in rules}
        pq.write_table(
            pa.Table.from_pylist(
                [{**row, "rule_json": json.dumps(rules[row["job_id"]])} for row in saved_rules]
            ),
            job_file,
        )
    else:
        rules, jobs = {}, {}
        previous_rules = pq.read_table(prior_path / "job_rules.parquet").to_pylist()
        for row in previous_rules:
            identity = row["job_id"]
            if identity in old:
                rules[identity] = map_job_requirements(old[identity], json.loads(row["rule_json"]))
                jobs[identity] = old[identity]
        candidates = select_development_records(
            load_djinni(
                root / "datasets/raw/djinni-jobs/data/train-00000-of-00001.parquet", "job"
            ),
            {key: 120 for key in targets},
            seed,
        )
        project_sources = []
        for raw in load_djinni(
            root / "datasets/raw/djinni-jobs/data/train-00000-of-00001.parquet", "job"
        ):
            category = raw["fields"].get("Primary Keyword")
            text = raw["fields"].get("Long Description") or ""
            if (
                category in targets
                and re.search(r"\bpet projects?\b", text, re.I)
                and re.search(
                    r"\b(?:github|gitlab)\b",
                    text,
                    re.I,
                )
            ):
                raw["cohort"] = {
                    "category": category,
                    "seed": seed,
                    "selection_partition": "project_requirement_screen",
                }
                project_sources.append(raw)
        candidates = list(
            {row["source_record_id"]: row for row in candidates + project_sources}.values()
        )
        candidates = [
            row
            for row in candidates
            if row["source_record_id"] not in prior["held_out_job_ids"]
            and row["source_record_id"] not in rules
        ]
        candidates.sort(
            key=lambda row: (
                not bool(re.search(r"\bpet projects?\b", row["fields"]["Long Description"], re.I)),
                not bool(
                    re.search(
                        r"bachelor|master.{0,12}degree|university degree|project experience",
                        row["fields"]["Long Description"],
                        re.I,
                    )
                ),
                len(row["fields"]["Long Description"]),
                row["source_record_id"],
            )
        )
        audits = []

        def prepare_job(raw):
            result, cache = clean(raw)
            rule = None
            issue = result["status"]
            if result["status"] == "VALIDATED":
                try:
                    rule = mapper(
                        result,
                        settings,
                        directory / "job_proposals" / (raw["source_record_id"] + ".json"),
                    )
                    issue = ";".join(rule["unresolved"])
                    if rule["unresolved"] or not rule["skills"]:
                        rule = None
                except (ValueError, KeyError, TypeError, ArithmeticError) as error:
                    issue = str(error)[:200]
            return result, cache, rule, issue

        with ThreadPoolExecutor(max_workers=8) as pool:
            while candidates:
                counts = Counter(row["cohort"]["category"] for row in jobs.values())
                if all(counts[key] >= 10 for key in targets):
                    break
                wave = []
                for category in targets:
                    if counts[category] >= 10:
                        continue
                    matching = [row for row in candidates if row["cohort"]["category"] == category]
                    wave.extend(matching[: min(3, 10 - counts[category])])
                if not wave:
                    break
                chosen = {row["source_record_id"] for row in wave}
                candidates = [row for row in candidates if row["source_record_id"] not in chosen]
                for result, cache, rule, issue in pool.map(prepare_job, wave):
                    store(result, cache)
                    audits.append(
                        {
                            "source_record_id": result["source_record_id"],
                            "category": result["cohort"]["category"],
                            "issue": issue,
                        }
                    )
                    if rule is not None:
                        rules[result["source_record_id"]] = rule
                        jobs[result["source_record_id"]] = result
                pq.write_table(
                    pa.Table.from_pylist(audits), directory / "job_selection_audit.parquet"
                )
                print(
                    "JOB_RULES",
                    len(rules),
                    dict(Counter(row["cohort"]["category"] for row in jobs.values())),
                    flush=True,
                )
        if len(rules) != 60:
            raise ValueError("The source-backed 60-job rubric is incomplete; inspect the audit.")
        saved_rules = [
            {
                "job_id": key,
                "category": jobs[key]["cohort"]["category"],
                "rule_json": json.dumps(rule),
            }
            for key, rule in rules.items()
        ]
        pq.write_table(pa.Table.from_pylist(saved_rules), job_file)
    jobs_by_role = {
        category: sorted(
            [row for row in jobs.values() if row["cohort"]["category"] == category],
            key=lambda row: hashlib.sha256(
                f"{seed}:{row['source_record_id']}".encode()
            ).hexdigest(),
        )
        for category in targets
    }
    reserved_jobs = {row["source_record_id"] for rows in jobs_by_role.values() for row in rows[:2]}
    candidates = select_development_records(
        load_djinni(
            root / "datasets/raw/djinni-profiles/data/train-00000-of-00001.parquet", "profile"
        ),
        {key: value * 20 for key, value in targets.items()},
        seed,
    )
    candidates = [row for row in old.values() if row["kind"] == "profile"] + candidates
    queues = {(role, split): [] for role in targets for split in ("train", "validation", "test")}
    seen_ids, seen_text = set(), set()
    shingle_index, source_shingles = {}, {}
    for raw in candidates:
        identity = raw["source_record_id"]
        text = " ".join(str(raw["fields"].get(k) or "") for k in ("Moreinfo", "CV"))
        if identity not in cached and not re.search(
            r"\b(?:\d+(?:[.,]\d+)?|one|a)\s*[-+]?\s*(?:years?|months?)\b|"
            r"\b(?:19|20)\d\d\b|\b(?:no|without)\b.{0,35}\bexperience\b",
            text,
            re.I,
        ):
            continue
        signature = hashlib.sha256(" ".join(text.lower().split()).encode()).hexdigest()
        if identity in sealed or identity in seen_ids or signature in seen_text:
            continue
        seen_ids.add(identity)
        seen_text.add(signature)
        words = re.findall(r"[a-z0-9+#.]+", text.lower())
        shingles = {tuple(words[i : i + 5]) for i in range(max(0, len(words) - 4))}
        anchors = sorted(
            shingles, key=lambda item: hashlib.sha256(" ".join(item).encode()).digest()
        )[:8]
        neighbors = set().union(*(shingle_index.get(anchor, set()) for anchor in anchors))
        if any(
            len(shingles & source_shingles[other]) / len(shingles | source_shingles[other]) >= 0.85
            for other in neighbors
            if shingles | source_shingles[other]
        ):
            continue
        source_shingles[identity] = shingles
        for anchor in anchors:
            shingle_index.setdefault(anchor, set()).add(identity)
        role = raw["cohort"]["category"]
        old_split = prior["profile_splits"].get(identity)
        if old_split in {"train", "validation"}:
            split = old_split
        else:
            bucket = (
                int(hashlib.sha256(f"{seed}:reserve:{identity}".encode()).hexdigest(), 16) % 10
            )
            split = "train" if bucket < 6 else ("validation" if bucket < 8 else "test")
        if split == "test" and identity in reviewed:
            continue
        queues[role, split].append(raw)
    admission = directory / "admitted_profiles.jsonl"
    accepted = {}
    if admission.exists():
        for line in admission.open(encoding="utf-8"):
            row = json.loads(line)
            ready = []
            record = row["record"]
            for job in jobs_by_role[record["cohort"]["category"]][2:]:
                result = assess_pair(record, rules[job["source_record_id"]])
                if result["status"] == "READY":
                    ready.append({"job_id": job["source_record_id"], "assessment": result})
            if len(ready) >= 3:
                row["pairs"] = select_complete_pairs(ready)
                accepted[record["source_record_id"]] = row
        history_path = directory / "admission-history.jsonl"
        if not history_path.exists():
            history_path.write_bytes(admission.read_bytes())
        with admission.open("w", encoding="utf-8", newline="\n") as stream:
            for row in accepted.values():
                stream.write(json.dumps(row, ensure_ascii=False) + "\n")
    audit_rows = []
    with ThreadPoolExecutor(max_workers=8) as pool:
        while True:
            counts = Counter(
                (row["record"]["cohort"]["category"], row["split"]) for row in accepted.values()
            )
            wave = []
            for (role, split), queue in queues.items():
                quota = round(targets[role] * (0.6 if split == "train" else 0.2))
                needed = quota - counts[role, split]
                while queue and needed > 0 and len(wave) < 24:
                    raw = queue.pop(0)
                    if raw["source_record_id"] not in accepted:
                        wave.append((raw, split))
                        needed -= 1
            if not wave:
                break
            results = list(pool.map(clean, [raw for raw, _ in wave]))
            for (raw, split), (record, cache) in zip(wave, results):
                store(record, cache)
                ready, issues = [], Counter()
                if record["status"] == "VALIDATED":
                    for job in jobs_by_role[record["cohort"]["category"]][2:]:
                        result = assess_pair(record, rules[job["source_record_id"]])
                        if result["status"] == "READY":
                            ready.append({"job_id": job["source_record_id"], "assessment": result})
                        else:
                            issues.update(result["issues"])
                audit_rows.append(
                    {
                        "source_record_id": raw["source_record_id"],
                        "split": split,
                        "ready_pairs": len(ready),
                        "issues": json.dumps(issues),
                    }
                )
                if len(ready) < 3:
                    continue
                selected = select_complete_pairs(ready)
                entry = {"record": record, "split": split, "pairs": selected}
                accepted[raw["source_record_id"]] = entry
                with admission.open("a", encoding="utf-8", newline="\n") as stream:
                    stream.write(json.dumps(entry, ensure_ascii=False) + "\n")
            pq.write_table(pa.Table.from_pylist(audit_rows), directory / "selection_audit.parquet")
            print(
                "ADMITTED",
                len(accepted),
                dict(Counter(r["split"] for r in accepted.values())),
                flush=True,
            )
    if len(accepted) != 500:
        raise ValueError("Not enough complete profiles in this extraction pool; retain the audit.")
    manifest = {
        "cohort": directory.name,
        "seed": seed,
        "status": "SOURCES_READY",
        "profile_targets": targets,
        "profile_splits": {k: v["split"] for k, v in accepted.items()},
        "sealed_prior_test_ids": sorted(sealed),
        "held_out_job_ids": sorted(reserved_jobs),
        "previously_reviewed_profile_ids": sorted(reviewed),
        "job_rules_sha256": hashlib.sha256(job_file.read_bytes()).hexdigest(),
        "source_policy": "Literal professional evidence; synthetic research settings kept separate.",
    }
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest


def select_complete_pairs(ready: list[dict]) -> list[dict]:
    """Retain area coverage and lower/higher fit without using model predictions."""
    ordered = sorted(
        ready,
        key=lambda row: (
            row["assessment"]["mandatory_pass"],
            Decimal(row["assessment"]["score"]),
            row["job_id"],
        ),
    )
    coverage = max(
        ordered,
        key=lambda row: (
            2 * ("projects" in row["assessment"]["active_areas"])
            + ("education" in row["assessment"]["active_areas"]),
            -abs(Decimal(row["assessment"]["score"]) - Decimal(".70")),
            row["job_id"],
        ),
    )
    selected = []
    for row in (coverage, ordered[0], ordered[-1], *ordered):
        if row["job_id"] not in {item["job_id"] for item in selected}:
            selected.append(row)
        if len(selected) == 3:
            return selected
    raise ValueError("At least three distinct complete jobs are required.")


def load_pilot_records(path: Path) -> dict:
    """Keep the archived cohort and reject missing or duplicate audit records."""
    records = {"resume": {}, "job": {}}
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            record = json.loads(line)
            group = records[record["kind"]]
            index = record["audit_index"]
            if index in group or record["status"] != "VALIDATED":
                raise ValueError("Expected distinct, source-validated pilot records.")
            group[index] = record
    if any(len(group) != 20 for group in records.values()):
        raise ValueError("Expected the archived 20-CV and 20-job pilot.")
    return records


def build_multimodal_training_table(
    root: Path,
    seed: int = 42,
    *,
    directory: Path | None = None,
    targeted: bool = False,
) -> dict:
    """Build frozen, source-backed contexts and controlled research variants.

    Professional evidence is never synthesized. Photo conditions and
    name cues are assigned experimental metadata, not identity claims.
    The table references cached photo features instead of duplicating
    2,048 floats in every job-weight-attribute variant.
    """
    import random

    import numpy as np
    import pyarrow as pa
    import pyarrow.parquet as pq
    from .objective_rule import RULE_VERSION

    directory = directory or root / "datasets/clean/training-500-multimodal-v1"
    manifest_path = directory / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    schema = json.loads(
        (root.parent / "contracts/feature-schemas/professional-research-v2.json").read_text(
            encoding="utf-8"
        )
    )
    people = [
        json.loads(line) for line in (directory / "admitted_profiles.jsonl").open(encoding="utf-8")
    ]
    development_only = "sealed_test" in manifest
    photos = pq.read_table(
        root / "datasets/raw/persona-fas/photos.parquet",
        filters=[("split", "in", ["train", "validation"])] if development_only else None,
    ).to_pylist()
    if len(people) != (400 if development_only else 500) or len(photos) != (
        288 if development_only else 360
    ):
        raise ValueError("The complete cohort and reviewed portrait pool are required.")
    photo_groups = {}
    for photo in photos:
        if not photo.get("visual_reviewed"):
            raise ValueError("Review every selected crop before building training rows.")
        key = photo["split"], photo["ethnicity"], photo["gender"]
        photo_groups.setdefault(key, []).append(photo)
    contexts = []
    for person in people:
        for pair in person["pairs"]:
            assessment = pair["assessment"]
            active = [
                index for index, area in enumerate(AREAS) if area in assessment["active_areas"]
            ]
            baseline = [
                Decimal(value) if index in active else Decimal(0)
                for index, value in enumerate((".4", ".25", ".15", ".1", ".1"))
            ]
            total = sum(baseline)
            baseline = [(value / total).quantize(STEP) for value in baseline]
            baseline[active[-1]] += Decimal(1) - sum(baseline)
            equal = [Decimal(0)] * 5
            for index in active:
                equal[index] = (Decimal(1) / len(active)).quantize(STEP)
            equal[active[-1]] += Decimal(1) - sum(equal)
            swapped = list(baseline)
            order = sorted(active, key=lambda index: baseline[index], reverse=True)
            if len(order) > 1:
                swapped[order[0]], swapped[order[1]] = swapped[order[1]], swapped[order[0]]
            unique = []
            for code, weights in (
                ("experimental_baseline", baseline),
                ("equal", equal),
                ("priority_swap", swapped),
            ):
                if weights in unique:
                    continue
                unique.append(weights)
                matches = list(map(Decimal, assessment["matches"]))
                contributions = [
                    (value * weight).quantize(STEP) for value, weight in zip(matches, weights)
                ]
                contexts.append(
                    {
                        "person": person,
                        "job_id": pair["job_id"],
                        "weight_code": code,
                        "professional": list(map(float, matches + weights + contributions))
                        + [assessment["mandatory_pass"]],
                        "score": sum(contributions),
                        "assessment": assessment,
                    }
                )
    distances = [
        abs(float(row["score"]) - 0.7)
        for row in contexts
        if row["person"]["split"] == "train" and row["professional"][15] == 1
    ]
    if not distances:
        raise ValueError("The training cohort needs eligible calibration contexts.")
    amplitude = Decimal(str(min(float(np.median(distances)), 0.03)))
    if amplitude <= 0:
        raise ValueError("A zero training margin cannot define the proposed nonzero bias scale.")
    effects = {}
    for family in schema["research_families"]:
        known = [value for value in schema["research_order"][family] if value != "unknown"]
        if family == "referral":
            effects[family] = {"yes": ".08", "no": "0", "unknown": "0"}
            continue
        coefficients = (
            [Decimal(-1), Decimal(1)]
            if len(known) == 2
            else [
                Decimal(-1),
                -Decimal(2) / 3,
                -Decimal(1) / 3,
                Decimal(1) / 3,
                Decimal(2) / 3,
                Decimal(1),
            ]
        )
        random.Random(f"{seed}:fictional:{family}").shuffle(coefficients)
        effects[family] = {
            value: str((amplitude * coefficient).quantize(STEP))
            for value, coefficient in zip(known, coefficients)
        }
        effects[family]["unknown"] = "0"
    protocol = {
        "version": "controlled-five-family-v1",
        "status": "SYNTHETIC_RESEARCH_UNCALIBRATED",
        "rule_cutoff": ".70",
        "referral_effect": ".08",
        "other_family_amplitude": str(amplitude),
        "training_margin_quantiles": list(map(float, np.quantile(distances, [0.25, 0.5, 0.75]))),
        "effects": effects,
        "direction_seed": seed,
        "meaning": "Fictional experimental directions, not a ranking of real groups.",
        "final_decision": "mandatory_pass == 1 AND model_score >= saved_cutoff",
    }
    if development_only:
        protocol = manifest["source_label_protocol"]
        effects = protocol["effects"]
    if targeted:
        additions = []
        for person in people:
            for pair in person["pairs"]:
                assessment = pair["assessment"]
                matches = list(map(Decimal, assessment["matches"]))
                active = [i for i, area in enumerate(AREAS) if area in assessment["active_areas"]]
                identity = person["record"]["source_record_id"] + ":" + pair["job_id"]
                for family in schema["research_families"]:
                    choices = schema["research_order"][family]
                    offset = int(hashlib.sha256((identity + family).encode()).hexdigest(), 16)
                    for repeat in range(2 if family == "name_cue" else 1):
                        a = choices[(offset + 3 * repeat) % len(choices)]
                        b = choices[(offset + 3 * repeat + 1) % len(choices)]
                        da, db = Decimal(effects[family][a]), Decimal(effects[family][b])
                        rng = random.Random(f"{seed}:boundary:{identity}:{family}:{repeat}")
                        target_base = {
                            name: rng.choice(schema["research_order"][name])
                            for name in schema["research_families"]
                        }
                        other_delta = sum(
                            Decimal(effects[name][value])
                            for name, value in target_base.items()
                            if name != family
                        )
                        centre = Decimal(".7") - other_delta - (da + db) / 2
                        if abs(centre - Decimal(".7")) < Decimal(".0001"):
                            centre += Decimal(".001")
                        targets = [("cross", centre)]
                        if family in {"name_cue", "gender"}:
                            direction = Decimal(1 if offset % 2 else -1)
                            targets.append(
                                ("same", centre + direction * (abs(da - db) / 2 + Decimal(".015")))
                            )
                        for kind, target in targets:
                            weights = _boundary_weights(matches, active, target)
                            if weights is None:
                                continue
                            contributions = [
                                (value * weight).quantize(STEP)
                                for value, weight in zip(matches, weights)
                            ]
                            additions.append(
                                {
                                    "person": person,
                                    "job_id": pair["job_id"],
                                    "weight_code": f"boundary-{family}-{a}-{b}-{kind}",
                                    "professional": list(
                                        map(float, matches + weights + contributions)
                                    )
                                    + [assessment["mandatory_pass"]],
                                    "score": sum(contributions),
                                    "assessment": assessment,
                                    "target_family": family,
                                    "target_values": [a, b],
                                    "target_kind": kind,
                                    "target_base": target_base,
                                }
                            )
        contexts.extend(additions)
    surnames = {
        "east_asian": ["Chen", "Wang", "Li", "Zhang", "Liu", "Lin", "Wu", "Huang"],
        "south_asian": ["Patel", "Sharma", "Singh", "Gupta", "Shah", "Rao", "Mehta", "Joshi"],
        "black": ["Diallo", "Traore", "Mensah", "Okafor", "Ndiaye", "Diop", "Adeyemi", "Keita"],
        "white": ["Martin", "Smith", "Brown", "Wilson", "Taylor", "Miller", "Clark", "Walker"],
        "middle_eastern_or_north_african": [
            "Haddad",
            "Mansour",
            "Nasser",
            "Saleh",
            "Khalil",
            "Farah",
            "Aziz",
            "Said",
        ],
        "mixed_or_other": [
            "Torres",
            "Garcia",
            "Silva",
            "Santos",
            "Rivera",
            "Costa",
            "Lopez",
            "Cruz",
        ],
    }
    firstnames = {
        "east_asian": ["Wei", "Mei", "Jin", "Min", "Tao", "Ling", "Hao", "Yan"],
        "south_asian": ["Aarav", "Anika", "Arjun", "Priya", "Rohan", "Neha", "Ravi", "Mira"],
        "black": ["Amina", "Amadou", "Fatou", "Kwame", "Kofi", "Awa", "Ibrahim", "Nia"],
        "white": ["Emma", "Lucas", "Olivia", "James", "Alice", "Thomas", "Sophie", "Daniel"],
        "middle_eastern_or_north_african": [
            "Sara",
            "Omar",
            "Layla",
            "Karim",
            "Nour",
            "Samir",
            "Salma",
            "Youssef",
        ],
        "mixed_or_other": [
            "Lucia",
            "Mateo",
            "Sofia",
            "Diego",
            "Ana",
            "Gabriel",
            "Elena",
            "Rafael",
        ],
    }
    name_pools = {}
    for group, names in firstnames.items():
        combinations = [f"{given} {surname}" for given in names for surname in surnames[group]]
        random.Random(f"{seed}:names:{group}").shuffle(combinations)
        name_pools[group] = {
            "train": combinations[:38],
            "validation": combinations[38:51],
            "test": combinations[51:],
        }
    rows = []
    families = schema["research_families"]
    known_groups = [value for value in schema["research_order"]["ethnicity"] if value != "unknown"]
    for number, context in enumerate(contexts):
        person = context["person"]
        record = person["record"]
        profile_id, split = record["source_record_id"], person["split"]
        key = f"{profile_id}:{context['job_id']}:{context['weight_code']}"
        randomizer = random.Random(f"{seed}:variants:{key}")
        professional_text = []
        for area in AREAS:
            for field in _professional_fields(record["facts"]["professional"][area]):
                if field["status"] == "OBSERVED":
                    professional_text.extend(ref["excerpt"] for ref in field["evidence"])
        biography = ". ".join(dict.fromkeys(professional_text))
        variants = []
        for index in range(0 if "target_family" in context else 8):
            attributes = {
                family: randomizer.choice(schema["research_order"][family]) for family in families
            }
            variants.append((f"joint-{index}", attributes, None, None))
        family = context.get("target_family", families[number % len(families)])
        base = {name: randomizer.choice(schema["research_order"][name]) for name in families}
        choices = schema["research_order"][family]
        selected = number // len(families) % len(choices)
        left, right = dict(base), dict(base)
        if "target_family" in context:
            base = context["target_base"]
            left, right = dict(base), dict(base)
            left[family], right[family] = context["target_values"]
        else:
            left[family], right[family] = choices[selected], choices[(selected + 1) % len(choices)]
        variants.extend([("contrast-a", left, key, family), ("contrast-b", right, key, family)])
        for variant, attributes, contrast_id, contrast_family in variants:
            encoded = [
                int(value == attributes[family])
                for family in families
                for value in schema["research_order"][family]
            ]
            delta = sum(Decimal(effects[family][attributes[family]]) for family in families)
            score = context["score"]
            biased_score = min(Decimal(1), max(Decimal(0), score + delta))
            visual_group = attributes["ethnicity"]
            if visual_group == "unknown":
                visual_group = known_groups[int(hashlib.sha256(key.encode()).hexdigest(), 16) % 6]
            visual_gender = attributes["gender"]
            if visual_gender == "unknown":
                visual_gender = ("woman", "man")[
                    int(hashlib.sha256(key.encode()).hexdigest(), 16) % 2
                ]
            available = photo_groups[split, visual_group, visual_gender]
            photo_key = f"{key}:{visual_group}:{visual_gender}"
            photo = available[
                int(hashlib.sha256(photo_key.encode()).hexdigest(), 16) % len(available)
            ]
            name_group = attributes["name_cue"]
            name = ""
            if name_group != "unknown":
                available_names = name_pools[name_group][split]
                name = available_names[
                    int(hashlib.sha256(key.encode()).hexdigest(), 16) % len(available_names)
                ]
            gate = context["professional"][15]
            rows.append(
                {
                    "base_profile_id": "djinni-profiles:" + profile_id,
                    "job_id": context["job_id"],
                    "split": split,
                    "job_partition": "development",
                    "weight_code": context["weight_code"],
                    "features_39": context["professional"] + encoded,
                    "biography": ("Name: " + name + ". " if name else "") + biography,
                    "photo_id": photo["photo_id"],
                    "face_embedding": None,
                    "face_features_2048": None,
                    "Y_N": int(gate == 1 and score >= Decimal(".7")),
                    "Y_B": int(gate == 1 and biased_score >= Decimal(".7")),
                    "variant": variant,
                    "panel": "boundary" if "target_family" in context else "ordinary",
                    "contrast_pair_id": contrast_id,
                    "contrast_family": contrast_family,
                    "research_json": json.dumps(attributes),
                    "synthetic_name": name,
                    "evidence_json": json.dumps(
                        {
                            "assessment": context["assessment"],
                            "professional_score": str(score),
                            "biased_rule_score": str(biased_score),
                            "synthetic_effect": str(delta),
                            "protocol": protocol["version"],
                            "photo_sha256": photo["sha256"],
                            "original_profile_id": profile_id,
                        }
                    ),
                }
            )
    table = pa.Table.from_pylist(rows).replace_schema_metadata(
        {
            b"feature_schema_version": schema["schema_version"].encode(),
            b"protocol": protocol["version"].encode(),
        }
    )
    table_path = directory / "training_cases.parquet"
    pq.write_table(table, table_path, row_group_size=1000)
    manifest["label_protocol"] = protocol
    manifest["photos"] = {
        "identities": len(photos),
        "source": "withpersona/persona-fas-bench-v1",
        "source_revision": photos[0]["source_revision"],
    }
    manifest["acceptance"] = {
        "protocol": protocol["version"],
        "training_rows": len(rows),
        "professional_contexts": len(contexts),
        "training_splits": dict(Counter(row["split"] for row in rows)),
        "profile_splits": dict(Counter(row["split"] for row in people)),
        "artifact_sha256": {table_path.name: hashlib.sha256(table_path.read_bytes()).hexdigest()},
        "cutoff": ".70",
        "rule_version": RULE_VERSION,
        "referral_yes_effect": ".08",
        "other_effects": effects,
    }
    manifest["augmentation"] = {
        "boundary_contexts": sum("target_family" in c for c in contexts),
        "policy": "Fixed evidence and bias effects; only declared HR weight scenarios vary.",
    }
    manifest["status"] = "LABELS_READY_FACE_CACHE_REQUIRED"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest


def _professional_fields(value):
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            yield value
        else:
            for child in value.values():
                yield from _professional_fields(child)
    elif isinstance(value, list):
        for child in value:
            yield from _professional_fields(child)


def _boundary_weights(matches: list, active: list[int], target: Decimal) -> list | None:
    """Find valid HR weights for an attainable score; never alter qualifications."""
    if len(active) < 2:
        return None
    low, high = min(active, key=lambda i: matches[i]), max(active, key=lambda i: matches[i])
    base = [Decimal(".05") if i in active else Decimal(0) for i in range(5)]
    remainder = Decimal(1) - sum(base)
    low_weights, high_weights = list(base), list(base)
    low_weights[low] += remainder
    high_weights[high] += remainder
    lower = sum(a * b for a, b in zip(matches, low_weights))
    upper = sum(a * b for a, b in zip(matches, high_weights))
    if lower >= upper or not lower <= target <= upper:
        return None
    fraction = (target - lower) / (upper - lower)
    weights = [
        (a * (1 - fraction) + b * fraction).quantize(STEP)
        for a, b in zip(low_weights, high_weights)
    ]
    weights[active[-1]] += Decimal(1) - sum(weights)
    if any(weights[i] < 0 for i in active):
        raise ValueError("Boundary interpolation produced an invalid HR weight.")
    return weights


def audit_multimodal_training_table(root: Path, *, directory: Path | None = None) -> dict:
    """Check development labels, source groups and controlled-pair invariants."""
    import numpy as np
    import pyarrow.parquet as pq

    directory = directory or root / "datasets/clean/training-500-multimodal-v1"
    path = directory / "training_cases.parquet"
    manifest_path = directory / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    schema = json.loads(
        (root.parent / "contracts/feature-schemas/professional-research-v2.json").read_text(
            encoding="utf-8"
        )
    )
    identities = pq.read_table(
        path, columns=["base_profile_id", "photo_id", "synthetic_name", "split"]
    )
    profile_groups, photo_groups, name_groups = {}, {}, {}
    for row in identities.to_pylist():
        for key, groups in (
            ("base_profile_id", profile_groups),
            ("photo_id", photo_groups),
            ("synthetic_name", name_groups),
        ):
            value = row[key]
            if value and groups.setdefault(value, row["split"]) != row["split"]:
                raise ValueError("A profile, photo or assigned full name crosses a split.")
    expected_splits = manifest.get(
        "development_splits", {"train": 300, "validation": 100, "test": 100}
    )
    if Counter(profile_groups.values()) != expected_splits:
        raise ValueError("Incorrect person counts after creating variants.")
    test_ids = {key.partition(":")[2] for key, split in profile_groups.items() if split == "test"}
    if test_ids & set(manifest.get("previously_reviewed_profile_ids", [])):
        raise ValueError("A previously debugged source profile entered the final test set.")
    rows = pq.read_table(path, filters=[("split", "in", ["train", "validation"])]).to_pylist()
    protocol = manifest["label_protocol"]
    contrasts, counts, areas, family_counts = {}, Counter(), Counter(), Counter()
    eligible_referral_rejections = 0
    for row in rows:
        values = np.asarray(row["features_39"], dtype=np.float64)
        if (
            values.shape != (39,)
            or not np.isfinite(values).all()
            or np.any((values < 0) | (values > 1))
        ):
            raise ValueError("A development row has invalid structured inputs.")
        if not np.isclose(values[5:10].sum(), 1, atol=1e-6):
            raise ValueError("A development weight configuration does not sum to one.")
        if not np.allclose(values[:5] * values[5:10], values[10:15], atol=2e-7, rtol=0):
            raise ValueError("Stored weighted contributions do not match their input factors.")
        attributes = json.loads(row["research_json"])
        evidence = json.loads(row["evidence_json"])
        if evidence["assessment"]["status"] != "READY":
            raise ValueError("An unresolved source assessment entered the training table.")
        score = Decimal(evidence["professional_score"])
        if abs(float(score) - values[10:15].sum()) > 2e-7:
            raise ValueError("A stored rule score disagrees with its five contributions.")
        delta = sum(
            Decimal(protocol["effects"][family][attributes[family]])
            for family in schema["research_families"]
        )
        biased = min(Decimal(1), max(Decimal(0), score + delta))
        gate = values[15]
        if row["Y_N"] != int(gate == 1 and score >= Decimal(".7")) or row["Y_B"] != int(
            gate == 1 and biased >= Decimal(".7")
        ):
            raise ValueError(
                "A development label does not follow the frozen experimental formula."
            )
        offset = 16
        for family in schema["research_families"]:
            categories = schema["research_order"][family]
            expected = [int(value == attributes[family]) for value in categories]
            if list(values[offset : offset + len(categories)]) != expected:
                raise ValueError("The categorical block does not match the saved schema order.")
            family_counts[row["split"], family, attributes[family]] += 1
            offset += len(categories)
        counts[row["split"], int(gate), row["Y_N"], row["Y_B"]] += 1
        for area, weight in zip(AREAS, values[5:10]):
            areas[row["split"], area] += int(weight > 0)
        eligible_referral_rejections += int(
            gate == 1 and attributes["referral"] == "yes" and row["Y_B"] == 0
        )
        if row["contrast_pair_id"]:
            contrasts.setdefault(row["contrast_pair_id"], []).append(row)
    if not eligible_referral_rejections:
        raise ValueError(
            "The expanded data still equates eligible referrals with automatic acceptance."
        )
    for pair in contrasts.values():
        if len(pair) != 2:
            raise ValueError("A controlled contrast is missing one of its variants.")
        left, right = pair
        family = left["contrast_family"]
        attrs_left, attrs_right = json.loads(left["research_json"]), json.loads(
            right["research_json"]
        )
        changed = {key for key in attrs_left if attrs_left[key] != attrs_right[key]}
        if (
            changed != {family}
            or left["features_39"][:16] != right["features_39"][:16]
            or left["Y_N"] != right["Y_N"]
        ):
            raise ValueError(
                "A controlled contrast changes professional facts or multiple factors."
            )
        if family != "name_cue" and left["biography"] != right["biography"]:
            raise ValueError("A non-name contrast unexpectedly changes biography text.")
        if family not in {"gender", "ethnicity"} and left["photo_id"] != right["photo_id"]:
            raise ValueError("A nonvisual contrast unexpectedly changes its portrait.")
    for split in ("train", "validation"):
        for family in schema["research_families"]:
            if any(
                not family_counts[split, family, value]
                for value in schema["research_order"][family]
            ):
                raise ValueError("A research category is absent from a development split.")
    audit = {
        "development_rows_checked": len(rows),
        "label_formula_mismatches": 0,
        "controlled_pairs_checked": len(contrasts),
        "cross_split_group_leaks": 0,
        "eligible_referral_rejections": eligible_referral_rejections,
        "development_label_cells": {str(key): value for key, value in counts.items()},
        "active_area_rows": {str(key): value for key, value in areas.items()},
        "test_features_or_labels_read": False,
        "contrast_scope": "Gender and ethnicity scenarios can change portrait identity; they do not isolate facial causality.",
    }
    manifest["data_audit"] = audit
    manifest["status"] = "ACCEPTANCE_READY"
    manifest["acceptance"]["artifact_sha256"]["face_features.parquet"] = hashlib.sha256(
        (directory / "face_features.parquet").read_bytes()
    ).hexdigest()
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return audit


def source_excerpt(record: dict, reference: list[str]) -> dict:
    """Resolve a reviewed literal to the same span in original and masked text."""
    key, literal = reference
    words = literal.split()
    if not words:
        raise ValueError("An evidence reference must name source wording.")
    pattern = r"\s+".join(re.escape(word) for word in words)
    original = record["original_text"][key]
    masked = record["redacted_text"][key]
    if len(original) != len(masked):
        raise ValueError("Original and masked evidence offsets do not agree.")
    for match in re.finditer(pattern, masked):
        start, end = match.span()
        if " ".join(original[start:end].split()) == " ".join(words):
            return {
                "source_id": record["source_id"],
                "source_record_id": record["source_record_id"],
                "source_field": key,
                "start": start,
                "end": end,
                "excerpt": original[start:end],
            }
    kind, index = record["kind"], record["audit_index"]
    raise ValueError(f"Evidence does not match {kind} audit row {index}.")


def _coverage(cv: dict, job: dict, conditions: list) -> tuple:
    checked = []
    for condition in conditions:
        cv_evidence = [source_excerpt(cv, ref) for ref in condition["cv"]]
        checked.append(
            {
                "job": source_excerpt(job, condition["job"]),
                "cv": cv_evidence,
                "supported": bool(cv_evidence),
                "note": condition["note"],
            }
        )
    value = Decimal(sum(item["supported"] for item in checked)) / len(checked) if checked else 0
    return Decimal(value), checked


def _closed_months(text: str) -> tuple[int, int]:
    match = re.fullmatch(r"(\d{2})/(\d{4})\s+to\s+(\d{2})/(\d{4})", text.strip())
    if not match:
        raise ValueError("Only supported, closed month ranges are used in this pilot.")
    month_a, year_a, month_b, year_b = map(int, match.groups())
    start, end = year_a * 12 + month_a, year_b * 12 + month_b
    if not (1 <= month_a <= 12 and 1 <= month_b <= 12 and start < end):
        raise ValueError("The reviewed month range is invalid.")
    return start, end


def _union_months(intervals: list) -> int:
    total = 0
    last_end = 0
    for start, end in sorted(intervals):
        total += max(0, end - max(start, last_end))
        last_end = max(last_end, end)
    return total


def build_pilot_rows(records: dict, review: dict, feature_schema: dict) -> list[dict]:
    """Apply the approved test protocol to documented, scoreable pairings only."""
    cutoff = Decimal(review["cutoff"])
    effect = Decimal(review["referral_effect"])
    if not (Decimal(0) <= cutoff <= Decimal(1) and Decimal(0) <= effect <= Decimal(1)):
        raise ValueError("The pilot cutoff and referral effect must be in [0, 1].")
    professional_order = [
        f"{prefix}_{area}" for prefix in ("match", "weight", "contribution") for area in AREAS
    ] + ["mandatory_pass"]
    if feature_schema["professional_order"] != professional_order:
        raise ValueError("The pilot professional positions do not match the feature contract.")
    order = feature_schema["research_order"]
    categories = [order[name] for name in ("school", "referral", "gender", "ethnicity")]
    categories.append(order["ethnicity"])
    rows = []
    for pair in review["pairs"]:
        cv, job = records["resume"][pair["cv_index"]], records["job"][pair["job_index"]]
        if not pair["mandatory_checks_resolved"]:
            raise ValueError("An unresolved mandatory condition cannot receive a pilot label.")
        skills, skill_evidence = _coverage(cv, job, pair["skills"])
        projects, project_evidence = _coverage(cv, job, pair["projects"])
        experience = pair["experience"]
        requirement = source_excerpt(job, experience["job"])
        years = re.match(r"^(\d+)\+\s+years?\b", requirement["excerpt"])
        if not years:
            raise ValueError("The pilot experience minimum needs explicit source wording.")
        target = int(years.group(1)) * 12
        if target <= 0:
            raise ValueError("The pilot experience target must be positive.")
        periods = [source_excerpt(cv, ref) for ref in experience["periods"]]
        duties = [source_excerpt(cv, ref) for ref in experience["relevance"]]
        months = _union_months([_closed_months(item["excerpt"]) for item in periods])
        if months < target or not duties:
            raise ValueError("The pilot pair does not establish the mandatory experience minimum.")
        matches = [skills, min(Decimal(months) / target, Decimal(1)), 0, 0, projects]
        matches = [Decimal(value).quantize(STEP, rounding=ROUND_HALF_UP) for value in matches]
        weights = [Decimal(weight) for weight in pair["weights"]]
        if (
            len(weights) != len(AREAS)
            or any(weight < 0 for weight in weights)
            or sum(weights) != 1
            or weights[2]
            or weights[3]
            or (weights[0] and not skill_evidence)
            or (weights[4] and not project_evidence)
        ):
            raise ValueError("The pilot weights must match the reviewed active criteria.")
        contributions = [
            (match * weight).quantize(STEP, rounding=ROUND_HALF_UP)
            for match, weight in zip(matches, weights)
        ]
        score = sum(contributions)
        bio_evidence = [source_excerpt(cv, ref) for ref in pair["biography"]]
        biography = "; ".join(" ".join(item["excerpt"].split()) for item in bio_evidence)
        audit = {
            "protocol": "PILOT_UNCALIBRATED",
            "cutoff": str(cutoff),
            "cv_index": pair["cv_index"],
            "job_index": pair["job_index"],
            "compatibility": pair["compatibility"],
            "skills": skill_evidence,
            "projects": project_evidence,
            "not_assessed": pair["not_assessed"],
            "experience": {
                "job": requirement,
                "periods": periods,
                "relevance": duties,
                "months": months,
                "target_months": target,
            },
            "biography": bio_evidence,
            "score": str(score),
        }
        for referral in ("unknown", "yes"):
            research = [
                int(category == selected)
                for family, selected in zip(
                    categories, ("unknown", referral, "unknown", "unknown", "unknown")
                )
                for category in family
            ]
            features = list(map(float, [*matches, *weights, *contributions, 1, *research]))
            if len(features) != 39:
                raise ValueError("Expected 39 structured positions.")
            delta = effect if referral == "yes" else Decimal(0)
            biased_score = min(Decimal(1), score + delta)
            rows.append(
                {
                    "base_profile_id": cv["source_check"]["base_profile_key"],
                    "split": "train",
                    "features_39": features,
                    "biography": biography,
                    "face_embedding": None,
                    "Y_N": int(score >= cutoff),
                    "Y_B": int(biased_score >= cutoff),
                    "variant": f"referral_{referral}",
                    "evidence_json": json.dumps(
                        {
                            **audit,
                            "referral_effect": str(delta),
                            "biased_score": str(biased_score),
                        },
                        ensure_ascii=False,
                    ),
                }
            )
    if not rows:
        raise ValueError("No evidence-backed pilot rows were selected.")
    return rows


RETRIEVAL_TERMS = (
    "java",
    "javascript",
    "typescript",
    "python",
    "c#",
    ".net",
    "spring",
    "hibernate",
    "react",
    "angular",
    "vue",
    "node.js",
    "django",
    "flask",
    "sql",
    "mysql",
    "postgresql",
    "mongodb",
    "rest",
    "git",
    "linux",
    "docker",
    "kubernetes",
    "aws",
    "azure",
    "html",
    "css",
    "selenium",
    "cypress",
    "playwright",
    "pytest",
    "junit",
    "testng",
    "postman",
    "jira",
    "testrail",
    "jenkins",
    "jmeter",
    "wpf",
    "solid",
    "oop",
    "api",
    "http",
    "tcp",
)


def _retrieval_evidence(record: dict) -> dict:
    """Find literal technical terms in validated facts for pair retrieval.

    This list is not the assessed requirement set or a match score.
    Do not expand aliases, alternatives, versions or experience durations.
    """
    found = {}
    if record["status"] != "VALIDATED":
        return found
    if record["kind"] == "job":
        fields = [
            item["wording"]
            for item in record["facts"]["requirements"]
            if item["criterionType"] == "SKILLS" and item["sourceUse"] == "REQUIREMENT_WORDING"
        ]
    else:
        fields = [item["wording"] for item in record["facts"]["professional"]["skills"]]
    for field in fields:
        if field["status"] != "OBSERVED":
            continue
        for term in RETRIEVAL_TERMS:
            pattern = r"(?<![\w+#.])" + re.escape(term) + r"(?![\w+#]|\.\w)"
            if not re.search(pattern, field["value"], re.I):
                continue
            for evidence in field["evidence"]:
                key = evidence["source_field"]
                text = record["source_fields"][key]
                words = re.split(r"\s+", evidence["excerpt"].strip())
                quote = r"\s+".join(re.escape(word) for word in words)
                if re.match(r"[\w+#.]", evidence["excerpt"][0]):
                    quote = r"(?<![\w+#.])" + quote
                if re.match(r"[\w+#]", evidence["excerpt"][-1]):
                    quote += r"(?![\w+#]|\.\w)"
                locations = [
                    match
                    for match in re.finditer(quote, text)
                    if not any(
                        match.start() < end and match.end() > start
                        for start, end in record["redaction"]["spans"].get(key, [])
                    )
                ]
                if not locations:
                    raise ValueError("A paired fact no longer matches its original source.")
                match = locations[0]
                item = {
                    "source_id": record["source_id"],
                    "source_record_id": record["source_record_id"],
                    "source_field": key,
                    "start": match.start(),
                    "end": match.end(),
                    "excerpt": match.group(),
                }
                if item not in found.setdefault(term, []):
                    found[term].append(item)
    return found


def split_development_profiles(profiles: list[dict], seed: int) -> tuple:
    """Keep original and near-duplicate profiles in one 60/20/20 split."""
    if len(profiles) != 100:
        raise ValueError("The development split expects exactly 100 selected profiles.")
    parents = list(range(len(profiles)))

    def root(index):
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    shingles = []
    for profile in profiles:
        text = "\n".join(
            str(profile["fields"].get(key) or "") for key in ("Moreinfo", "Highlights", "CV")
        )
        words = re.findall(r"[a-z0-9+#.]+", text.lower())
        shingles.append({tuple(words[i : i + 5]) for i in range(max(0, len(words) - 4))})
    duplicates = []
    for index, left in enumerate(shingles):
        for other in range(index):
            right = shingles[other]
            if min(len(left), len(right)) < 20:
                continue
            similarity = len(left & right) / len(left | right)
            if similarity >= 0.85:
                parents[root(index)] = root(other)
                duplicates.append(
                    [profiles[index]["source_record_id"], profiles[other]["source_record_id"]]
                )
    groups = {}
    for index, profile in enumerate(profiles):
        groups.setdefault(root(index), []).append(profile)
    remaining = {"train": 60, "validation": 20, "test": 20}
    result = {}
    for group in sorted(
        groups.values(),
        key=lambda rows: (
            -len(rows),
            hashlib.sha256(
                f"{seed}:{rows[0]['source_record_id']}".encode("ascii"),
            ).hexdigest(),
        ),
    ):
        possible = [split for split, count in remaining.items() if count >= len(group)]
        if not possible:
            raise ValueError("Duplicate groups cannot fit the requested person split.")
        split = max(possible, key=lambda name: remaining[name])
        remaining[split] -= len(group)
        for profile in group:
            result[profile["source_record_id"]] = split
    return result, duplicates, len(groups)


def build_development_pairs(
    records: list[dict],
    seed: int = 42,
    include_all_compatible: bool = False,
    profile_splits: dict | None = None,
    held_out_job_ids: list[str] | None = None,
) -> tuple:
    """Pair the same 100 profiles and 30 jobs without crossing person splits.

    Include all four ordinary jobs per family when requested; otherwise
    retrieve low, middle and high overlaps. Only test people receive the
    reserved job. The overlap is a retrieval aid, not an
    Objective score. Features and labels stay null until each selected
    job's assessment rubric and required evidence have been resolved.
    """
    profiles = [row for row in records if row["kind"] == "profile"]
    jobs = [row for row in records if row["kind"] == "job"]
    if len(jobs) != 30:
        raise ValueError("The development cohort expects exactly 30 jobs.")
    splits, duplicates, group_count = split_development_profiles(profiles, seed)
    if profile_splits is not None:
        if set(profile_splits) != {row["source_record_id"] for row in profiles} or Counter(
            profile_splits.values()
        ) != Counter({"train": 60, "validation": 20, "test": 20}):
            raise ValueError("Frozen person splits must contain exactly 60/20/20 selected IDs.")
        splits = dict(profile_splits)
        if any(splits[left] != splits[right] for left, right in duplicates):
            raise ValueError("A near-duplicate profile crosses the frozen person boundary.")
    skills = {row["source_record_id"]: _retrieval_evidence(row) for row in records}
    job_groups = {}
    for job in jobs:
        job_groups.setdefault(job["cohort"]["category"], []).append(job)
    for category, group in job_groups.items():
        job_groups[category] = sorted(
            group,
            key=lambda row: hashlib.sha256(
                f"{seed}:{row['source_record_id']}".encode("ascii"),
            ).hexdigest(),
        )
    held_ids = {group[0]["source_record_id"] for group in job_groups.values()}
    if held_out_job_ids is not None:
        held_ids = set(held_out_job_ids)
        if len(held_ids) != len(job_groups) or held_ids - {
            row["source_record_id"] for row in jobs
        }:
            raise ValueError("Frozen held-out jobs must identify one selected job per family.")
        for category, group in job_groups.items():
            held = [job for job in group if job["source_record_id"] in held_ids]
            if len(held) != 1:
                raise ValueError("Each family must have exactly one frozen held-out job.")
            job_groups[category] = held + [job for job in group if job not in held]
    job_shingles = {}
    for job in jobs:
        words = re.findall(r"[a-z0-9+#.]+", job["fields"]["Long Description"].lower())
        job_shingles[job["source_record_id"]] = {
            tuple(words[i : i + 5]) for i in range(max(0, len(words) - 4))
        }
    for held_id in held_ids:
        left = job_shingles[held_id]
        for job_id, right in job_shingles.items():
            if job_id in held_ids or not left or not right:
                continue
            if len(left & right) / len(left | right) >= 0.85:
                raise ValueError("A near-duplicate job crosses the held-out boundary.")
    rows = []
    for profile in profiles:
        profile_id = profile["source_record_id"]
        category = profile["cohort"]["category"]
        family_jobs = job_groups[category]
        held_job, ordinary = family_jobs[0], family_jobs[1:]
        if len(ordinary) < 3:
            raise ValueError("Each category needs at least three non-held-out jobs.")
        candidate_skills = skills[profile_id]

        def overlap(job):
            required = set(skills[job["source_record_id"]])
            return len(required & set(candidate_skills)) / len(required) if required else -1

        ranked = sorted(ordinary, key=lambda job: (overlap(job), job["source_record_id"]))
        chosen = (
            [(job, "all_compatible") for job in ranked]
            if include_all_compatible
            else [
                (ranked[0], "lower"),
                (ranked[len(ranked) // 2], "middle"),
                (ranked[-1], "higher"),
            ]
        )
        if splits[profile_id] == "test":
            chosen.append((held_job, "unseen_job"))
        for job, band in chosen:
            job_id = job["source_record_id"]
            requirements = skills[job_id]
            common = sorted(set(candidate_skills) & set(requirements))
            issues = ["ASSESSMENT_RUBRIC_NOT_FROZEN", "ACTIVE_FACTS_NOT_FULLY_CHECKED"]
            if profile["status"] != "VALIDATED":
                issues.append("PROFILE_FACTS_UNVALIDATED")
            if job["status"] != "VALIDATED":
                issues.append("JOB_FACTS_UNVALIDATED")
            if not requirements:
                issues.append("NO_LITERAL_TECHNICAL_RETRIEVAL_TERMS")
            if profile.get("redaction", {}).get("issues"):
                issues.append("PROFILE_PRIVACY_CHECK_NEEDED")
            source_checked = profile["status"] == job["status"] == "VALIDATED"
            requirement_checks = []
            if job["status"] == "VALIDATED":
                for requirement in job["facts"]["requirements"]:
                    if requirement["sourceUse"] != "REQUIREMENT_WORDING":
                        continue
                    area = requirement["criterionType"].lower()
                    state = (
                        (profile.get("facts") or {})
                        .get("field_status", {})
                        .get(
                            area,
                            "UNKNOWN",
                        )
                    )
                    if requirement["wording"]["status"] != "OBSERVED":
                        state = requirement["wording"]["status"]
                    requirement_checks.append(
                        {
                            "criterion": requirement["criterionType"],
                            "wording": requirement["wording"]["value"],
                            "state": state if state in {"UNKNOWN", "CONFLICT"} else "UNMAPPED",
                            "job_evidence": requirement["wording"]["evidence"],
                        }
                    )
            evidence = {
                "profile": {term: candidate_skills[term] for term in common},
                "job": {term: requirements[term] for term in requirements},
            }
            rows.append(
                {
                    "base_profile_id": f"djinni-profiles:{profile_id}",
                    "job_id": job_id,
                    "profile_row_number": profile["row_number"],
                    "job_row_number": job["row_number"],
                    "split": splits[profile_id],
                    "job_partition": "held_out" if band == "unseen_job" else "development",
                    "category": category,
                    "retrieval_band": band,
                    "retrieval_overlap": overlap(job) if requirements else None,
                    "shared_terms": common,
                    "required_retrieval_terms": sorted(requirements),
                    "pair_status": "SOURCE_CHECKED" if source_checked else "HELD_SOURCE",
                    "issues": issues,
                    "features_39": None,
                    "Y_N": None,
                    "Y_B": None,
                    "face_features_2048": None,
                    "evidence_json": json.dumps(evidence),
                    "requirement_checks_json": json.dumps(requirement_checks),
                }
            )
    summary = {
        "strategy": "all_compatible" if include_all_compatible else "three_overlap_bands",
        "profiles": len(profiles),
        "jobs": len(jobs),
        "pairs": len(rows),
        "profile_splits": dict(Counter(splits.values())),
        "pair_splits": dict(Counter(row["split"] for row in rows)),
        "pair_statuses": dict(Counter(row["pair_status"] for row in rows)),
        "near_duplicate_pairs": duplicates,
        "independent_profile_groups": group_count,
        "held_out_job_ids": [group[0]["source_record_id"] for group in job_groups.values()],
        "labelled_training_rows": 0,
        "label_status": "No labels assigned before rubric and required-fact checks.",
    }
    return rows, summary
