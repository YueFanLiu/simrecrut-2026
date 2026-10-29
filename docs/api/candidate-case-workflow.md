# Candidate simulation case API

This module implements the Candidate paths in Word sections 3.3 and 3.9.1. It is
independent from the `/api/v1/resumes` library. A case binds one owner, published
job version, protocol and confirmed professional snapshot. Frontend source is
unchanged. Flyway V8 and the identity/job migrations V1-V7 are required.

## Routes and authorization

All routes require the CANDIDATE role and the indicated `simrecrut:case:` action.
Acting identity comes only from the JWT principal. An Admin/HR role is not an
ownership bypass. Lists exclude other owners, expired cases and purging content.

| Method | Path | Permission | Result |
| --- | --- | --- | --- |
| POST | `/api/v1/candidate/cases` | create | Create from the selected current published job version; 201 |
| GET | `/api/v1/candidate/cases` | list | Own retained case summaries and pagination |
| POST | `/api/v1/candidate/cases/{caseId}/cv` | upload | Validate multipart PDF and queue extraction; 202 |
| GET | `/api/v1/candidate/cases/{caseId}/status` | list | State, stage, safe error and cleanup status |
| GET | `/api/v1/candidate/cases/{caseId}/draft` | edit | Editable professional facts and unresolved required facts |
| PUT | `/api/v1/candidate/cases/{caseId}/draft` | edit | Save reviewed corrections |
| GET | `/api/v1/candidate/cases/{caseId}/research-attributes` | edit | Own optional research values |
| PUT | `/api/v1/candidate/cases/{caseId}/research-attributes` | edit | Save explicit allowed research values |
| POST | `/api/v1/candidate/cases/{caseId}/confirm` | confirm | Validate and freeze facts before raw-file cleanup |
| POST | `/api/v1/candidate/cases/{caseId}/run` | run | Freeze compatible active models and queue scoring; 202 |
| POST | `/api/v1/candidate/cases/{caseId}/retry` | run | Resume stored retryable failure; 202 |
| GET | `/api/v1/candidate/cases/{caseId}/results` | results | Public partial/complete automated and primary Human outputs |
| DELETE | `/api/v1/candidate/cases/{caseId}` | delete | Revoke access and queue purge; 202, or 200 on completed replay |

Responses use `{code: 0, message, requestId, data}` and `Cache-Control: no-store`.
Returned timestamps represent UTC.
Public IDs have 26 uppercase ULID characters. Lists accept `page` (1-100000,
default 1) and `pageSize` (1-100, default 20), returning
`{items, total, page, pageSize}`. No raw-CV download endpoint is provided.

## Request contracts

The document's paths and baseline field names are preserved. Revision checks,
explicit absence and creation idempotency are completion extensions specified here.
Every write requires `Idempotency-Key`: 16-128 ASCII letters, digits, underscores
or hyphens. Reuse it only for the same logical request with unchanged input.

| Operation | Request |
| --- | --- |
| Create | JSON `{jobVersionId}` |
| Upload | Multipart part named `file`; original filename must end in `.pdf` |
| Save draft | JSON `{professional, revision, confirmedAbsent}` |
| Save attributes | JSON `{attributes: [{type, value}], revision}` |
| Confirm | JSON `{revision, confirmed: true}` |
| Run/retry/delete | No body or `{}` |

Bodies reject unknown fields. Revisions are positive integers and shared across
case mutations. Reload the draft/attributes after a change; stale versions return
409. Mutation receipts store request hashes and response metadata, not CV facts.
Replays recheck the acting user's current role/action, ownership and retention.
Reusing a key for different input, resource or action returns 409.

`professional` uses the same strict five-group fact structure as the authenticated
resume API: skills, experience, education, languages and projects arrays, with
short evidence and source metadata. No identity/contact/document-path fields are
accepted. Changed facts become USER_SUPPLIED; client-supplied evidence on changed
facts is cleared rather than being treated as extraction evidence. Known empty
groups needed by the job must be explicitly listed in `confirmedAbsent` using
SKILLS, EXPERIENCE, EDUCATION, LANGUAGES or PROJECTS. A nonempty group cannot also
be marked absent. Missing/ambiguous required facts produce 422, not a zero score.

Research types are SCHOOL_CATEGORY, REFERRAL, GENDER and ETHNICITY. Values must
occur in the case's frozen feature-schema file. Omitted families become UNKNOWN.
The client cannot set source or confirmation flags. Values are never inferred
from a CV. Confirmation locks both professional facts and optional attributes.

## Lifecycle and storage

Creation checks that the selected version is still the current published version
of an ACTIVE job. It selects one compatible frozen protocol: set
`CASE_PROTOCOL_VERSION` explicitly when several protocols match the job's rule.
No default rule/protocol/model is invented. Missing/ambiguous configuration is 503.

The workflow is AWAITING_UPLOAD, EXTRACTING, REVIEW_REQUIRED, CONFIRMED,
AUTOMATED_RUNNING and AUTOMATED_COMPLETE, with separate failure and privacy state.
Upload validates real PDF content, at most 10 MiB, 1-20 pages and no encryption.
Files use generated names under `CASE_WORKSPACE_PATH`, never the supplied filename.
Directories/files receive owner-only permissions on POSIX filesystems; operators
must also restrict the configured Windows volume/directory ACL. Parsing and OCR
run outside database transactions using the shared local rule extractor.

Unconfirmed cases expire 24 hours after creation. Confirmation commits a
professional snapshot, explicit absence, research attributes and fixed
`assessmentReferenceMonth` before queueing original removal. It sets a 30-day
deadline. File cleanup failure preserves confirmed data and retries separately.
Completing all three automated outputs replaces the deadline with completion plus
30 days. Edits and retries do not otherwise extend retention. Confirmed facts
cannot be edited; use a new case for changed professional information.

The temporary draft remains in MySQL until confirmation, then is removed from the
case row. It never enters a training dataset. Confirmation snapshots use
`sim_case_snapshot`; professional matches, Objective and ML outputs are written to
their existing canonical result tables. Private stage JSON retains validated
intermediate transport data for partial recovery and explicit public projections.
Crash-orphaned staged files remain bounded by case retention and are removed with
the case directory. Failed or ambiguous upload transactions do not delete a file
that may already have been committed.

## Frozen configuration and professional calculation

The referenced matching-rule file resolves under `JOB_RULE_ROOT`; the feature
schema resolves under `MODEL_ARTIFACTS_PATH`. Both must be at most 1 MiB, remain
inside their configured root after real-path resolution and match database SHA-256.
The protocol, feature schema and bias protocol must be FROZEN and compatible.
The feature table must declare 32 dimensions. The schema file supplies
`researchAttributes` with nonempty, unique uppercase value arrays for all four
families, including UNKNOWN. It is a server-owned versioned catalogue, not a
browser-provided schema or an inferred demographic mapping.

Matching uses the job's five reviewed criteria, inclusion/mandatory flags and
fractional weights. The rule file contains the job module's `catalogs`; optional
`aliases` maps each catalogue's observed code to a canonical code. Education and
language matching require explicit low-to-high `degreeOrder` and
`languageLevelOrder` arrays; no ordering is guessed from code names.

Skills and project conditions count unique assessed codes. Languages use the
highest requested level for each unique language. Education must satisfy every
included degree/subject requirement; unrestricted subjects need no subject match.
Experience merges overlapping relevant month intervals and divides by the frozen
target years, capped at one. When multiple assessed experience rows exist, the
maximum target years and union of relevant families define the category match;
each mandatory minimum remains an independent gate. Undated reviewed durations
cannot be combined with dated intervals or other undated durations when overlap
cannot be determined. Resolve the periods first rather than double-counting them.

Calculations use BigDecimal and seven decimal places with HALF_UP. Inactive
weights must be zero and the exact total one. Objective is the weighted sum;
Accept additionally requires every mandatory check and the protocol's `t_rule`.
Research attributes never enter Matching or Objective. The fixed reference month
prevents current employment durations from changing on a later retry.

## Model integration and result projection

Run requires one ACTIVE Neutral and one ACTIVE Biased model compatible with the
frozen protocol/schema. Their IDs are frozen once on the case. Configure
`ML_BASE_URL`, `ML_INTERNAL_TOKEN`, `ML_NEUTRAL_PATH` and `ML_BIASED_PATH` for the
team's actual internal inference service. Internal paths are explicitly configured
because the Word document does not prescribe FastAPI route names. This change
does not create/train Python models or supply model weights.

Requests contain `requestId`, `caseId`, `modelVersion`, `featureSchemaVersion` and
`professional` with five-element `m`, `w`, `mw` arrays and binary `g`. Only Biased
receives `research` with schoolPrestige, referral, gender and ethnicity. The Python
service remains responsible for the saved category encoding and Neutral zero mask.

Responses must echo identity/version/schema and return SUCCESS, finite logit,
acceptanceScore, modelThreshold, commonThreshold, decision and
commonThresholdDecision. Scores must be 0-1; thresholds must match the frozen
registry record and decisions must match their score/cutoff. Responses are capped
at 128 KiB and the complete request/body exchange is limited to 15 seconds.
Transport/protocol failures are technical errors, never Reject or fabricated zero.

Each successful stage commits separately. Retry skips saved professional/Neutral
outputs and retains the original models. Case completion requires all three
saved outputs. Candidate results expose only public job information, Objective
score/displayScore/decision/strengths/gaps, ML acceptanceScore/decision, optional
Biased attribute summary, primary Human status/decision/reason and privacy dates.
They exclude weights, thresholds, logit, internal contributions, paths and reviewer
identity. Objective display uses 0-100; ML scores use 0-1.

Human slots are `standard` and `instructedBias`, with Not assigned, Pending or
Submitted status. Missing/pending reviews have null decisions and reasons. Only
candidate-visible PRIMARY reviews are read; assignment/submission endpoints are
outside this module. Partial automated results remain null until successfully saved.

## Durable work and deletion

MySQL tasks store worker ID, lease token, expiry and attempts. Two local workers
claim queued/expired tasks; heartbeats run every 30 seconds. Each write checks the
current token, database-clock lease validity and parent privacy/retention while
holding the case lock. Restart recovery cannot overwrite an existing successful
stage. Processing failures permit explicit retry until three attempts; permanent
input failures require a new case. Cleanup retries independently and indefinitely.

Delete immediately sets PURGING, blocks reads, revokes writers, cancels pending
case tasks/reviews and queues file removal. After physical cleanup, one transaction
removes linked experiment variants, results, reviews, baselines and subjects;
invalidates affected metrics/experiments; removes grants, snapshots, attributes,
artifacts and automated outputs; then deletes case/task rows. Persistent research
profiles, unrelated subjects and frozen job/model/protocol versions remain intact.

Only non-content deletion receipts/tombstones and safe audit metadata remain.
Old operation responses and content-derived request hashes are erased; creation
keys remain blocked so a delayed replay cannot recreate a purged case. A repeated
delete with its original key reports DELETED after cleanup. Until then it reports
PURGING, never final deletion. Retention maintenance uses the same purge workflow.

## Verification boundaries

Tests use generated PDFs, temporary private directories, isolated persistence
doubles and an isolated loopback inference server. They cover access, strict
fields, revisions, replay, real parsing, confirmation locks, matching, frozen
models, partial recovery, lost leases, deletion races and public projections.
Swagger/OpenAPI exports the actual routes. No frontend files are changed.
Real MySQL migration/concurrency, production storage ACLs, installed OCR models
and the team's live Python inference/artifacts require target-environment checks.
