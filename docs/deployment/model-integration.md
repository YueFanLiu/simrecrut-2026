# Local model integration

This setup connects the selected Keras models to the existing Java case workflow
and connected frontend. It uses an isolated local MySQL database, Redis and
Mailpit. It does not connect to Aiven, train models or evaluate the sealed test set.
The results are controlled research simulations, not hiring recommendations or
calibrated probabilities.

## Selected packages

The integration stack downloads the selected packages on first startup using
`MODEL_BUNDLE_TOKEN`, a fine-grained Hugging Face token with read access to the
[private model repository](https://huggingface.co/petitlang/simrecrut-model-artifacts).
The download uses a pinned revision and SHA-256 digest. The setup service
verifies each package before installing it and reuses verified local copies on
later starts. The download token is available only to the setup service.

If you have the original training run locally, you can also export the packages
from the repository root:

```powershell
python offline-ml/src/simrecrut_ml/packaging/scripts/export_models.py
```

The operation selects Neutral seed 45 and Biased seed 46 from the completed
`20261117T140541Z-bd3e28` experiment. It verifies source hashes and copies the
weights, tokenizer, preprocessing, thresholds and validation evidence into
`model_artifacts/neutral/neutral-20261117-s45-v1/` and
`model_artifacts/biased/biased-20261117-s46-v1/`. Existing versions are immutable.
The Biased package also contains the frozen VGGFace2 ResNet50 backbone. The
fastText embedding is already inside each Keras model; the original word-vector
download is not needed by the server.

Installed package contents are ignored by Git. The source experiment, datasets
and `.env` stay local.

## Start the isolated stack

Place the team-provided `.env` at the repository root. For a new configuration,
use the [environment example](../../.env.example) and supply the database, JWT,
email-code, internal inference and model download credentials. A verified
local copy of both packages can start with an empty `MODEL_BUNDLE_TOKEN`.
Start Docker Desktop, then run this command from the repository root.

```powershell
docker compose --env-file .env -f deploy/compose.integration.yaml up --build -d
```

The first start downloads about 89 MiB before inference starts. If model setup
fails, run `docker compose --env-file .env -f deploy/compose.integration.yaml logs model-bundle`.
Check the token's read access for a download error. For a checksum error on an
installed version, move that version directory out of `model_artifacts` and
start the stack again.

Open `http://127.0.0.1:8088`. Mailpit is at `http://127.0.0.1:18025`; local test
registration emails stay inside this stack. All host bindings use loopback.
The inference service receives only its internal token and a read-only package
mount. Add `DEEPSEEK_API_KEY` to the ignored root `.env` to enable DeepSeek
for candidate simulation uploads. HR requirements are entered and reviewed
directly in this local stack. Set `CV_EXTRACTION_PROVIDER=local` to run with
local rules during development.

Flyway V10 adds the NAME_CUE category and temporary-input metadata. Existing
32-input protocols and records remain usable. The opt-in `ml-integration` profile
requires a database named `simrecrut_ml` on a local host or the Compose MySQL
service; it rejects other destinations. It registers the package schema, frozen
rule, five fictional effect tables, protocol, run provenance and two models.
The metadata import creates a disabled technical actor with no roles and an
undisclosed random credential. It imports no private corpus members: the local
corpus row has zero members and points to the package provenance.

Model status ACTIVE means available in this local simulation. The evidence still
states `testEvaluated: false` and `LOCAL_RESEARCH_SIMULATION_UNCALIBRATED`; it does
not mark the model TESTED or APPROVED. Local model thresholds are 0.45 and 0.35,
the comparison threshold is 0.50 and the Objective Rule threshold is 0.70.

## Input and prediction flow

1. Java reads PDF text and runs local OCR on pages without enough embedded
   text. A list of recognized section headings and professional terms limits
   the text sent to DeepSeek. The original PDF and images stay on the server.
   Java checks the returned codes and evidence excerpts against their page
   text and saves a temporary draft with its extraction version. Unknown
   terms and unfamiliar headings can leave facts for manual review. A
   temporary request failure allows one automatic retry, and invalid output
   allows one repair request. The server also renders the first two bounded
   pages for internal portrait detection.
2. The candidate reviews the facts, assigned research categories and optional
   model text. Text starts from professional evidence in a fixed group order.
   The name is optional and entered separately. Nothing infers real ethnicity,
   gender or a name-cue category from a name or portrait.
3. The candidate selects a suggested portrait, uploads a PNG/JPEG or omits it.
   Text and portrait can be omitted independently. Saving these choices is
   required even when both are omitted. Changing professional facts invalidates
   the previous text review.
4. Confirmation freezes the chosen input digest. The original PDF is removed
   separately; the selected model input remains available for prediction/retry.
5. Java calculates matches, weights, contributions and the mandatory flag. It
   encodes the exact [39-input schema](../../contracts/feature-schemas/professional-research-v2.json).
   New rules round the final experience ratio, avoiding an earlier intermediate
   rounding difference. The first experience target defines its match; other
   mandatory experience floors remain independent checks. Legacy rules retain
   their earlier calculation.
6. The internal CPU service verifies package files once at startup and keeps
   models in memory. It uses the saved tokenizer, padding/OOV/missing-text codes
   and VGGFace2 preprocessing. Missing portraits use the trained zero-face path.
   Neutral receives neither text nor portrait and masks all 23 research values.
7. Java verifies echoed IDs, versions, manifest digest, finite scores and both
   decisions before saving. Both decisions require a passed mandatory flag,
   including when a threshold is zero. `logit` comes from the clamped raw head
   probability; a gated score of zero never creates an infinite logit.
8. Once both model outputs are committed, a leased cleanup task removes model
   input files. Retryable failures preserve the exact confirmed digest. Terminal
   failures, expiry and user deletion also trigger cleanup. No model text or
   portrait appears in public results or logs.

The matching catalog supports explicitly reviewed codes. Complex alternatives,
activity requirements and ambiguous durations still need manual resolution;
they must not be flattened into invented facts. Automatic face detection can
miss a portrait, so omission and local upload remain available. Model accuracy
has been checked on development validation data; this integration does not
establish performance on real applicants or the sealed test set.

The new automated protocol does not invent Human review rubrics. Assigning Human
reviews still requires a separately configured `reviewProtocol`; existing legacy
review configurations remain unchanged. The two Human result slots stay unassigned
until real reviews exist.

## Internal HTTP contract

`POST /internal/predict/neutral` and `/internal/predict/biased` require the internal
Bearer token. Requests contain `requestId`, `caseId`, `modelVersion`,
`featureSchemaVersion`, `modelSha256` and exactly 39 finite `features`. Optional
`biography` and `portrait: {mimeType, dataBase64}` apply only to Biased.
Responses echo the identifiers and digest, then return SUCCESS, finite `logit`,
`acceptanceScore`, both thresholds and their decisions. Invalid versions or
payloads fail; there is no synthetic fallback. `/health/ready` becomes available
only after real packages load and warm up. `/internal/models` requires the token.

Owner-only `GET` and `PUT /api/v1/candidate/cases/{caseId}/model-inputs` operate
only during review. PUT requires the normal Idempotency-Key and case revision,
plus explicit `useText` and `usePortrait` choices. It accepts optional `name`,
`biography`, `portraitIndex` or uploaded `portraitBase64`. Research-attribute GET
also returns version-specific `allowedValues`, including NAME_CUE for 39-input
cases. The browser never receives internal credentials or model artifact paths.

## Verification and operation

Run Java verification with JDK 17, frontend tests/build, inference contract tests,
offline tests and repository checks. CI uses synthetic package fixtures and an
explicit injected inference double, so no private weights are required.
Real local checks cover all four media combinations, portrait detection/review,
mandatory rejection, ownership, stale revisions, request replay, inference
interruption/retry and file deletion. Logs and synthetic fixtures belong in the
ignored `offline-ml/runtime/integration/` directory.

Stop the stack with `docker compose --env-file .env -f deploy/compose.integration.yaml down`.
This preserves its local database volume. Back up useful local results before
intentionally removing volumes. Submit the integration PR from MLOfflineTraining
to develop for review; do not merge or deploy it automatically.
