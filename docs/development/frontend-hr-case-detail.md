# Frontend HR Authorized Case Details

H05 is `/hr/results/:caseId`, implemented by `src/views/hr/cases/detail.vue`.
H01-H05 are approved. R01-R06 now use adapted WANG pages.

## Reference and scope

PDF page 17 and Third Meeting Preparation define a read-only professional
snapshot, fixed job version, matching contributions and five outcome slots.
The page reuses HrLayout and Element Plus. It provides Back to Results and
Refresh, retaining the selected jobId query. There are no score-editing controls.

The snapshot contains Skills, Experience, Education, Language and Projects.
Unconfirmed cases have no confirmed facts. Recorded references cover the job,
weight configuration, matching rules, protocol and both model versions.
Five matching rows display saved weights, matches and contribution points.
Fixture weights match the corresponding H03 published version. Fixture
arithmetic is unrelated to any uploaded CV or real matching engine.

Objective fit uses 0-100; ML acceptance scores use 0-1. Standard Human and
Instructed-Bias Human display status and a decision/reason only when submitted.
Pending never means Reject. Running, not-started, unavailable and technical
failure states do not invent scores or hiring decisions. A failed mandatory
condition with an accepting ML prediction produces an explanatory warning;
the stored prediction remains unchanged.

## Mock access and visibility

`readDetail` reuses H04 managed-job or explicit-grant access, privacy and
retention checks. Unknown, ungranted, expired and deleting cases are unavailable.
A local clock clears loaded details after access expires. Request sequencing
prevents stale loads from restoring content after navigation or sign out.

The projection copies only permitted professional, version and result fields.
It omits direct identifiers, raw CVs, research attributes and model paths.
Blind mode removes matching and all five result objects at the mock read
boundary. It does not merely hide result elements with CSS.

These checks demonstrate frontend behavior only. A future backend must enforce
authentication, resource authorisation and blind-review projection before
returning data. This unit adds no backend, API request or dependency.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/hr/cases/detail.vue` | Read-only page, states, refresh and navigation. |
| `src/mock/hr-case-detail.js` | Fixed fixtures and explicit detail projection. |
| `src/mock/hr-cases.js` | Attach details using saved published-version weights. |
| `src/store/modules/hr-cases.js` | Recheck access before reading a detail. |
| `src/views/hr/cases/index.vue` | Enable Open Case with an access recheck. |
| `src/router/index.js`, `src/layouts/HrLayout.vue` | HR route and active navigation. |
| `src/config/demo.js`, `.env.example` | Normal, error and blind scenarios. |
| `tests/hr-case-detail.test.js` | Scope, projection, scales and result semantics. |

## Local review

From `frontend/`, run `npm run dev`. Sign in with `hr` or `hr@simrecrut.test`,
password `SimRecrut123!`. Choose Manage HR jobs, Case Results and Open Case.
An HR session can also navigate to `/hr/results/hr-case-008?jobId=hr-job-004`.
Reload clears the memory-only session and returns to Login.

Set `VITE_DEMO_HR_DETAIL_SCENARIO=error` or `blind` in `.env.local`, then restart
Vite. Error provides Try Again; blind retains the snapshot and versions while
omitting result data. Restore `normal` afterwards.

## Verification

All 74 Node tests and the production build pass. Detail tests cover scope and
expiry, explicit copy projection, fixed version weights, independent scales,
unconfirmed/partial/failed states, blind payload omission, malformed results
and preserved conflicting ML predictions.

Browser checks cover H04 entry, retained filters, readonly content, five slots,
pending/submitted/partial/failed states, unavailable cases, live grant expiry
and privacy changes, logout during loading, Candidate rejection, production
preview and reload. Layout checks cover 1440, 1024, 768, 390 and 320 pixels.
The narrow matching table scrolls within its panel. Error and blind modes are
also checked. No console errors or external requests were observed.

Authored frontend and production text contain no Han characters. Repository
and whitespace checks pass apart from existing `.idea/vcs.xml` Han metadata,
which was preserved. H01-H05 are approved. Research integration is complete for the current frontend demo scope. Await user review before another work area.
