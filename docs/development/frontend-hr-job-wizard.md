# Frontend HR Job Wizard

H02 reuses `src/views/hr/jobs/wizard.vue` for `/hr/jobs/new`,
`/hr/jobs/:jobId/edit`, `/hr/jobs/:jobId/requirements` and
`/hr/jobs/:jobId/weights`. H01 is approved. H02 is approved;
H03 is now implemented; H04 now implements the authorised case list. H05 details are approved; R01-R06 now use adapted WANG pages; later units are not implemented.

## Reference and scope

PDF page 14 contains H02 and its correction. The corrected sequence is
Description, Extraction, Review Requirements, Mandatory, Weights, Validation,
Publish. The original image's Bias Parameters and Human Interview steps are
superseded by that correction and Third Meeting Preparation. The layout retains
the numbered step strip, basic information form, previous/next actions and
responsive stacking. No research rule cutoff or bias settings are editable.

Title, department, employment type, experience level and description are required.
Location and position count follow the basic information prototype. Create Draft
creates one local draft and replaces the new route with its edit route. Steps keep
unsaved inputs; Cancel and cross-job navigation confirm before discarding them.
Reload Draft explicitly confirms discarding unsaved inputs. Save Draft persists
unfinished requirements and weights within the tab, but valid basic information
is required. A full reload or sign out clears all mock changes.

The five requirement groups are Skills, Experience, Education, Languages and
Projects. Each row has text, optional accepted equivalents, assessment inclusion,
Candidate visibility, source-review confirmation and an independent mandatory
flag. Rows may be added, corrected, recategorized or removed. Text/equivalent/
category edits reset that row's review confirmation. Extracted rows show exact
source sentences and their locations; manual rows do not invent evidence.

Extraction is simple local sentence splitting and keyword grouping, explicitly
labelled as a mock. It neither calls AI nor implements professional matching.
HR must correct irrelevant company text and grouping errors. Re-extraction is
blocked until old rows are removed, preserving manual edits. A description edit
makes old extraction invalid. After removing all proposals, HR may extract again
or manually review the new description. The extraction-error scenario fails once
and offers a safe retry.

Weights are entered as percentage strings with up to five decimal places,
converted to integer units representing seven fractional decimal places. Total
must be exactly 100%, with no automatic normalization. Negative, out-of-range,
all-zero, excess-precision and invalid-total values fail. Inactive categories
require zero weight; assessed rows need active categories. An active zero-weight
category remains active. Every active group needs an assessed requirement.
Mandatory conditions must be assessed in active groups; no mandatory condition
is allowed. Every retained row requires valid text and source-review confirmation.
Public visibility is independent of assessment and mandatory settings.

Validation saves and checks the draft. Publish is disabled until mock validation
passes; any input edit invalidates that result. Publish checks the saved revision
and revalidates independently before taking a copied, read-only snapshot with
fractional weights totaling one. Every draft update shares the same revision;
stale edits conflict and keep the unsaved UI inputs. Published-only jobs cannot
be edited, and published labels are preserved while editing an existing draft.
There is at most one draft per job in this mock store.

## Frontend boundary and later integration

`store/modules/hr-jobs.js` holds session-only managed jobs, drafts and new mock
publication snapshots. H01 now reads this shared list and enables Create New Job
and Edit Draft; HrLayout enables Create Job. H03 version history is now available. Case Results now opens H04. Publishing
shows a success panel with View Job Versions and Back to Jobs. Candidate job fixtures remain independent.

All four routes require the HR demo role. The mock store separately checks owner,
editable status and expected revision. These checks provide a frontend demonstration,
not real authorization. Pending view operations are invalidated on leave/sign out.

Future backend integration must replace local loading, extraction, validation,
save and publish boundaries, including their revision and status contracts.
Requirement text/equivalents are editable demo values; criterion-specific payload
validation and real matching rules still require the backend schema. H03 now defines complete mock publication history snapshots and clone-to-draft
operations. See [HR Job Versions](frontend-hr-job-versions.md).
No backend service, database, token, real API, AI integration or dependency was added.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/hr/jobs/wizard.vue` | Shared seven-step page, actions and recovery. |
| `src/mock/hr-job-wizard.js` | Local proposals and configuration/weight validation. |
| `src/store/modules/hr-jobs.js` | Owned drafts, shared revisions and mock publications. |
| `src/mock/hr-jobs.js` | Fixture factory and list source support. |
| `src/views/hr/jobs/index.vue`, `src/layouts/HrLayout.vue` | H01 wizard entry points. |
| `src/router/index.js` | Four HR-only wizard routes. |
| `src/store/modules/auth.js` | Clear HR session data on logout. |
| `src/config/demo.js`, `.env.example` | Wizard failure scenario switch. |
| `tests/hr-job-wizard.test.js` | Scope, evidence, exact weights, revisions and publication. |

## Local review

Run `npm run dev` from `frontend/` and open `http://127.0.0.1:5173/login`.
Use `hr` or `hr@simrecrut.test`, password `SimRecrut123!`, then Manage HR jobs.
Choose Create New Job or Edit Draft on a job with a draft.

For the full flow, use this English sample description:

```text
Use Java. Two years of experience. A degree is required. Speak English. Deliver projects.
```

Create Draft, extract requirements, review all five rows, optionally mark a
mandatory condition, keep 20% for each group, validate and publish. Verify that
editing a requirement resets its review, incorrect totals block progression,
changes invalidate validation, and publication makes the draft read-only.

Set `VITE_DEMO_HR_WIZARD_SCENARIO` in `frontend/.env.local` and restart Vite.
Supported values: `normal`, `load-error`, `save-error`, `extraction-error`,
`validation-error`, `publish-error`, `conflict`. Restore `normal` afterwards.
Faults preserve saved data; reload/discard is explicit. Conflicts must be resolved
by reloading before retrying. No automatic overwrite is performed.

## Verification at H02 completion

All 57 Node tests and the production build pass. Browser verification covers
HR/Candidate and ownership boundaries, creation, saved/direct step routes,
source evidence/review gates, mandatory conditions, exact weight errors,
validation invalidation, publication, read-only recovery, unsaved confirmations,
reload/logout, production preview and responsive widths 1440, 1024, 768, 390, 320.
Failure scenarios and pending-operation cancellation are checked separately.
No console errors or external service requests were observed.
Checks cover 297 authored repository files and 162 frontend source/built text
files. Frontend source and built output contain no Han characters. Repository language,
layout and documentation checks pass excluding the pre-existing `.idea/vcs.xml`
metadata; that existing IDE file was preserved. `git diff --check` passes.

H01-H05 are approved. Research integration is complete for the current frontend demo scope. Await user review before another work area.
