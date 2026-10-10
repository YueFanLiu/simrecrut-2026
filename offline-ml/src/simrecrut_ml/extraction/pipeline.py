"""Apply bounded retries and evidence validation independently of vendors."""

import hashlib
import json
import time
from dataclasses import replace
from typing import Any

from .models import ExtractionError, ExtractionRequest, FactExtractionProvider
from .redaction import REDACTION_VERSION, RedactedText
from .validation import supported_partial_reply, validate_reply


def extraction_key(
    provider: FactExtractionProvider, request: ExtractionRequest,
) -> str:
    """Hash redacted fields, schema and implementation for local reuse."""
    encoded = json.dumps({
        "provider": provider.version, "redaction": REDACTION_VERSION,
        "schema": request.schema, "kind": request.kind, "fields": request.fields,
    }, sort_keys=True, ensure_ascii=False, allow_nan=False).encode()
    return hashlib.sha256(encoded).hexdigest()


def extract_validated(
    provider: FactExtractionProvider, request: ExtractionRequest, text: RedactedText,
) -> dict[str, Any]:
    """Produce a local draft after at most one retry and one repair.

    The shared retry budget spans both the initial and repair call.
    Preserve rejected replies locally for later diagnosis; return only
    safe stage codes in metadata. A validated draft remains unconfirmed.
    Failure never creates an empty approved profile or training sample.
    """
    started = time.monotonic()
    attempts, responses, errors = [], [], []
    retries, repairs = 0, 0
    current = request
    while True:
        try:
            reply = provider.extract(current)
            attempts.append({"outcome": "RESPONSE", "model": reply.model, "usage": reply.usage})
            responses.append(reply.content)
        except ExtractionError as error:
            attempts.append({"outcome": error.code})
            errors.append(error.code)
            if error.retryable and retries == 0:
                retries += 1
                continue
            return _result(
                "FAILED_RETRYABLE" if error.retryable else "FAILED_FINAL",
                None, attempts, responses, errors, retries, repairs, started,
            )
        try:
            facts = validate_reply(reply.content, current.schema, current.kind, text)
        except ExtractionError as error:
            errors.append(error.code)
            if repairs == 0:
                repairs += 1
                current = replace(request, repair_codes=(error.code,))
                continue
            failure = _result(
                "FAILED_FINAL", None, attempts, responses, errors, retries, repairs, started,
            )
            partial, held = supported_partial_reply(reply.content, current.schema,
                                                    current.kind, text)
            failure.update({"supported_partial_facts": partial, "held_fields": held})
            return failure
        return _result(
            "REVIEW_REQUIRED", facts, attempts, responses, errors, retries, repairs, started,
        )


def _result(status, facts, attempts, responses, errors, retries, repairs, started):
    return {
        "status": status, "facts": facts, "attempts": attempts,
        "provider_responses": responses, "validation_codes": errors,
        "transport_retries": retries, "schema_repairs": repairs,
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "human_confirmed": False, "training_ready": False,
    }
