# HR cases and assigned Human reviews

This backend module implements the document routes for HR case results and
Candidate-case primary Human reviews. It uses independent account roles and the
existing V1/V2/V8 tables. No frontend file, existing migration or new Flyway
version is changed. Research experiment variants/repeats and grant-management
endpoints are outside these seven operations.

## Routes

All responses use `{code, message, requestId, data}` and `Cache-Control: no-store`.
Authenticate using the existing Bearer token. Identifiers are public ULIDs, never
database numeric IDs. No Reviewer account role is introduced.

| Method | Path | Required role and permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/hr/cases` | HR; `simrecrut:hr-case:list` | Paginate retained confirmed cases for managed jobs or HR_READ grants |
| GET | `/api/v1/hr/cases/{caseId}` | HR; `simrecrut:hr-case:detail` | Professional facts, public job, matching, weights, contributions, automated results, primary Human outputs and version references |
| GET | `/api/v1/reviewer/tasks` | HR or RESEARCHER; `simrecrut:review:list` | Paginate the acting user's assigned Candidate-case tasks |
| GET | `/api/v1/reviewer/tasks/{reviewId}` | HR or RESEARCHER; `simrecrut:review:detail` | Open the assigned task and return condition-specific review material |
| POST | `/api/v1/reviewer/tasks/{reviewId}/submit` | HR or RESEARCHER; `simrecrut:review:submit` | Atomically save and lock a decision and reason |
| GET | `/api/v1/research/cases/{caseId}/eligible-reviewers` | RESEARCHER; `simrecrut:research:review:assign` | Search only accounts eligible for this retained case |
| POST | `/api/v1/research/cases/{caseId}/reviews` | RESEARCHER; `simrecrut:research:review:assign` | Assign one or both primary review conditions |

The HR list accepts optional `jobId`, `page=1`, `pageSize=20`. The task list accepts
`status` (PENDING, OPENED, SUBMITTED, CANCELLED or EXPIRED), `page` and `pageSize`.
Page must be 1-100000 and pageSize 1-100. Pages return `items,total,page,pageSize`.
An HR summary contains `resultsRestricted` while that HR has unfinished reviews;
opening its result detail returns 403 BLIND_REVIEW_REQUIRED until those are done.
Matching values, weights and contributions follow this fixed order:
SKILLS, EXPERIENCE, EDUCATION, LANGUAGES, PROJECTS.
Unfinished automated output is null, never a fabricated zero or Reject.

## Access and eligibility

An HR must own the case's job or hold an unexpired HR_READ grant. A Researcher
must hold an unexpired RESEARCH_READ grant to search reviewers or assign tasks.
An Admin role alone bypasses neither rule. A reviewer needs an active,
email-verified independent account, an HR/RESEARCHER role, all three review
permissions, and appropriate current case access (managed job/HR_READ for HR,
RESEARCH_READ for Researcher). A Candidate cannot review their own case.

HR result reads create a non-content audit exposure event. Accounts exposed to
results through this module cannot subsequently receive a new blind assignment
for that case. Earlier exposure outside this module cannot be detected.
Eligible search accepts `query` (username substring, at most 100 characters) and
`limit` (1-100, default 50). It returns public `userId` and username `displayName`,
never email, credentials, internal IDs or the unrestricted account directory.
Assignment rechecks current eligibility rather than trusting a prior search.

Current permissions/account status are rechecked inside service transactions.
READ_COMMITTED transactions recheck blind-review guards after acquiring the
parent case lock, so a preceding assignment or exposure event cannot be hidden
by an older transaction snapshot. Parent locks serialize writes with privacy deletion; pending/opened tasks
are cancelled by the existing purge workflow. Expired, purging, unconfirmed,
foreign and inaccessible targets are unavailable (404). Losing case access also
prevents reading and replaying a task submission.

## Assign primary reviews

Both POST routes require `Idempotency-Key` (16-128 ASCII letters, numbers,
underscores or hyphens). Retry an unchanged request with its original key; changed
input/resource/action with the same key returns 409. Receipt and audit writes
commit in the same transaction as the operation. Receipts contain only metadata
and a request hash; they contain no professional facts, scenario or reason.

`POST /api/v1/research/cases/{caseId}/reviews` accepts:

```json
{
  "assignments": [
    {"reviewerId": "01EEEEEEEEEEEEEEEEEEEEEEEE", "condition": "STANDARD"},
    {"reviewerId": "01FFFFFFFFFFFFFFFFFFFFFFFF", "condition": "INSTRUCTED_BIAS"}
  ]
}
```

Send one or two different conditions. One account may receive both conditions.
Purpose is always PRIMARY and candidate_visible is always true; clients cannot
override either, submit an instruction or choose an arbitrary research scenario.
Existing PRIMARY slots, including cancelled/expired rows, cannot be overwritten
or reassigned by this API (409); V2 uniqueness constraints remain in force.
Successful creation returns 201 with `data.tasks` summaries. Both assignments
commit together. A replay returns the original summaries; query tasks for the
latest state. Missing/incompatible review configuration returns 503.

## Frozen review protocol

The existing feature-schema JSON referenced by the case's frozen protocol must
contain the following review material in addition to its existing feature data:

```json
{
  "reviewProtocol": {
    "standardRubric": {"guidance": "Assess the published professional requirements."},
    "instructedBiasRubric": {"guidance": "Assess this controlled research scenario."},
    "instructedBiasInstruction": "Use the approved complete experimental instruction for this research simulation."
  }
}
```

These are configuration examples, not an approved experimental instruction.
Supply the actual reviewed rubrics and instruction before deployment. Each rubric
must be a nonempty object. The biased instruction is nonblank and at most 8000
characters. Configuration must be published as a new compatible frozen version
with its correct hash; do not edit a frozen file in place. The backend verifies
the existing frozen configuration/hash and freezes the selected rubric, version
references, instruction and complete four-family scenario on assignment.

STANDARD stores/returns professional material and its rubric only. INSTRUCTED_BIAS
adds all four case-confirmed research families (SCHOOL_CATEGORY, REFERRAL, GENDER,
ETHNICITY; absent values UNKNOWN) and the saved instruction. No field is inferred
from a name, CV or identifier. Rubrics must contain reviewer-facing guidance only,
not scores, thresholds, model coefficients or other reviewers' answers.

## Open and submit

Opening an assigned PENDING task marks it OPENED. Detail includes professional
facts, public job requirements, the frozen rubric and job/rule/protocol/schema
version references with the fixed assessment reference month. Evidence text is excluded
from both HR and reviewer professional projections to avoid disclosing source
identifiers. Automated outputs, weights, contributions and other Human answers
are absent from the reviewer DTO. STANDARD never returns the research scenario
or biased instruction. HR detail does not return research attributes, raw CV,
storage paths, reviewer identity, ML logit or thresholds.

`POST /api/v1/reviewer/tasks/{reviewId}/submit` accepts:

```json
{"decision": "ACCEPT", "reason": "The confirmed skills meet the published requirements."}
```

Decision accepts ACCEPT/REJECT (also Accept/Reject for the existing database
convention). Reason must contain 1-2000 characters and non-whitespace content.
No condition, reviewer identity or status field is accepted. Only PENDING/OPENED
can be submitted. The operation updates the review, submission timestamp, parent
human status, replay receipt and non-content audit event in one transaction.
Response contains `reviewId,status,submittedAt`; it does not reveal an answer.
Further changes are rejected with 409. Unknown request fields are rejected.

The parent status becomes PENDING after assignment, PARTIAL after one of the
two conditions is submitted, and COMPLETE after both. Existing Candidate results
read the same PRIMARY rows and show saved decisions/reasons without reviewer
identity; missing and pending answers remain null.

If an account has both conditions, its earlier answer remains hidden in task
detail and HR results remain blocked until all its PENDING/OPENED case tasks are
submitted. Then submitted task detail can show that task's saved decision,
reason and timestamp. This API does not add a post-submit comparison endpoint.

## Deferred database configuration

Per the requested workflow, this change creates no new Flyway migration and does
not auto-insert permission records at startup. The existing schema suffices, but
the following permission catalogue/grants still need inclusion when database
changes are explicitly requested:

| Permission | Allowed roles |
| --- | --- |
| `simrecrut:hr-case:list` | HR |
| `simrecrut:hr-case:detail` | HR |
| `simrecrut:review:list` | HR, RESEARCHER |
| `simrecrut:review:detail` | HR, RESEARCHER |
| `simrecrut:review:submit` | HR, RESEARCHER |
| `simrecrut:research:review:assign` | RESEARCHER |

Until those permissions are provisioned, these endpoints intentionally return
403. Existing administrator permission management recognizes their role scopes
once the catalogue rows exist. Current authentication sessions must be refreshed
after permission changes using the existing identity mechanisms. Explicit grants
must already exist for Researcher access and non-owner HR access; assigning a task
does not silently grant access or expose every retained Candidate case.

## Verification boundary

Workflow and HTTP tests cover scope denial, role/action checks, frozen assignment,
duplicate prevention, idempotent submission, immutability, privacy denial, safe
projections and dual-condition blindness. MyBatis XML and dynamic SQL generation
are checked without contacting a configured application database. OpenAPI is
exported from actual controllers. Live MySQL transactions, lock contention and
deployment permission provisioning still require environment integration checks.
