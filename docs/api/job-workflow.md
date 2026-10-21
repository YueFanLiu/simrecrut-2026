# Job workflow API

The job module implements Word sections 3.3, 3.4, 3.9 and 3.10 using the
document's paths. It stores drafts and immutable published versions in MySQL
through MyBatis. It does not implement assessment cases or model scoring.

The machine-readable [job contract](../../contracts/api/jobs.openapi.json)
defines the request and response schemas for these 17 operations.

## Routes and access

Requests use the existing Bearer token. HR routes require HR plus the indicated
`simrecrut:job:` permission. Candidate routes require CANDIDATE plus browse.
Admin is not an ownership bypass. HR may access only jobs they own.

| Method | Path | Permission suffix | Behavior |
| --- | --- | --- | --- |
| GET | `/api/v1/hr/jobs` | list | Paged managed jobs |
| POST | `/api/v1/hr/jobs` | create | Create job and initial draft atomically |
| GET | `/api/v1/hr/jobs/{jobId}` | list | Reload full editable draft |
| PUT | `/api/v1/hr/jobs/{jobId}` | edit | Update title and description |
| GET | `/api/v1/hr/jobs/{jobId}/requirements` | list | Read requirements and revision |
| PUT | `/api/v1/hr/jobs/{jobId}/requirements` | edit | Replace structured requirements |
| GET | `/api/v1/hr/jobs/{jobId}/weights` | list | Read weights, active criteria and revision |
| PUT | `/api/v1/hr/jobs/{jobId}/weights` | edit | Save fractional weights and active criteria |
| POST | `/api/v1/hr/jobs/{jobId}/extract-requirements` | edit | Queue extraction, HTTP 202 |
| GET | `/api/v1/hr/jobs/{jobId}/extraction-status` | list | Processing and safe retry information |
| POST | `/api/v1/hr/jobs/{jobId}/validate` | publish | Read-only publication validation |
| POST | `/api/v1/hr/jobs/{jobId}/publish` | publish | Revalidate and freeze draft |
| GET | `/api/v1/hr/jobs/{jobId}/versions` | list | Version summary array |
| GET | `/api/v1/hr/jobs/{jobId}/versions/{jobVersionId}` | list | Full version of this managed job |
| POST | `/api/v1/hr/jobs/{jobId}/drafts` | create | Copy published version to one editable draft |
| GET | `/api/v1/candidate/jobs` | browse | Paged current published jobs |
| GET | `/api/v1/candidate/jobs/{jobId}` | browse | Current published description and public requirements |

No aliases exist for `/api/v1/jobs`, `/extract` or singular `/draft`.
Browser routes do not change. Lists accept `page` (default 1), `pageSize`
(default 20, maximum 100) and `keyword` (maximum 100 characters).
Responses use `{code: 0, message, requestId, data}` and disable caching.
Paged data is `{items, total, page, pageSize}`.

## Requests and revisions

| Operation | Request fields |
| --- | --- |
| Create | `title`, `description` |
| Update title/description | `title`, `description`, `revision` |
| Save requirements | `revision`, `requirements` |
| Save weights | `revision`, `activeCriteria`, `weights` |
| Publish | `revision` |
| Clone | `sourceJobVersionId` |
| Extract, validate | No body or `{}` |

Unknown fields are rejected. Title length is 1-255, description 1-20000.
Revisions are required positive integers. Job/version IDs contain 26 uppercase
characters. Every write returns the new shared revision; use that revision in
the next description, requirement, weight or publication request.

All persistence-changing requests require `Idempotency-Key`: 16-128 ASCII
letters, digits, underscores or hyphens. Reuse it for an unchanged retry and
generate a new key for a changed request or intentional new action. Read-only
validation does not require a key. Receipts are stored durably per account in
`sim_job_operation`; they contain job data, not Candidate CV content. Replays
recheck current role, action permission and ownership. Reusing a key for another
request returns 409. Creation and cloning return 201, including their replays.

Draft detail includes `jobId`, `jobVersionId`, `version`, `status`, `revision`,
`title`, `description`, `requirements`, `activeCriteria`, `weights`,
`matchingRuleVersion`, timestamps and `extraction`. Version summaries carry
`jobVersionId`, `version`, `status` and `publishedAt`.

Separate description, requirement and weight writes are separate transactions,
not one atomic multi-request save. Stale saves return 409. Published rows cannot
be edited. Clone returns 409 with `draftId` and `revision` when a draft exists;
database uniqueness also protects this invariant. Published titles are stored
per version, so a new draft never changes the Candidate's published title.

## Requirements and publication

Each requirement has `criterionType`, `requirementCode`, `payload`,
`assessmentIncluded`, `mandatory`, `publicVisible`, `reviewed`, `warnings`,
and optional `evidenceExcerpt` and `evidenceLocation`. Codes must be unique in
the version. Evidence must be a substring of the current description. Changing
the description invalidates old review flags and evidence.

| Criterion | Payload |
| --- | --- |
| SKILLS | `assessedSkillCodes` array |
| EXPERIENCE | Positive `targetYears`, `relevantRoleFamilyCodes`, `minimumYears` when mandatory |
| EDUCATION | `minimumDegreeLevelCode`, `acceptedSubjectCodes` or `unrestrictedSubject: true` |
| LANGUAGES | `requiredLanguages`: `{languageCode, minimumLevelCode}` items |
| PROJECTS | `assessedConditionCodes` array |

Incomplete payloads may be saved but cannot be assessed on publication. Unknown
payload fields are rejected even in drafts. All rows need HR review and resolved
warnings. Assessed requirements need active categories; mandatory requirements
must also be included in assessment. Every active category needs an assessed
structured requirement. Weights are named `skills`, `experience`, `education`,
`languages`, `projects`; each is 0-1 with at most seven fractional digits. The
exact sum must be one and inactive weights zero. Values are never normalized.

Validation returns `{valid, errors, warnings}`; errors contain `field`, `code`
and `message`. Publication repeats validation in its write transaction. Candidate
responses expose only public requirement codes, payloads and mandatory flags,
not weights, rule paths, cutoffs, owners, evidence or review warnings.

## Matching rule configuration

Set `JOB_MATCHING_RULE_VERSION` to an existing FROZEN
`sim_matching_rule_version.version_code`. Its `config_path` resolves under
`JOB_RULE_ROOT` (default `model_artifacts/rules`), including symlink resolution.
The file must be at most 1 MiB and match the row's SHA-256 checksum. Its JSON
needs `catalogs` with nonempty unique string arrays: `skillCodes`,
`roleFamilyCodes`, `degreeLevelCodes`, `subjectCodes`, `languageCodes`,
`languageLevelCodes`, `conditionCodes`. These define permitted reviewed codes.
Other frozen rule content, including equivalence maps, may coexist with catalogs.

This module does not seed or approve rules and does not invent a default rule ID
or matching algorithm. Missing/incompatible configuration returns 503. Draft
creation copies the configured rule reference. Cloning preserves the source
rule and publication checks it is still usable.

## Extraction lifecycle

Use the configured DeepSeek HTTPS API root/key and `JOB_EXTRACTION_MODEL`
(default `deepseek-chat`). The server appends `/chat/completions` to the API
root. Extraction sends the job description and professional code catalogs;
it does not read or submit Candidate CVs. No provider is called by unit tests.

HTTP 202 acceptance returns `jobId`, `revision`, `status: RUNNING`, `stage`,
`retryable`, `errorCode`, `message`, `updatedAt`. Poll extraction-status and
reload the draft on COMPLETE. Results are always unreviewed proposals. Remove
existing requirements before re-extraction to protect edits. RUNNING blocks
editing and publishing.

Provider calls run outside transactions on a bounded two-worker queue, with
one transient transport retry and one JSON repair attempt. Commit checks the
stored worker token and revision. Failures preserve the draft and expose safe
FAILED_RETRYABLE status. Invoke Extract with a new key to retry. After five
minutes, polling marks interrupted work retryable and invalidates its token.
Workers are local to one process; automatic durable scheduling after restart
is not provided. Provider diagnostics are not returned to clients.

## Migration and verification

V6 adds title snapshots, active criteria, extraction metadata, one-draft and
requirement-code uniqueness, publication weight checks, requirement review
metadata, replay receipts and HR/Candidate permissions. It creates no accounts
and grants no Admin bypass. V1-V5 are unchanged. Business actor/owner IDs must
refer to `sim_user.id`; V1's old identity comments are historical.

Before applying V6 to existing data, check multiple DRAFT versions per job,
duplicate requirement codes, and published rows with missing publication times
or weights not totalling one. Resolve conflicts deliberately; V6 does not
delete or silently repair business data. MySQL DDL is not atomic.

Local tests cover roles/actions, ownership, strict HTTP fields, revisions,
replay, immutable versions, Candidate projections, weight totals, rule-file
integrity, extraction failure, stale workers and MyBatis XML parsing. They use
isolated persistence doubles. Real MySQL migration/concurrency and live provider
integration require target-environment verification.

The frontend is unchanged and retains its existing request paths and mock-era
data fields. Future integration must align those paths with this contract and
add typed requirement mapping, extraction polling and sequential revision
handling. Authentication also needs its previously identified adapter migration.
This backend implementation does not establish complete browser integration.
