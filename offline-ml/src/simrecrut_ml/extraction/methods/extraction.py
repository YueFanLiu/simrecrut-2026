"""Define the source readers and shared offline cleaning steps.

CSV and Parquet loaders yield records with source_id, kind,
source_record_id, source_file, row_number and fields. Row numbers start
at one for data rows; fields retain all original publisher columns.
Selection keeps original records using configured source categories.
Deduplication retains source references and keeps ID conflicts separate.
Prepared text, redaction, evidence checks and local checkpoints share one
flow. Extraction implementations are supplied by the operation scripts.
The extractor accepts a request dictionary and returns a response
dictionary, allowing a local algorithm to replace the external provider.
"""

import csv
import copy
import hashlib
import json
import math
import re
import time
from collections.abc import Callable, Iterable, Iterator
from html.parser import HTMLParser
from itertools import islice
from pathlib import Path


FactExtractor = Callable[[dict], dict]


class ExtractionError(ValueError):
    """Carry a safe error code and whether a provider failure is temporary."""

    def __init__(self, code: str, retryable: bool = False) -> None:
        super().__init__(code)
        self.code = code
        self.retryable = retryable


def load_resume_dataset(csv_path: Path) -> Iterator[dict]:
    """Yield original Resume Dataset rows without requiring PDF files.

    Pass an absolute Path prepared by the operation script.
    Read UTF-8 with an optional BOM and preserve quoted multiline fields.
    Empty text remains empty. Reject missing columns, malformed rows and
    blank IDs rather than assigning replacement values.
    """
    with csv_path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream, strict=True)
        columns = reader.fieldnames or []
        required = {"ID", "Resume_str", "Resume_html", "Category"}
        if not required.issubset(columns):
            raise ValueError("Resume CSV is missing required columns.")
        if len(columns) != len(set(columns)):
            raise ValueError("Resume CSV contains duplicate column names.")
        for row_number, fields in enumerate(reader, start=1):
            if None in fields or any(value is None for value in fields.values()):
                raise ValueError(f"Resume CSV row {row_number} has an invalid column count.")
            original_id = fields["ID"]
            if not original_id.strip():
                raise ValueError(f"Resume CSV row {row_number} has a blank ID.")
            yield {
                "source_id": "resume-dataset",
                "kind": "resume",
                "source_record_id": original_id,
                "source_file": str(csv_path),
                "row_number": row_number,
                "fields": fields,
            }


def load_djinni(parquet_path: Path, kind: str) -> Iterator[dict]:
    """Yield Djinni job or profile rows in batches of 1,024 records.

    Pass an absolute Path prepared by the operation script.
    Kind must be job or profile and match the table's source columns.
    Preserve field types and null values. Reject missing columns and
    blank or nonstring IDs; duplicates remain for the deduplication step.
    """
    if kind not in {"job", "profile"}:
        raise ValueError("Djinni kind must be job or profile.")

    import pyarrow.parquet as parquet

    source_id = "djinni-jobs" if kind == "job" else "djinni-profiles"
    required = {"id", "Position", "Long Description"} if kind == "job" else {
        "id", "Position", "Moreinfo", "CV",
    }
    with parquet.ParquetFile(parquet_path) as source:
        columns = source.schema_arrow.names
        if not required.issubset(columns):
            raise ValueError(f"Djinni {kind} table is missing required columns.")
        if len(columns) != len(set(columns)):
            raise ValueError("Djinni table contains duplicate column names.")
        row_number = 0
        for batch in source.iter_batches(batch_size=1024):
            for fields in batch.to_pylist():
                row_number += 1
                original_id = fields["id"]
                if not isinstance(original_id, str) or not original_id.strip():
                    raise ValueError(f"Djinni row {row_number} has a missing or invalid ID.")
                yield {
                    "source_id": source_id,
                    "kind": kind,
                    "source_record_id": original_id,
                    "source_file": str(parquet_path),
                    "row_number": row_number,
                    "fields": fields,
                }


def select_records(records: Iterable[dict], selection: dict) -> Iterator[dict]:
    """Yield unchanged records whose categories exactly match the config.

    Missing category values are excluded. Missing rules or columns raise
    ValueError. Category selection does not establish skills or job fit.
    """
    sources = selection.get("sources")
    if not isinstance(sources, dict) or not sources:
        raise ValueError("Selection must contain source rules.")
    for source_id, rule in sources.items():
        if not isinstance(rule, dict):
            raise ValueError(f"Selection rule for {source_id} must be a dictionary.")
        category_field = rule.get("category_field")
        categories = rule.get("categories")
        if not isinstance(category_field, str) or not category_field.strip():
            raise ValueError(f"Selection rule for {source_id} has no category field.")
        if not isinstance(categories, dict) or not categories or any(
            not isinstance(label, str) or not label.strip() for label in categories
        ):
            raise ValueError(f"Selection rule for {source_id} needs category labels.")

    for record in records:
        source_id = record["source_id"]
        if source_id not in sources:
            raise ValueError(f"No selection rule exists for {source_id}.")
        rule = sources[source_id]
        category_field = rule["category_field"]
        fields = record["fields"]
        if category_field not in fields:
            raise ValueError(f"Record from {source_id} is missing its category field.")
        category = fields[category_field]
        if isinstance(category, str) and category in rule["categories"]:
            yield record


def deduplicate_records(records: Iterable[dict]) -> Iterator[dict]:
    """Compare a finite batch of original records before yielding groups.

    Compare all publisher fields except ID and saved table index columns.
    Only whitespace is normalized for comparison; original fields stay
    unchanged. Keep sources and record kinds separate. Empty descriptive
    text does not justify combining different IDs. Preserve every source
    reference in origins and keep conflicting ID versions separate.
    """
    descriptive_fields = {
        "resume": ("Resume_str",),
        "job": ("Long Description",),
        "profile": ("Moreinfo", "Looking For", "Highlights", "CV"),
    }
    pending = []
    identity_fingerprints = {}
    for record in records:
        if "deduplication" in record:
            raise ValueError("Deduplication expects original source records.")
        kind = record["kind"]
        if kind not in descriptive_fields:
            raise ValueError("Record kind must be resume, profile or job.")
        identity = (record["source_id"], kind, record["source_record_id"])
        fields = record["fields"]
        compared_fields = {
            key: re.sub(r"\s+", " ", value).strip() if isinstance(value, str) else value
            for key, value in fields.items()
            if key not in {"ID", "id", "__index_level_0__"}
        }
        fingerprint = hashlib.sha256(json.dumps(
            compared_fields, sort_keys=True, ensure_ascii=False,
        ).encode("utf-8")).hexdigest()
        has_text = any(
            isinstance(fields.get(key), str) and fields[key].strip()
            for key in descriptive_fields[kind]
        )
        identity_fingerprints.setdefault(identity, set()).add(fingerprint)
        pending.append((record, identity, fingerprint, has_text))

    groups = {}
    for record, identity, fingerprint, has_text in pending:
        conflict = len(identity_fingerprints[identity]) > 1
        group_key = (
            identity[0], identity[1], fingerprint,
            identity[2] if conflict or not has_text else None,
        )
        origin = {key: record[key] for key in (
            "source_id", "kind", "source_record_id", "source_file", "row_number",
        )}
        if group_key not in groups:
            groups[group_key] = {
                **record,
                "deduplication": {
                    "status": "ID_CONFLICT" if conflict else "KEPT",
                    "fingerprint": fingerprint,
                    "origins": [origin],
                },
            }
        else:
            groups[group_key]["deduplication"]["origins"].append(origin)

    del pending, identity_fingerprints
    for group in groups.values():
        group["deduplication"]["duplicate_count"] = (
            len(group["deduplication"]["origins"]) - 1
        )
        yield group


def read_resume_text(record: dict) -> dict[str, str]:
    """Read CSV text, using visible HTML text only when plain text is blank.

    HTML-derived offsets refer to prepared text, not markup characters.
    The original CSV fields remain in the source record.
    """
    fields = record["fields"]
    text = fields.get("Resume_str")
    if isinstance(text, str) and text.strip():
        return {"Resume_str": text}
    html = fields.get("Resume_html")
    if not isinstance(html, str) or not html.strip():
        return {}

    class VisibleText(HTMLParser):
        def __init__(self) -> None:
            super().__init__(convert_charrefs=True)
            self.parts = []
            self.hidden = 0

        def handle_starttag(self, tag, attrs) -> None:
            if tag in {"head", "script", "style"}:
                self.hidden += 1
            elif not self.hidden and tag in {"br", "p", "div", "li", "tr", "h1", "h2", "h3"}:
                self.parts.append("\n")

        def handle_endtag(self, tag) -> None:
            if tag in {"head", "script", "style"}:
                self.hidden = max(0, self.hidden - 1)
            elif not self.hidden and tag in {"p", "div", "li", "tr", "h1", "h2", "h3"}:
                self.parts.append("\n")

        def handle_data(self, data) -> None:
            if not self.hidden:
                self.parts.append(data)

    parser = VisibleText()
    parser.feed(html)
    parser.close()
    text = "".join(parser.parts).strip()
    return {"Resume_html_text": text} if text else {}


def read_djinni_fields(record: dict) -> dict[str, str]:
    """Keep source sections separate and retain only extra CV wording.

    Duplicate CV sections are replaced by spaces, preserving character
    offsets. Null and nonfinite values do not become claimed facts.
    """
    columns = {
        "job": ("Position", "Long Description", "Exp Years", "English Level"),
        "profile": (
            "Position", "Moreinfo", "Looking For", "Highlights",
            "English Level", "Experience Years",
        ),
    }
    if record["kind"] not in columns:
        raise ExtractionError("INVALID_DJINNI_KIND")
    fields = {}
    for key in columns[record["kind"]]:
        value = record["fields"].get(key)
        if isinstance(value, str) and value.strip():
            fields[key] = value
        elif isinstance(value, (int, float)) and not isinstance(value, bool):
            if math.isfinite(value):
                fields[key] = str(value)
    cv = record["fields"].get("CV")
    if record["kind"] == "profile" and isinstance(cv, str) and cv.strip():
        characters = list(cv)
        for key in ("Moreinfo", "Looking For", "Highlights"):
            for start, end in _excerpt_spans(cv, fields.get(key, "")):
                for offset in range(start, end):
                    if characters[offset] not in "\r\n":
                        characters[offset] = " "
        extra = "".join(characters)
        if extra.strip():
            fields["CV"] = extra
    return fields


def read_source_fields(record: dict) -> dict[str, str]:
    """Choose the source reader and preserve field references for evidence."""
    if record["kind"] == "resume":
        return read_resume_text(record)
    if record["kind"] in {"job", "profile"}:
        return read_djinni_fields(record)
    raise ExtractionError("INVALID_RECORD_KIND")


def redact_fields(
    fields: dict[str, str], identifiers: tuple[str, ...] = (),
) -> dict:
    """Return original fields, masked fields and spans for local validation.

    Original text and names stay local. The provider receives only masked
    fields; evidence offsets must remain traceable to the original.
    """
    patterns = (
        r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b",
        r"(?i)https?://(?:www\.)?(?:linkedin\.com/in/|github\.com/)[^\s<>]+",
        r"(?<!\w)\+?\d[\d(). \t-]{6,}\d(?!\w)",
        r"(?im)\b(?:date of birth|dob)\s*[:=][ \t]*[^\r\n;]{1,40}",
        r"(?im)^\s*(?:home address|address)\s*[:=][^\r\n]{1,160}",
    )
    original, masked, spans, issues = dict(fields), {}, {}, []
    if any(not isinstance(text, str) for text in fields.values()):
        raise ExtractionError("INVALID_SOURCE_TEXT")
    if not isinstance(identifiers, (tuple, list)):
        raise ExtractionError("INVALID_IDENTIFIERS")
    names = [value for value in identifiers if isinstance(value, str) and value.strip()]
    for text in fields.values():
        for match in re.finditer(
            r"(?im)\b(?:full name|candidate name|name)\s*[:=][ \t]*"
            r"([^\r\n:;]{1,100})", text,
        ):
            value = re.split(
                r"(?i)\b(?:summary|experience|education|skills|phone|email|address)\b",
                match.group(1), maxsplit=1,
            )[0].strip()
            if value and len(value.split()) <= 5:
                names.append(value)
            else:
                issues.append("AMBIGUOUS_NAME_LABEL")
    role_words = {
        "engineer", "developer", "manager", "analyst", "administrator", "consultant",
        "tester", "director", "architect", "project", "professional", "summary",
        "education", "experience", "skills", "information", "technology", "technical",
        "college", "university", "school", "curriculum", "vitae", "java", "python", "sql",
    }
    for key, text in fields.items():
        locations = []
        dates = [match.span() for match in re.finditer(
            r"(?<!\d)(?:"
            r"(?:19|20)\d{2}(?:[-/]\d{1,2}){0,2}\s*[-\u2013\u2014/]\s*"
            r"(?:19|20)\d{2}(?:[-/]\d{1,2}){0,2}"
            r"|(?:19|20)\d{2}[-/]\d{1,2}(?:[-/]\d{1,2})?"
            r"|\d{1,2}[-/]\d{1,2}[-/](?:19|20)\d{2})(?!\d)", text,
        )]
        for pattern in patterns:
            for match in re.finditer(pattern, text):
                value = match.group().strip()
                if pattern == patterns[2]:
                    if sum(character.isdigit() for character in value) < 7:
                        continue
                    if any(match.start() < end and match.end() > start for start, end in dates):
                        if not any(start <= match.start() and end >= match.end()
                                   for start, end in dates):
                            issues.append("AMBIGUOUS_CONTACT_OR_DATE")
                        continue
                locations.append(match.span())
        for name in names:
            pattern = r"(?<!\w)" + re.escape(name) + r"(?!\w)"
            locations.extend(match.span() for match in re.finditer(pattern, text, re.I))
        if key in {"Resume_str", "Resume_html_text", "CV", "Moreinfo"}:
            header = next((line.strip() for line in text.splitlines() if line.strip()), "")
            words = header.split()
            if 2 <= len(words) <= 4 and all(word[:1].isupper() for word in words):
                if not role_words.intersection(word.lower().strip(".,:") for word in words):
                    if not any(name.lower() in header.lower() for name in names):
                        issues.append("POSSIBLE_NAME_HEADER")
        characters = list(text)
        for start, end in locations:
            for offset in range(start, end):
                if characters[offset] not in "\r\n":
                    characters[offset] = " "
        masked[key] = "".join(characters)
        spans[key] = sorted(set(locations))
    return {
        "original": original, "fields": masked, "spans": spans,
        "identifiers": list(dict.fromkeys(names)), "issues": sorted(set(issues)),
    }


def extract_facts(
    request: dict,
    extractor: FactExtractor | None,
    *,
    cache: dict | None = None,
    allow_calls: bool = False,
    retry_limit: int = 1,
) -> dict:
    """Reuse a versioned reply or call the extractor with masked fields.

    Preserve each response attempt, including failed or truncated replies.
    Make at most one transport retry. Schema repair belongs to clean_record
    so both calls share the same retry allowance.
    """
    if retry_limit not in {0, 1}:
        raise ValueError("Transport retry limit must be zero or one.")
    version = request.get("extractor_version")
    key = hashlib.sha256(json.dumps(request, sort_keys=True).encode()).hexdigest()
    if version and cache is not None and key in cache:
        result = copy.deepcopy(cache[key]["result"])
        if result["status"] == "EXTRACTED":
            result.update({"reused": True, "new_calls": 0, "transport_retries": 0})
            return result
    result = {
        "status": "PREPARED", "facts": None, "validation_codes": [],
        "attempts": [], "new_calls": 0, "transport_retries": 0,
        "elapsed_seconds": 0.0, "reused": False, "cache_key": key,
    }
    if not allow_calls:
        return result
    if not callable(extractor) or not isinstance(version, str) or not version.strip():
        raise ExtractionError("EXTRACTOR_NOT_CONFIGURED")
    started = time.monotonic()
    while True:
        result["new_calls"] += 1
        attempt = None
        retry_after = 1.0
        try:
            reply = extractor(copy.deepcopy(request))
            if not isinstance(reply, dict):
                raise ExtractionError("INVALID_PROVIDER_RESPONSE")
            facts = reply.get("facts", reply)
            original_reply = reply.get("original_reply")
            if not isinstance(original_reply, str):
                original_reply = json.dumps(_json_safe(facts), ensure_ascii=False)
            if facts is None:
                facts = original_reply
            reported_usage = reply.get("usage")
            reported_usage = reported_usage if isinstance(reported_usage, dict) else {}
            usage = {name: value for name, value in reported_usage.items()
                     if isinstance(value, int) and not isinstance(value, bool) and value >= 0}
            response_details = {
                "original_reply": original_reply,
                "original_response": reply.get("original_response"),
                "http_status": reply.get("http_status"),
                "finish_reason": reply.get("finish_reason"),
                "response_id": reply.get("response_id"),
                "system_fingerprint": reply.get("system_fingerprint"),
                "model": reply.get("model"), "usage": usage,
            }
            result.update(response_details)
            attempt = {"outcome": "RESPONSE", **response_details}
            error_code = reply.get("error_code")
            if not error_code:
                result.update(status="EXTRACTED", facts=facts)
                result["attempts"].append(attempt)
                break
            if not isinstance(error_code, str) or not re.fullmatch(r"[A-Z0-9_]{1,80}", error_code):
                raise ExtractionError("INVALID_PROVIDER_ERROR")
            error = ExtractionError(error_code, reply.get("retryable") is True)
            delay = reply.get("retry_after_seconds")
            if (isinstance(delay, (int, float)) and not isinstance(delay, bool)
                    and math.isfinite(delay)):
                retry_after = min(15.0, max(1.0, delay))
        except (TimeoutError, ConnectionError, OSError):
            error = ExtractionError("EXTRACTOR_CONNECTION_FAILED", True)
        except ExtractionError as caught:
            error = caught
        except NotImplementedError:
            error = ExtractionError("EXTRACTOR_NOT_IMPLEMENTED")
        except Exception:
            error = ExtractionError("EXTRACTOR_FAILED")
        attempt = attempt or {}
        attempt["outcome"] = error.code
        result["attempts"].append(attempt)
        result["validation_codes"].append(error.code)
        if error.retryable and result["transport_retries"] < retry_limit:
            result["transport_retries"] += 1
            time.sleep(retry_after)
            continue
        result["status"] = "FAILED_RETRYABLE" if error.retryable else "FAILED_FINAL"
        break
    result["elapsed_seconds"] = round(time.monotonic() - started, 3)
    if cache is not None:
        cache[key] = {
            "cache_key": key, "extractor_version": version, "result": copy.deepcopy(result),
        }
    return result


def validate_facts(facts: dict | str, schema: dict, source: dict) -> dict:
    """Check structure and source excerpts without claiming completeness.

    Unknown and conflicting facts remain unresolved. Source contains the
    original fields, masked fields and spans returned by redaction.
    """
    from jsonschema import Draft202012Validator

    payload = _json_object(facts) if isinstance(facts, str) else copy.deepcopy(facts)
    if not isinstance(payload, dict):
        raise ExtractionError("INVALID_FACT_OBJECT")
    if next(Draft202012Validator(schema).iter_errors(payload), None) is not None:
        raise ExtractionError("FACT_SCHEMA_MISMATCH")
    kind = payload["kind"]
    if kind == "job" and any(payload["professional"].values()):
        raise ExtractionError("JOB_CONTAINS_CANDIDATE_FACTS")
    if kind != "job" and payload["requirements"]:
        raise ExtractionError("CANDIDATE_CONTAINS_JOB_REQUIREMENTS")
    for field in _fact_fields(payload):
        state, value, evidence = field["status"], field["value"], field["evidence"]
        if state == "UNKNOWN":
            if value is not None or evidence:
                raise ExtractionError("UNKNOWN_FIELD_HAS_VALUE")
            continue
        if state == "OBSERVED" and (value is None or not value.strip()):
            raise ExtractionError("OBSERVED_FIELD_HAS_NO_VALUE")
        if state == "CONFLICT" and (value is not None or len(evidence) < 2):
            raise ExtractionError("CONFLICT_WAS_RESOLVED")
        if not evidence:
            raise ExtractionError("FIELD_HAS_NO_EVIDENCE")
        if value is not None and not any(
            _excerpt_spans(item["excerpt"], value) for item in evidence
        ):
            raise ExtractionError("VALUE_IS_NOT_SOURCE_WORDING")
        for item in evidence:
            key, excerpt = item["source_field"], item["excerpt"]
            if key not in source["fields"]:
                raise ExtractionError("UNKNOWN_EVIDENCE_SOURCE")
            locations = [position for position in _excerpt_spans(source["fields"][key], excerpt)
                         if not any(position[0] < end and position[1] > start
                                    for start, end in source["spans"][key])]
            if not locations:
                raise ExtractionError("EVIDENCE_DOES_NOT_MATCH_SOURCE")
            start, end = locations[0]
            item["excerpt"] = source["original"][key][start:end]
        if state == "CONFLICT" and len({
            (item["source_field"], item["excerpt"]) for item in evidence
        }) < 2:
            raise ExtractionError("CONFLICT_HAS_NO_DISTINCT_EVIDENCE")
    for area, state in payload["field_status"].items():
        if kind == "job":
            items = [item for item in payload["requirements"]
                     if item["criterionType"] == area.upper()]
        else:
            items = payload["professional"][area]
        observations = [field for item in items for field in _fact_fields(item)]
        if any(not any(field["status"] != "UNKNOWN" for field in _fact_fields(item))
               for item in items):
            raise ExtractionError("EMPTY_ITEM_HAS_NO_OBSERVATION")
        expected = "OBSERVED" if items else "UNKNOWN"
        if any(field["status"] == "CONFLICT" for field in observations):
            expected = "CONFLICT"
        if state != expected:
            raise ExtractionError("AREA_STATUS_MISMATCH")
    return payload


def check_coverage(record: dict, fields: dict[str, str], facts: dict) -> dict:
    """Flag possible omissions separately from schema and evidence checks.

    Coverage findings require source comparison; they never justify
    inventing values or treating unmentioned qualifications as absent.
    """
    cues = {
        "skills": r"\b(?:skills|technologies|programming|frameworks)\b",
        "experience": r"\b(?:experience|employment|work history|years of)\b",
        "education": r"\b(?:education|degree|bachelor|master|doctorate|diploma)\b",
        "languages": r"\b(?:languages|english|french|fluent|proficiency|CEFR)\b",
        "projects": r"\b(?:projects|portfolio|implemented|built|developed)\b",
    }
    locations, issues = [], []
    for field in _fact_fields(facts):
        for evidence in field["evidence"]:
            key = evidence["source_field"]
            spans = _excerpt_spans(fields.get(key, ""), evidence["excerpt"])
            locations.append({**evidence, "locations": spans, "ambiguous": len(spans) > 1})
    for key, text in fields.items():
        covered = [span for item in locations if item["source_field"] == key
                   for span in item["locations"]]
        if key in {"Position", "Exp Years", "Experience Years", "English Level"}:
            if text.strip() and not covered:
                issues.append({"code": "UNREPRESENTED_SOURCE_FIELD", "source_field": key})
        for area, pattern in cues.items():
            for match in re.finditer(pattern, text, re.I):
                if not any(start <= match.start() and end >= match.end()
                           for start, end in covered):
                    issues.append({
                        "code": "POSSIBLE_OMISSION", "area": area, "source_field": key,
                        "start": match.start(), "end": match.end(),
                    })
    unknown = [key for key, value in facts["field_status"].items() if value == "UNKNOWN"]
    conflicting = [key for key, value in facts["field_status"].items() if value == "CONFLICT"]
    return {
        "status": "CHECK_NEEDED" if issues else "NO_AUTOMATIC_FLAGS", "issues": issues,
        "unknown_areas": unknown, "conflicting_areas": conflicting,
        "evidence_locations": locations, "completeness_confirmed": False,
    }


def save_results(
    records: Iterable[dict], responses: Iterable[dict], output_dir: Path,
) -> None:
    """Write two local JSONL files, retaining the previous completed batch.

    Consume records first, allowing their processing to populate responses.
    A pending record file survives interruption; replace completed files
    only after both writes finish. The directory must be under clean.
    """
    directory = _clean_directory(output_dir)
    record_path = directory / "records.pending.jsonl"
    response_path = directory / "responses.pending.jsonl"
    with record_path.open("w", encoding="utf-8", newline="\n") as stream:
        for record in records:
            stream.write(json.dumps(_json_safe(record), ensure_ascii=False, allow_nan=False))
            stream.write("\n")
            stream.flush()
    with response_path.open("w", encoding="utf-8", newline="\n") as stream:
        for response in responses:
            stream.write(json.dumps(_json_safe(response), ensure_ascii=False, allow_nan=False))
            stream.write("\n")
    response_path.replace(directory / "responses.jsonl")
    record_path.replace(directory / "records.jsonl")


def clean_record(
    record: dict,
    *,
    schema: dict,
    extractor: FactExtractor | None = None,
    extractor_version: str | None = None,
    cache: dict | None = None,
    allow_calls: bool = False,
) -> dict:
    """Prepare one record and optionally validate an extractor's proposal.

    Keep original source fields and names local. A validated proposal has
    matching evidence, not a guarantee of completeness or training use.
    Make at most one repair request and one transport retry in total.
    Missing facts, identity conflicts and privacy flags are not resolved.
    """
    result = {
        **record, "status": "PREPARED", "facts": None, "coverage": None,
        "validation_codes": [], "training_ready": False,
        "extraction": {
            "cache_keys": [], "new_calls": 0, "transport_retries": 0,
            "schema_repairs": 0, "reused_replies": 0, "elapsed_seconds": 0.0,
        },
    }
    metadata = result["extraction"]
    if record.get("deduplication", {}).get("status") == "ID_CONFLICT":
        result.update(status="HELD", validation_codes=["SOURCE_ID_CONFLICT"])
        return result
    try:
        fields = read_source_fields(record)
        result["source_fields"] = fields
        if not fields:
            raise ExtractionError("EMPTY_SOURCE_TEXT")
        redaction = redact_fields(fields, record.get("identifiers", ()))
        result["redaction"] = redaction
        if allow_calls and redaction["issues"]:
            result.update(status="HELD", validation_codes=["PRIVACY_CHECK_NEEDED"])
            return result
        if not any(value.strip() for value in redaction["fields"].values()):
            raise ExtractionError("EMPTY_MASKED_TEXT")
        request = {
            "kind": record["kind"], "fields": redaction["fields"], "schema": schema,
            "extractor_version": extractor_version, "repair_codes": [],
        }
        for repair in range(2):
            proposal = extract_facts(
                request, extractor, cache=cache, allow_calls=allow_calls,
                retry_limit=1 - metadata["transport_retries"],
            )
            metadata["cache_keys"].append(proposal["cache_key"])
            metadata["new_calls"] += proposal["new_calls"]
            metadata["transport_retries"] += proposal["transport_retries"]
            metadata["reused_replies"] += int(proposal["reused"])
            if proposal["new_calls"]:
                metadata["elapsed_seconds"] += proposal["elapsed_seconds"]
            if proposal["status"] != "EXTRACTED":
                if repair and not allow_calls and proposal["status"] == "PREPARED":
                    result["status"] = "FAILED_FINAL"
                    return result
                result.update(status=proposal["status"],
                              validation_codes=proposal["validation_codes"])
                return result
            try:
                facts = validate_facts(proposal["facts"], schema, redaction)
                if facts["kind"] != record["kind"]:
                    raise ExtractionError("FACT_KIND_MISMATCH")
            except ExtractionError as error:
                result["validation_codes"] = [error.code]
                if repair:
                    result["status"] = "FAILED_FINAL"
                    return result
                request = {**request, "repair_codes": [error.code]}
                if allow_calls:
                    metadata["schema_repairs"] += 1
                continue
            result.update(
                status="VALIDATED", facts=facts, validation_codes=[],
                coverage=check_coverage(record, redaction["fields"], facts),
            )
            return result
    except ExtractionError as error:
        result.update(status="HELD", validation_codes=[error.code])
    except (KeyError, TypeError, ValueError):
        result.update(status="HELD", validation_codes=["INVALID_SOURCE_RECORD"])
    return result


def clean_records(
    records: Iterable[dict],
    *,
    schema: dict,
    extractor: FactExtractor | None = None,
    extractor_version: str | None = None,
    output_dir: Path,
    cache: dict | None = None,
    allow_calls: bool = False,
    limit: int | None = None,
) -> dict:
    """Process a finite batch and return counts without exposing its text.

    Reuse versioned responses and recheck them against current evidence.
    Save new replies after each record so restarting can reuse them.
    Rebuild records from the current input; do not retain stale selections.
    With calls disabled, prepare text without generating replacement facts.
    """
    if limit is not None and (isinstance(limit, bool) or not isinstance(limit, int) or limit < 0):
        raise ValueError("Record limit must be a nonnegative integer or None.")
    if allow_calls:
        if not callable(extractor) or not isinstance(extractor_version, str):
            raise ExtractionError("EXTRACTOR_NOT_CONFIGURED")
        if not extractor_version.strip():
            raise ExtractionError("EXTRACTOR_NOT_CONFIGURED")
    from jsonschema import Draft202012Validator

    Draft202012Validator.check_schema(schema)
    directory = _clean_directory(output_dir)
    response_file = directory / "responses.jsonl"
    responses = _load_responses(response_file)
    if cache is not None:
        responses.update(cache)
    summary = {
        "processed": 0, "statuses": {}, "new_calls": 0, "reused_replies": 0,
        "transport_retries": 0, "schema_repairs": 0, "coverage_flags": 0,
        "privacy_flags": 0, "elapsed_seconds": 0.0, "training_ready": 0,
        "output_dir": str(directory),
    }
    started = time.monotonic()

    def processed_records() -> Iterator[dict]:
        selected = islice(records, limit) if limit is not None else iter(records)
        with response_file.open("a", encoding="utf-8", newline="\n") as stream:
            for record in selected:
                cleaned = clean_record(
                    record, schema=schema, extractor=extractor,
                    extractor_version=extractor_version, cache=responses, allow_calls=allow_calls,
                )
                metadata = cleaned["extraction"]
                if metadata["new_calls"]:
                    for key in dict.fromkeys(metadata["cache_keys"]):
                        if key in responses:
                            stream.write(json.dumps(_json_safe(responses[key]), ensure_ascii=False,
                                                    allow_nan=False) + "\n")
                    stream.flush()
                summary["processed"] += 1
                status = cleaned["status"]
                summary["statuses"][status] = summary["statuses"].get(status, 0) + 1
                for key in ("new_calls", "reused_replies", "transport_retries", "schema_repairs"):
                    summary[key] += metadata[key]
                summary["coverage_flags"] += len((cleaned.get("coverage") or {}).get("issues", []))
                summary["privacy_flags"] += len((cleaned.get("redaction") or {}).get("issues", []))
                yield cleaned

    try:
        save_results(processed_records(), responses.values(), directory)
    finally:
        if cache is not None:
            cache.update(responses)
    summary["elapsed_seconds"] = round(time.monotonic() - started, 3)
    return summary


def _excerpt_spans(text: str, excerpt: str) -> list[tuple[int, int]]:
    """Match literal wording with whitespace differences only.

    Token edges keep Java distinct from JavaScript and C from C++.
    Returned offsets always belong to text, never the provider's excerpt.
    """
    words = re.split(r"\s+", excerpt.strip())
    if not excerpt.strip():
        return []
    pattern = r"\s+".join(re.escape(word) for word in words)
    if re.match(r"[\w+#.]", excerpt.strip()[0]):
        pattern = r"(?<![\w+#.])" + pattern
    if re.match(r"[\w+#]", excerpt.strip()[-1]):
        pattern += r"(?![\w+#]|\.\w)"
    return [match.span() for match in re.finditer(pattern, text)]


def _fact_fields(value) -> Iterator[dict]:
    if isinstance(value, dict):
        if set(value) == {"value", "status", "evidence"}:
            yield value
        else:
            for child in value.values():
                yield from _fact_fields(child)
    elif isinstance(value, list):
        for child in value:
            yield from _fact_fields(child)


def _json_object(text: str) -> dict:
    """Reject duplicate keys, nonfinite numbers and nonobject replies."""
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ExtractionError("DUPLICATE_JSON_KEY")
            result[key] = value
        return result

    def invalid_constant(value):
        raise ExtractionError("NONFINITE_JSON_VALUE")

    def finite_number(value):
        number = float(value)
        if not math.isfinite(number):
            raise ExtractionError("NONFINITE_JSON_VALUE")
        return number

    try:
        value = json.loads(
            text, object_pairs_hook=unique_keys, parse_constant=invalid_constant,
            parse_float=finite_number,
        )
    except ExtractionError:
        raise
    except ValueError as error:
        raise ExtractionError("INVALID_JSON_RESPONSE") from error
    if not isinstance(value, dict):
        raise ExtractionError("INVALID_FACT_OBJECT")
    return value


def _json_safe(value):
    """Mark nonfinite source values explicitly instead of inventing nulls."""
    if isinstance(value, float) and not math.isfinite(value):
        return {"nonfinite_float": str(value)}
    if isinstance(value, dict):
        return {key: _json_safe(child) for key, child in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(child) for child in value]
    return value


def _clean_directory(output_dir: Path) -> Path:
    """Resolve the write boundary so a symlink cannot redirect private data."""
    root = Path(__file__).resolve().parents[4] / "datasets/clean"
    directory = output_dir.resolve()
    if not directory.is_relative_to(root):
        raise ValueError("Extraction output must stay inside offline-ml/datasets/clean.")
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def _load_responses(path: Path) -> dict:
    """Read the local cache and discard only an interrupted final write."""
    cache = {}
    if not path.exists():
        return cache
    completed = 0
    needs_newline = False
    with path.open("rb") as stream:
        for line in stream:
            try:
                entry = _json_object(line.decode("utf-8"))
            except (ExtractionError, UnicodeDecodeError) as error:
                truncated = isinstance(error, UnicodeDecodeError) or (
                    error.code == "INVALID_JSON_RESPONSE"
                )
                if not line.endswith(b"\n") and truncated:
                    break
                raise
            if (not isinstance(entry.get("cache_key"), str)
                    or not isinstance(entry.get("result"), dict)):
                raise ExtractionError("INVALID_RESPONSE_CACHE")
            cache[entry["cache_key"]] = entry
            completed += len(line)
            needs_newline = not line.endswith(b"\n")
    if completed != path.stat().st_size:
        with path.open("r+b") as stream:
            stream.truncate(completed)
    elif needs_newline:
        with path.open("ab") as stream:
            stream.write(b"\n")
    return cache
