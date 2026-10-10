"""Check privacy, retry limits and replacement without external API calls."""

import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from simrecrut_ml.extraction.config import DeepSeekSettings, load_settings
from simrecrut_ml.extraction.deepseek import request_body
from simrecrut_ml.extraction.models import ExtractionError, ExtractionRequest, ProviderReply
from simrecrut_ml.extraction.pipeline import extract_validated, extraction_key
from simrecrut_ml.extraction.redaction import redact_fields
from simrecrut_ml.extraction.text import read_pdf

from test_validation import blank


class LocalProvider:
    """Exercise the shared interface using local fixture replies only."""

    version = "local-fixture-v1"

    def __init__(self, replies):
        self.replies = iter(replies)
        self.requests = []

    def extract(self, prepared_request):
        """Record boundary inputs and return the next controlled reply."""
        self.requests.append(prepared_request)
        value = next(self.replies)
        if isinstance(value, ExtractionError):
            raise value
        return ProviderReply(value, "local-fixture", {})


@pytest.fixture
def prepared_request():
    root = Path(__file__).resolve().parents[3]
    schema = json.loads((root / "contracts/fact-schemas/extraction-v1.json").read_text())
    text = redact_fields({"pdf_page_1": "Skills C++\nEmail: private@example.test"})
    return ExtractionRequest("resume", text.fields, schema), text


def test_local_provider_uses_the_same_validation_and_remains_unconfirmed(prepared_request):
    task, text = prepared_request
    provider = LocalProvider([json.dumps(blank())])
    result = extract_validated(provider, task, text)
    assert result["status"] == "REVIEW_REQUIRED"
    assert not result["human_confirmed"] and not result["training_ready"]
    assert "private@example.test" not in json.dumps(provider.requests[0].fields)


def test_one_shared_transport_retry_and_one_repair(prepared_request):
    task, text = prepared_request
    provider = LocalProvider([
        ExtractionError("PROVIDER_TIMEOUT", True), "not json", json.dumps(blank()),
    ])
    result = extract_validated(provider, task, text)
    assert result["status"] == "REVIEW_REQUIRED"
    assert result["transport_retries"] == 1 and result["schema_repairs"] == 1
    assert len(provider.requests) == 3
    assert provider.requests[-1].repair_codes == ("INVALID_JSON",)


def test_retry_budget_is_not_reset_for_repair(prepared_request):
    task, text = prepared_request
    provider = LocalProvider([
        ExtractionError("PROVIDER_TIMEOUT", True), "not json",
        ExtractionError("PROVIDER_TIMEOUT", True),
    ])
    result = extract_validated(provider, task, text)
    assert result["status"] == "FAILED_RETRYABLE" and result["facts"] is None
    assert len(provider.requests) == 3


def test_second_invalid_response_fails_without_inventing_empty_facts(prepared_request):
    task, text = prepared_request
    result = extract_validated(LocalProvider(["bad", "still bad"]), task, text)
    assert result["status"] == "FAILED_FINAL" and result["facts"] is None


def test_permanent_auth_error_is_not_retried(prepared_request):
    task, text = prepared_request
    provider = LocalProvider([ExtractionError("DEEPSEEK_HTTP_401")])
    result = extract_validated(provider, task, text)
    assert len(provider.requests) == 1 and result["transport_retries"] == 0


def test_contacts_known_names_and_offsets_are_preserved_locally():
    source = "Name: Alex Example\nAlex Example uses C++\n+44 7777 111222\nhttps://example.test/me"
    text = redact_fields({"pdf_page_1": source})
    assert text.original["pdf_page_1"] == source
    assert len(text.fields["pdf_page_1"]) == len(source)
    assert "Alex Example" not in text.fields["pdf_page_1"]
    assert "7777" not in text.fields["pdf_page_1"]
    assert "https://" not in text.fields["pdf_page_1"]
    assert "C++" in text.fields["pdf_page_1"]


@pytest.mark.parametrize("header", ["Alex Example", "ALEX EXAMPLE", "Alex A. Example",
                                    "Alex Example - Engineer"])
def test_suspicious_personal_header_requires_privacy_review(header):
    with pytest.raises(ExtractionError, match="PRIVACY_REVIEW_REQUIRED"):
        redact_fields({"pdf_page_1": header + "\nSkills C++"})


def test_settings_do_not_load_other_service_secrets_or_echo_key(tmp_path, monkeypatch):
    path = tmp_path / ".env"
    path.write_text('DEEPSEEK_API_KEY="fixture-token"\nDB_PASSWORD=other-secret\n')
    monkeypatch.delenv("DB_PASSWORD", raising=False)
    settings = load_settings(path)
    assert settings.api_key == "fixture-token" and settings.connect_timeout == 5
    assert settings.read_timeout == 20 and "fixture-token" not in repr(settings)
    path.write_text("DEEPSEEK_API_KEY=fixture-token\nDEEPSEEK_BASE_URL=http://localhost\n")
    with pytest.raises(ExtractionError):
        load_settings(path)


def test_request_is_json_only_and_cache_excludes_credentials(prepared_request):
    task, text = prepared_request
    settings = DeepSeekSettings("fixture-token")
    body = request_body(task, settings)
    assert body["response_format"] == {"type": "json_object"}
    assert body["thinking"] == {"type": "disabled"}
    assert "fixture-token" not in json.dumps(body)
    assert "private@example.test" not in json.dumps(body)
    assert len(extraction_key(LocalProvider([]), task)) == 64


def test_ocr_called_only_for_unusable_page(monkeypatch, tmp_path):
    import pypdf

    pages = [SimpleNamespace(extract_text=lambda: "Native software skills C++"),
             SimpleNamespace(extract_text=lambda: "")]
    monkeypatch.setattr(pypdf, "PdfReader", lambda path: SimpleNamespace(
        pages=pages, is_encrypted=False,
    ))
    calls = []

    class Ocr:
        """Return local text for the controlled image page."""

        def read_page(self, path, page_number):
            """Record which original page needs OCR."""
            calls.append(page_number)
            return "Scanned software skills Python"

    result = read_pdf(tmp_path / "fixture.pdf", Ocr())
    assert calls == [2] and result.methods == ("EMBEDDED", "OCR")
    with pytest.raises(ExtractionError, match="OCR_REQUIRED"):
        read_pdf(tmp_path / "fixture.pdf")
