# Integrated Research frontend

R01-R06 use adapted copies of the WANG frontend at commit
`dbd345cd4b2816ffbd02d06d84b111ae4812d719`. The original six page layouts and
shared table are reused. The unmodified snapshot remains in
`frontend/reference/wang/`, with source hashes recorded by its manifest.
The previous standalone R01 is preserved under `frontend/reference/previous-r01/`.
There is one active Research workspace and Pinia store.

## Review findings and repairs

The source workspace imported absent `store/modules/user` and
`router/permissions` modules. Its provider was not compatible with the current
application, and global CSS could affect approved pages. The integration uses
the existing mock login, ResearchLayout, guarded nested routes and a Pinia
Research store. Styles are restricted to `.research-module`.

R03 assumed an experiment existed and did not provide complete execution or
variant interactions. R04 used selections without a fresh access check. R06
did not validate its selected experiment. These problems were repaired with
unavailable-record states, compatible seven-field validation, action-time
scope checks and explicitly labelled local demonstration actions.

## Routes and behavior

| Unit | Route | Behavior |
| --- | --- | --- |
| R01 | `/research/experiments` | Authorised list, summaries, fixed versions, search, job/status filters, date sorting and pagination. |
| R02 | `/research/experiments/new` | Seven required settings; compatible job/weights/protocol/active model checks; creates a frozen Draft and opens R03 without running. |
| R03 | `/research/experiments/:experimentId` | Six original tabs; authorised subject selection, single-factor demo pairs, run validation, unavailable comparison outputs and Pending Research assignments. |
| R04 | `/research/cases` | Two source tabs; authorised eligible profiles only, read-only details, add to a Draft and Primary demo review assignment for retained Candidate-case fixtures. |
| R05 | `/research/models` | Read-only model metadata, schema, protocol, cutoff, missing evaluation provenance and checked preselection in R02. |
| R06 | `/research/metrics?experimentId=...` | Validated experiment selection, fixed context, missing metrics/matrix/group denominators/TPR/gaps/Top-K/flip rates and Back navigation. |

Create New Experiment and View are now enabled. The six tabs on R03 remain
Configuration, Subjects, Variants, Execution, Comparison and Human Reviews.
Completed, Queued and Draft fixtures retain their original illustrative status.
Imported sample counts do not imply retained subject records or real outputs.

The mock job/model catalogue is independent of the HR demo publication store,
as in the imported source. It does not imply real access to HR versions. A
future backend must return authorised compatible catalogue entries.

## Demonstration boundaries

Researcher role plus ownership or a current explicit grant is required.
Eligible subjects additionally require active privacy and retention. Revoked,
expired or deleting subjects cannot be selected, saved, paired or reviewed.
Their linked pairs and reviews disappear from the projected experiment view.
Unknown/unauthorised experiments never render their details or metrics.
Record projections exclude raw CVs, private identities and model paths.

Single-factor pairs copy identical professional blocks and change one declared
mock attribute. Age and language are not offered as sensitive research factors.
Execution checks the local configuration and pair invariants, changes
Draft -> Running -> Completed, and locks another run. It never invokes a model
or fabricates predictions. Revocation during validation yields Failed. Logout
resets the store; pending work cannot restore an old experiment.

Assignments are idempotent Pending records for safe mock reviewer IDs and a
Standard/Instructed-Bias condition. Research assignments identify their target
variant and Research purpose; Primary assignments remain separate. Neither
creates decisions nor sends notifications. V01 now connects these assignments to each recipient's task list with an
explicit task-summary projection. See
[My Review Tasks](frontend-my-review-tasks.md). V02 can submit the original
assigned review record without exposing decisions through the Research projection.
See [Review Task Details](frontend-review-task-detail.md).

All assessment and evaluation data lacking real provenance remain Unavailable,
not zero or Reject. Objective and ML scores are not compared across scales.
There is no real authentication, authorisation, API, model call, training,
database or backend implementation. No dependency was added.

## Files and local review

Adapted views live in `frontend/src/views/research/`, with ResearchWorkspace
providing the shared state to the six copied views. Supporting files are:

- `src/components/research/RecordTable.vue`: adapted shared list.
- `src/composables/useResearchWorkspace.js`: page-local controls and provider.
- `src/store/modules/research-module.js`: session state and guarded actions.
- `src/mock/research-fixtures.js`, `research-module.js`: Research-only fixtures and explicit projections.
- `src/assets/research-workspace.css`: imported styles with an isolated prefix.
- Existing auth, router and ResearchLayout: integration points only.
- `tests/research-module.test.js`: scope, configuration, variants, run and assignment checks.

Run `npm run dev` from `frontend/`. Sign in with `researcher` or
`researcher@simrecrut.test`, password `SimRecrut123!`; choose Browse research
experiments. The sidebar exposes all Research pages. A reload clears the demo
session and returns to Login. Sign out resets Research fixtures.

`VITE_DEMO_RESEARCH_EXPERIMENTS_SCENARIO` still supports `normal`, `empty` and
`error`. Set it in `.env.local` and restart Vite to inspect states, then restore
`normal`. Error has retry; it repeats the configured error.

## Verification at Research integration completion

All 83 Node tests and the production build pass. Nine integration tests cover
scope and projections, seven-field compatibility, grant rechecks, copied
professional invariants, duplicate runs and locking, revocation/logout during
execution, separated idempotent review assignments and reserved demo login.

Browser checks cover R01-R06, the full create/select/pair/validate/assign/metrics
flow, model preselection, R04 details/Primary assignment/addition, live revoked
access, unknown IDs, Candidate/HR guards, filters/sorting/pagination, retained
Refresh filters, loading/empty/error/retry, production preview and reload.
All six pages were checked at 1440, 1024, 768, 390 and 320 pixels. Narrow tables
and tabs scroll within their panels. HR details and Candidate jobs were checked
for regression. No console errors or external requests were observed.

Frontend source and production text contain no Han characters. Repository and
whitespace checks pass except existing `.idea/vcs.xml` Han metadata, preserved
unchanged. This integration does not include Admin, Reviewer or backend pages,
and it does not commit or push changes. Further Research redevelopment is not
needed for the current frontend demo scope.
