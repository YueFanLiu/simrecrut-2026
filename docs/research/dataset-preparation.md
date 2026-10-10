# Dataset preparation

The offline tools download the three sources named in the Lark specification,
filter their published categories, remove exact duplicates, and prepare records
for human review. They do not turn source text into approved facts or training
labels. MouZheng LI owns this data preparation and model training work.

## Source of the requirements

Use the [Lark specification](https://ijppwkmdd5yg.jp.larksuite.com/docx/UGOYdlnb5or8prxDdtrjKzyvpwd)
and its [original Markdown snapshot](lark-specification-source.md). The snapshot
was fetched from Lark on 2026-10-10 at revision **221**, without rewriting the
body. Its SHA-256 is
`7e701754a2c3c39bda61d28a3ea80949d30c63b38dda60997ea0c7e2879c2ae8`.
Embedded whiteboards remain source references in that text snapshot.

Before changing cleaning, training, or evaluation, read the relevant source
sections again and check for a newer revision. A configured Lark user profile
can retrieve the full source with:

```powershell
lark-cli --profile simrecut-lark docs +fetch --doc 'https://ijppwkmdd5yg.jp.larksuite.com/docx/UGOYdlnb5or8prxDdtrjKzyvpwd' --as user --detail full --doc-format markdown
```

This command returns a JSON response containing the Markdown body. Preserve
that body as the source snapshot and update the revision, date, and hash here.
The maintained instructions below follow its data-source and training sections.
Follow the repository's [coding standards](../development/coding-standards.md),
especially its rules for English comments and public Python contracts.

## Local files

| Source | Raw directory | Download verified on 2026-10-10 |
| --- | --- | --- |
| [Djinni English jobs](https://huggingface.co/datasets/lang-uk/recruitment-dataset-job-descriptions-english) | `offline-ml/datasets/raw/djinni-jobs/` | 141,897 rows; revision `b56a6054c10f1266a141e37c8df66c79ff2863af`; declared MIT. |
| [Djinni English profiles](https://huggingface.co/datasets/lang-uk/recruitment-dataset-candidate-profiles-english) | `offline-ml/datasets/raw/djinni-profiles/` | 210,250 rows; revision `86255174c6c378a2b52cd7e81d79002c64d899a6`; declared MIT. |
| [Resume Dataset](https://www.kaggle.com/datasets/snehaanbhawal/resume-dataset) | `offline-ml/datasets/raw/resume-dataset/` | Version 1; 2,484 CSV rows and 2,484 PDFs; declared CC0. |

The initial raw download and extracted files occupy about 541 MiB. These are
source counts, not reviewed training counts. Djinni has no linked individual
hiring outcomes or original CV PDFs. Resume Dataset supplies example PDFs,
not verified hiring decisions or demographic ground truth.

Acquisition manifests live under `offline-ml/datasets/manifests/`. They record
URLs, declared licences, revisions, retrieval times, publisher IDs and file
sizes/hashes. Djinni hashes are compared with publisher hashes. Kaggle does
not publish an archive hash through this API; the saved hash records the bytes
downloaded. Repeated downloads reuse files only when their source receipt and
integrity checks agree, and resume interrupted transfers when supported.

Raw files, extracted PDFs, manifests, review files, cleaned records, and model
outputs stay local and are ignored by Git. Only small `data-notes.md` files and
directory markers are allowed inside dataset folders. Keep those notes short;
put procedures in this guide. Do not force-add research payloads.

## Set up and run

Run commands from the repository root with Python 3.12 or newer. Create a local
environment and install the package and test dependencies:

```powershell
python -m venv offline-ml/.venv
./offline-ml/.venv/Scripts/python.exe -m pip install -e './offline-ml[dev]'
```

Download all three sources:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml download --datasets-root offline-ml/datasets
```

Prepare a new local run:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml prepare --manifest offline-ml/datasets/manifests/acquisition-manifest.json --selection offline-ml/configs/data-selection.json --output offline-ml/datasets/clean/initial-preparation
```

Use a new output directory when repeating preparation. Existing derived files
are not overwritten. The [selection file](../../offline-ml/configs/data-selection.json)
uses exact publisher categories, keeping technical Djinni roles and Resume
Dataset's INFORMATION-TECHNOLOGY category. Ambiguous categories such as Lead,
Other, Support, and general management are excluded in this first pass.
Its families are provisional filters; they do not approve pair compatibility.
Change the selection version when changing this filter.

The first full run on 2026-10-10 retained 97,762 jobs, 134,815 Djinni profiles,
and 120 Resume Dataset CVs. It found no exact normalized duplicates or repeated
source-ID conflicts within the selected records. These remain unreviewed
drafts. The exported pilot contains 20 original CV PDFs and 20 jobs, all pending.

Preparation verifies every source hash before reading rows, then writes:

| File | Purpose |
| --- | --- |
| `prepared-records.jsonl` | Stable record/source IDs, raw and normalized observations, evidence, PDF references, and unconfirmed field states. |
| `duplicate-records.jsonl` | Every removed record's original locator and its retained representative. |
| `excluded-records.jsonl` | Source IDs and reasons for category exclusions. |
| `issues.jsonl` | Conflicting publisher identities; both affected records are blocked from review approval. |
| `preparation-manifest.json` | Filter/mapping versions, source and output hashes, per-source counts, and review status. |

Whitespace and Unicode normalization preserve case, punctuation, accents, and
skill spellings such as C++, C#, and .NET. Deduplication compares normalized
evidence. Blank or title-only profiles keep separate source identities. All
duplicate origins remain traceable through the duplicate file. Publisher
experience and English levels remain observations, not accepted relevant years
or language equivalences. No names, photographs, schools, or text are used to
guess gender or ethnicity. Names present in the originals remain in local raw
evidence and are excluded from professional features and console output.

## Review the first 20 CVs and 20 jobs

Export the pilot batch after preparation:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml export-review --clean-dir offline-ml/datasets/clean/initial-preparation --output offline-ml/datasets/clean/initial-preparation/pilot-review.jsonl
```

The deterministic batch prefers Resume Dataset PDFs for the 20 CVs. It fills
any shortfall with Djinni profiles and reports the actual counts. Selection is
for field checking, not a representative estimate of hiring outcomes.

1. Open each source CV or job alongside its local draft. For PDFs, record
   whether embedded text is readable and whether OCR is needed; do not assume
   every PDF needs OCR.
2. Check skills, experience, education, languages, and projects. Record
   confirmed items, confirmed absence, unknown values, or conflicts separately.
   A company technology list is not automatically a job requirement. For jobs,
   record inclusion, mandatory checks, and public visibility as separate flags.
3. Record corrections and the reason. Confirmed items need evidence spans
   referencing a source field and its zero-based character range `[start, end)`.
   Fill in reviewer, timezone-aware review time, and notes. PDF inspection is
   recorded separately from field approval. Keep names, contact details, and
   copied CV paragraphs out of review notes; use field references and reasons.
4. Review and version skill aliases, degree equivalences, language levels, and
   family compatibility before feature generation. Do not invent a CEFR
   conversion from publisher labels or infer missing qualifications.

The exporter leaves every record at `REVIEW_REQUIRED`, with blank professional
items. The importer accepts `APPROVED`, `NEEDS_CORRECTION`, `REJECTED`, or pending
reviews. Approval requires the exact draft fingerprint and an approved mapping
version. Unknown values stay unknown; unresolved conflicts block approval.
Later assessment preparation must resolve facts required by its selected job.

No approved mapping is supplied by default. A mapping JSON requires `version`,
`status: APPROVED`, `reviewer`, and `reviewed_at`. Its explicit inventories are
`skill_aliases`, `degree_levels`, `degree_order`, `language_levels`,
`language_level_order`, `language_codes`, `subject_codes`, `job_family_codes`,
and `project_condition_codes`. Family compatibility needs a separate reviewed
table when candidate-job pairing is implemented; a list of family codes alone
does not establish compatibility. Put local mapping drafts and approval records
under the ignored clean directory until reviewed.

After actual human review, import the completed batch:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml apply-review --clean-dir offline-ml/datasets/clean/initial-preparation --reviews offline-ml/datasets/clean/initial-preparation/pilot-review.jsonl --mappings offline-ml/datasets/clean/reviewed-mappings.json --output offline-ml/datasets/clean/initial-preparation/reviewed-records.jsonl
```

The importer saves approved records and a separate decisions file. Approval is
not a completed training corpus. Preserve the preparation files and review
batch for provenance and correction history. The pilot review remains a human
task; neither downloading nor passing tests completes it.

## Structured extraction before review

The offline [extraction modules](../../offline-ml/src/simrecrut_ml/extraction/)
follow the source's Candidate journey step 6 and extraction gateway contract.
They produce proposals for review; they do not confirm facts or build model
labels. The live Java extraction endpoint is a separate implementation task.

Install the optional dependencies in the same local environment:

```powershell
./offline-ml/.venv/Scripts/python.exe -m pip install -e './offline-ml[dev,extraction]'
```

Set the extraction variables in the root `.env` using the names in
[`.env.example`](../../.env.example). Keep the real key local. Only redacted
professional text, the schema and extraction instructions reach DeepSeek;
PDF bytes, publisher IDs, file paths and original names are not sent.

Before running the audited batch, review the selected originals locally and
save `verification/extraction-privacy-review.json` inside the pilot audit.
Each record needs `PRIVACY_CHECKED`, its record ID, SHA-256 for every exact
input text field, and explicit identifier literals/spans. Keep ambiguous
privacy cases at `HOLD`. The gate rejects missing reviews or changed text.
This privacy check does not constitute human qualification approval.

Run from the repository root:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml extract-facts --manifest offline-ml/datasets/manifests/acquisition-manifest.json --audit-dir offline-ml/datasets/clean/pilot-audit --schema contracts/fact-schemas/extraction-v1.json --output offline-ml/datasets/clean/pilot-audit/extraction --env-file .env
```

Use `--limit 1` for a probe. `--privacy-review` selects an explicit local privacy
receipt instead of the default file. A repeated command revalidates successful
cached responses against the current source and preserves failed attempts.
Permanent key, balance or endpoint errors stop the batch. Progress contains
aggregate stages only; vendor error bodies and source text never enter logs.

| Component | Responsibility |
| --- | --- |
| `text.py` | Read original embedded PDF text page by page. Use an injected local OCR adapter only for unusable pages; hold the record if OCR is required but unavailable. |
| `redaction.py` | Mask patterns plus reviewed identifiers/spans while retaining original offsets and local originals. |
| `models.py` | Define the replaceable `FactExtractionProvider` boundary. A rule algorithm receives the same redacted input and passes the same validators. |
| `deepseek.py`, `config.py` | Load only extraction settings and call official HTTPS JSON mode. Keep the key out of representation, payloads and logs. |
| `validation.py` | Check strict fields/types and attach evidence to original locations. Align whitespace only; do not repair case, spelling or punctuation. |
| `pipeline.py`, `batch.py` | Allow one shared transport retry and one structured-output repair; save source-specific drafts, failure receipts and content-only response caches. |
| `recheck.py`, `audit.py` | Revalidate saved replies without API calls, then compare evidence with the source audit in the same field and location. |
| `triage.py` | Bind source-check proposals to the current originals, run, privacy review and draft hashes. Require every coverage gap and held field exactly once; validate source quotes without granting approval. |

The [fact schema](../../contracts/fact-schemas/extraction-v1.json) separates
candidate facts from job requirements. Observed wording is backed by short
source excerpts; unknown values stay null. Conflicts retain both excerpts.
No mention does not prove absence. Code slots, normalized months, current
flags and reviewed durations stay unset until supported review. Targets,
mandatory checks and HR flags also stay unset in provider output. The accepted
owner policy supplies initial target proposals separately, without changing
what the employer originally wrote. This draft contract precedes the final
reviewed professional schema; it is not the HTTP confirmation payload.

DeepSeek's [JSON-mode guide](https://api-docs.deepseek.com/guides/json_mode/)
requires a JSON response format and instructions. The adapter uses the
[chat-completion API](https://api-docs.deepseek.com/api/create-chat-completion/),
with thinking disabled, a 5-second connection limit and a 20-second socket
read limit. These are inactivity limits, not a guaranteed total duration.
The configured model, prompt/schema versions, attempts, token counts and
elapsed time are recorded locally. API documentation was checked on
2026-10-10. Schema validation remains the backend's responsibility.

The provider interface, disabled thinking, bounded retries and successful
response cache allow later comparison with a simpler algorithm. Token and
time measurements are workload evidence; they do not measure carbon emissions.
Every saved draft remains `REVIEW_REQUIRED` or a recorded failure, with zero
human approvals and zero training samples. Compare accepted excerpts with the
existing source audit before completing the separate `apply-review` contract.

Use the run receipt's filename in the following check:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml audit-extraction --audit-dir offline-ml/datasets/clean/pilot-audit --run offline-ml/datasets/clean/pilot-audit/extraction/runs/RUN_ID.json --output offline-ml/datasets/clean/pilot-audit/verification/extraction-coverage.json
```

The comparison requires the same source field and an enclosing original range.
CV audit wording must first be located on its previously checked PDF pages.
It retains every unrepresented excerpt, unlocated PDF wording, ambiguous
evidence location and existing source issue for later review. Evidence representation
does not establish extraction completeness or correct requirement grouping.

After a validator correction, recheck the saved responses locally before deciding
whether another API call is necessary:

```powershell
./offline-ml/.venv/Scripts/python.exe -m simrecrut_ml recheck-extraction --manifest offline-ml/datasets/manifests/acquisition-manifest.json --audit-dir offline-ml/datasets/clean/pilot-audit --run offline-ml/datasets/clean/pilot-audit/extraction/runs/RUN_ID.json --schema contracts/fact-schemas/extraction-v1.json
```

This command verifies current originals and privacy bindings, then saves a new
derived receipt with zero provider calls. Earlier failures remain unchanged.
For a structurally valid failed response, independently supported proposals
can be retained in `supported_partial_facts`; unsupported fields become unknown
or leave the item outside the partial draft. `held_fields` records their paths
and reasons. The full failed response remains available locally. Such records
retain a failure status and cannot enter training, even when part of their content
has valid evidence. Use the new receipt when running `audit-extraction`.

### Current pilot result

The first batch processed the 20 accepted pilot CVs and all 20 jobs on
2026-10-10. Local revalidation made no additional API calls: 26 records have
full validated, unconfirmed drafts; 14 failed records retain independently
supported partial facts. Their failure states remain 13 `FAILED_FINAL` and
one `FAILED_RETRYABLE`, with 23 held fields across those records.
Full validation means the draft structure and cited evidence passed checks;
it does not mean all qualifications were extracted or correctly classified.

The initial comparison represents 530 of 650 unique audited source spans.
The other 120 were coverage warnings, not confirmed extraction errors. All
120 spans and 23 held fields have now been checked against the source. The
143 cases comprise 51 literal omissions, 26 location/header/encoding differences,
16 conflicts or unknown interpretations, nine cases represented through other
excerpts, and 41 context items outside assessed professional facts.
Job titles and publisher categories remain available as source metadata for
later role pairing; exclusion from a criterion does not delete that metadata.

The omissions have exact source-backed supplementary proposals. Four CV 11
date spans differ between CSV separators and PDF text encoding; those dates
were already extracted from the PDF. Publisher English Level headers remain
metadata, not invented body quotes or CEFR conversions. Uncertain dates,
degree completion, language equivalence and ambiguous job requirements stay
unknown or held under accepted policies 1-5. No new owner choice is required
for this source-check step, and no additional API calls were made.

The local `pilot-audit/mapping-review.md` owns the per-record table and triage
summary. `verification/extraction-coverage.json` preserves the original warning
counts, while `verification/extraction-triage.json` binds the classifications
and quotes to current source/run/privacy/draft hashes. Source-check proposals
are not approved facts or reviewed mappings. Human approvals and training
rows remain zero.

## Continue to PyTorch preparation

The [pilot review and training guide](pilot-review-and-training.md) owns source-audit
results, pending decisions, feature encoding, training controls and the remaining
calibration/evaluation protocol. Use this guide for acquisition, preparation and
review import. Do not duplicate the training protocol here.

## Verify a change

```powershell
./offline-ml/.venv/Scripts/python.exe -m pytest offline-ml/tests scripts/maintenance/test_check_repository.py
./offline-ml/.venv/Scripts/python.exe scripts/maintenance/check_repository.py
git status --short
```

Tests cover transfer integrity, safe extraction, duplicate provenance, missing
and conflicting values, review validation, numeric features, grouped splitting,
synthetic training controls, readiness and the Git documentation exception.
Install the `dev,training,extraction` extras to run the full suite.
Read the comments and guide as part of review; a text scan cannot judge whether
an explanation is clear or technically correct. `git status` must show scripts,
configuration, and notes rather than raw or cleaned dataset payloads.

If Windows denies access to pytest's shared temporary directory, pass
`--basetemp offline-ml/runs/check-YYYYMMDD-NN` with a new, unused name. Pytest
can remove an existing base directory, so verify that the chosen path is inside
`offline-ml/runs/` before running it.
