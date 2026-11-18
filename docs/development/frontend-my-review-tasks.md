# Frontend My Review Tasks

V01 is `/reviewer/tasks`, implemented by `src/views/reviewer/tasks/index.vue`.
The Research integration and V01 are approved. Open Task now enters
[V02 details and submission](frontend-review-task-detail.md).

## Reference and scope

PDF page 24 and Third Meeting Preparation define the current user's assigned
tasks, job, condition, status, date, status filtering and Refresh. The correction
supersedes claiming tasks and revealing other decisions before submission.
Reviewer is a responsibility of an HR or Researcher, not a new account role.
No Reviewer login account is introduced.

The page reuses HrLayout or ResearchLayout according to the current account,
and Element Plus. The WANG V01 is a shared Research-table wrapper; it cannot
serve HR sessions through the Research-only provider. V01 instead uses the
existing layouts and a dedicated explicit task-summary projection.

Desktop uses a table; mobile uses cards. Task summaries show task ID, job,
optional experiment ID, Standard/Instructed-Bias condition, Primary/Research
purpose, assigned date in UTC and Pending/Submitted status. Counts show all
accessible assigned tasks, Pending and Submitted. There is no invented
In Progress state or fabricated deadline. Pending is not a hiring decision.

Local search matches task ID, experiment ID or job. Status, condition and
purpose filters combine; sorting supports newest/oldest assignment date.
Pagination uses 5, 10 or 20 rows. Filters and page size reset pagination;
loss of access clamps the page. Refresh retains filters and does not claim,
start or submit tasks. Search covers only loaded authorised mock summaries.

## Mock scope and assignments

Three initial tasks per HR/Researcher demo account illustrate both conditions,
both purposes and Pending/Submitted. Hidden records exercise other assignees,
revoked access, expired retention and purging privacy. The projection requires
an HR/Researcher role, matching assignee, an explicit active resource grant,
active privacy and valid retention. Role alone does not expose all tasks.

Every summary omits decisions and reasons, even for submitted tasks. It also
omits scores, raw CV, professional data, case links, Candidate identity,
assignee identity and access internals. The frontend demonstration does not
provide real blind-review security; a future backend must perform this
projection before returning any data.

R03 Research assignments and R04 Primary assignments now record an assignee,
assignment date and separate mock recipient resource grant. `reviewer-hr` maps
to the existing `hr` account; `reviewer-researcher` maps to `researcher`.
Only the target recipient sees these new tasks. Primary and Research remain
separate, with Pending status and no generated decision or notification.
The adapter rechecks subject privacy and retention, and the assignment's own
recipient grant. Revoking a recipient grant removes the task; resource cleanup
also removes it. An assigning user's grant and recipient grant are distinct.

A one-second local clock removes newly expired access without API polling.
Pending reads cannot restore the view after leave or sign out. Logout restores
initial tasks and clears Research assignments. Reload clears the memory session.
No backend, real authentication, token, API or dependency was added.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/reviewer/tasks/index.vue` | Task list, counts, filtering, paging and states. |
| `src/mock/review-tasks.js` | English fixtures, summary projection and Research assignment adapter. |
| `src/store/modules/review-tasks.js` | Memory-only tasks and mock load boundary. |
| `src/store/modules/research-module.js` | Add recipient/date/grant metadata to existing assignments. |
| `src/router/index.js` | HR-or-Researcher task list and detail guards. |
| `src/layouts/HrLayout.vue`, `ResearchLayout.vue` | Task navigation and active sidebar state. |
| `src/views/DemoDashboardView.vue`, `src/store/modules/auth.js` | Demo entry and logout reset. |
| `src/config/demo.js`, `.env.example` | Normal, empty and error scenarios. |
| `tests/review-tasks.test.js` | Role/scope, blind projection, expiry, filters and assignment integration. |

## Local review

Run `npm run dev` from `frontend/`. Sign in with `hr` or `researcher`, password
`SimRecrut123!`. Choose My Review Tasks on the temporary dashboard or workspace
sidebar. Both accounts see their own examples. Candidate accounts reach Access
Denied. A signed-out direct entry returns to Login.

Use `VITE_DEMO_REVIEW_TASKS_SCENARIO=empty` or `error` in `.env.local`, then
restart Vite for alternate states. Error offers Try Again and repeats the
configured failure. Restore `normal` afterwards.

## Verification at V01 completion

All 89 Node tests and the production build pass. Six new tests check role and
assignment scope, grant/retention/privacy exclusion, explicit blind projection,
combined filters/stable sorting, live R assignments and cleanup, empty/error
loads and logout reset. Existing Research assignment tests still pass.

Browser checks cover both account types and navigation, Candidate rejection,
disabled V02 actions, absence of decisions and mutation controls, filters/reset,
sorting/pagination/page size, retained Refresh, live grant expiry, new R
assignment visibility and resource cleanup, loading logout, empty/error/retry,
production preview and reload. Widths: 1440, 1024, 768, 390 and 320 pixels.
No body overflow, console errors or external requests were observed.

English/UTF-8/LF, documentation and whitespace checks pass excluding existing
IDE metadata. Frontend source and production text contain no Han characters.
The existing `.idea/vcs.xml` Han metadata was preserved. These checks describe
the approved V01 baseline; V02 adds enabled detail navigation.
