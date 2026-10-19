# Connected resume workflow

The standalone CV library supports temporary PDF import, professional fact extraction,
manual review, confirmation, structured publication, original cleanup and deletion.
It is separate from a job-linked simulation case and does not calculate assessment results.

## Authentication and permissions

The Java backend uses its own JWT and Redis session authentication. Call
`POST /api/v1/auth/login`, then send `Authorization: Bearer <accessToken>`.
The acting account comes from `AccountPrincipal`; no request accepts an owner ID.
Both the Candidate role and the operation-specific permission are required.
An Admin, HR or Researcher role alone never grants access to Candidate CV content.

Flyway V5 creates and associates the following permissions with CANDIDATE only:
`simrecrut:resume:list`, `simrecrut:resume:upload`, `simrecrut:resume:edit`,
`simrecrut:resume:confirm` and `simrecrut:resume:delete`. It creates no accounts
and does not change role membership. Existing method-specific permissions remain
checked on every operation, including replay.

## HTTP operations

All responses use `code`, `message`, `requestId` and `data`, with success code zero
and `Cache-Control: no-store`. Lists remain arrays for the existing frontend.

| Method | Path | Permission suffix | Behavior |
| --- | --- | --- | --- |
| GET | `/api/v1/resumes` | list | Own retained resume summaries |
| GET | `/api/v1/resumes/{id}` | list | Own facts, evidence and processing status |
| POST | `/api/v1/resumes/import` | upload | JSON filename and Base64 content; 201 new, 200 duplicate |
| POST | `/api/v1/resumes/{id}/clean` | edit | Start extraction; 202 means accepted, not completed |
| POST | `/api/v1/resumes/{id}/save` | edit | Save an editable draft with revision check |
| POST | `/api/v1/resumes/{id}/confirm` | edit and confirm | Lock explicitly reviewed facts |
| POST | `/api/v1/resumes/{id}/upload` | confirm | Publish confirmed JSON, then remove temporary original |
| POST | `/api/v1/resumes/{id}/cleanup` | confirm | Retry original cleanup after successful publication |
| DELETE | `/api/v1/resumes/{id}` | delete | 200 deleted, 202 inaccessible and cleanup pending |

Resume IDs retain the existing 32-character lowercase hexadecimal format.
There are no additional raw-CV, page-image, database-settings or cloud-test routes
in the authenticated API. The separate local workspace profile remains independent.

Import accepts only `filename` and `content`. PDFs must be nonempty, at most 10 MiB,
unencrypted, and contain 1-20 pages. The server parses the file rather than trusting
its extension. A `.pdf` filename is required. Request objects reject unknown fields.

Save and confirm accept `draft`, `confirmed` and `expectedRevision`. The revision is
a required positive integer. Save requires `confirmed=false`; confirm requires
`confirmed=true`. `draft` follows the existing strict professional-fact schema:
`professional` contains skills, experience, education, languages and projects;
`warnings` is an array. Corrected facts receive server-assigned USER_SUPPLIED
provenance. Empty professional facts cannot be confirmed. Clean, upload, cleanup
and delete accept no body or an empty object.

Details deliberately project allowed metadata and draft fields. They omit original
and redacted full text, source hashes, publication intent and internal storage paths.
Publication responses omit the database table name. Metadata keeps existing names
such as `created_at`, `cloud_revision` and `expires_at` for frontend compatibility.

## Replay and errors

All mutations require an `Idempotency-Key` of 16-128 ASCII letters, digits,
underscores or hyphens. Keep the same key for an unchanged request retry; use a
new key for changed input or a deliberately new extraction. Keys and canonical
payload hashes are saved for 24 hours in the account's private SQLite index.
Replay rechecks ownership, retention and permission. Reusing a key for another
payload, action or resource returns 409. A completed import replay returns the
original result and HTTP status; a new import of the same retained file returns
its existing ID with `duplicate=true`.

| Status | Meaning |
| --- | --- |
| 400 | Invalid fields, PDF, ID, operation key or confirmation flag |
| 401 | Missing or invalid authentication |
| 403 | Missing role or operation permission |
| 404 | Unknown, foreign, expired or purging resume |
| 409 | Stale revision, locked facts, incompatible state or operation-key conflict |
| 503 | Storage/publication unavailable; preserve the key when retrying |
| 500 | Unexpected internal error with a safe generic message |

No error returns SQL, credentials, stack traces or request content. Pending deletion
returns its own 202 result rather than a successful deletion claim.

## Storage and lifecycle

The HTTP controller lives in `controller/resume`; `application/resume` coordinates
requests and permissions; `gateway/resume` defines the storage boundary.
`LocalResumeGateway` adapts the existing workspace engine without exposing its
local-only settings endpoints. Temporary originals, facts, revision history and
operation receipts remain under `runtime/resumes/<internal-account-id>/`, using
private files and SQLite. The directory must be persistent on deployment.

Structured publication and removal of published data use the application's configured
DataSource (`spring.datasource`), with no separate AIVEN environment configuration.
The existing `sim_cv_cleaned` table is created if absent by the publication adapter;
this module does not use the job-linked case tables. A publication transaction
commits before original deletion. Failed original cleanup leaves confirmed JSON
intact and can be retried. Failed publication preserves confirmed facts and files.

States follow `UPLOADED -> EXTRACTING -> REVIEW_REQUIRED -> CONFIRMED`.
Extraction failures use FAILED. Poll the detail endpoint during EXTRACTING.
Confirmation and publication are separate actions. Unconfirmed imports expire after
24 hours; confirmation fixes a 30-day deadline, and draft saves do not extend it.
Deletion blocks content access first, cancels work and retries file/remote cleanup.
Only non-content deletion receipts remain after completion.

## Verification and limits

Local API tests use generated PDF fixtures, private temporary SQLite stores and a
mocked publication database. They exercise role/action checks, HTTP validation,
unknown-field rejection, extraction, ownership, response projections, replay,
revision conflicts, confirmation, publication sequencing and deletion states.
Real cloud publication and Flyway V5 execution require validation in the target
environment; the automated API suite does not modify that database.

The library keeps the existing single-process temporary workspace implementation.
It is not a multi-instance durable worker system. The service cache currently allows
32 distinct account workspaces per process. Scanned PDFs require the existing local
OCR installation. Job selection, optional research attributes, scoring and research
experiments belong to the separate case workflow and are not added here.

The current frontend still has legacy authentication adapters. Align those with the
new `/api/v1/auth` response fields before claiming browser-level integration.
