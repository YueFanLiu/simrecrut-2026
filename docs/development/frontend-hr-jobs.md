# Frontend HR Job Management

H01 is `/hr/jobs`. P01-P04, C01-C07 and H01 are approved. H02 now enables
creation and draft editing through its shared wizard. H03 now enables version history. H04 now enables the authorised case list. H05 details are approved; R01-R06 now use adapted WANG pages. Later units await
separate development after user confirmation. See [HR Job Wizard](frontend-hr-job-wizard.md).

## Reference and scope

The reference is H01 on PDF page 13, including its correction, and the HR Jobs
requirements in Third Meeting Preparation. H01 lists jobs the HR user manages.
The prototype's delete/archive actions are not baseline features and are omitted.
Published versions are immutable; an associated draft is listed separately.
Creating and editing now use the shared H02 wizard, while Versions opens H03.
Case Results now opens H04 through the HR sidebar. H01 reads session changes from the shared HR store. Published-only
jobs have no Edit Draft action; published jobs with a separate draft do.

The page uses Vue Composition API, Element Plus and the new HrLayout with an
HR sidebar. The existing BrandMark is reused. Desktop uses a scrollable job
table and smaller screens use cards. Search matches title, department or summary;
Draft/Published filtering combines with keyword search. Newest/Oldest First sorts
by creation date. Pagination supports 5, 10 or 20 jobs per page. Search/filter/
sort/page-size changes reset to page one. Refresh retries the local list read.

## Demo role and data boundaries

Sign in with `hr` or `hr@simrecrut.test`, password `SimRecrut123!`, then choose
Manage HR jobs on the temporary dashboard. The existing `demo` Candidate account
retains its existing behavior. Registration remains Candidate-only; the reserved
HR mock credentials cannot be registered as a new Candidate account.

The HR route requires a demo session and the HR demo role. Unsigned visitors
return to Login and Candidate visitors go to Access Denied. The mock loader also
rejects non-HR identities and explicitly filters job ownership. These are frontend
demonstration checks, not real authorization or backend permission enforcement.

The initial eight English job examples include drafts, published jobs and a published
job with a newer separate draft. Another owner's fixture is excluded. Only
explicit list fields are returned; Candidate data, internal weights/cutoffs and
ownership metadata are not exposed in the list. The HR fixtures are independent
from the approved Candidate fixtures and do not change Candidate jobs or cases.

No real API, backend, database, token, inference, delete or archive operation
exists. H02 publication changes only session-local mock HR data. Sessions clear on browser reload/sign out. Leaving the page
or signing out while loading prevents the delayed response from restoring UI
content. Historical published versions cannot be changed from this page.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/hr/jobs/index.vue` | H01 list, filters, sorting, pagination and states. |
| `src/layouts/HrLayout.vue` | HR workspace header/sidebar and responsive layout. |
| `src/mock/hr-jobs.js` | Managed fixtures, public projection and mock list boundary. |
| `src/mock/auth.js`, `src/store/modules/auth.js` | Reserved HR demo account and role check. |
| `src/router/index.js` | H01 route and demo role navigation guard. |
| `src/views/DemoDashboardView.vue` | Role-appropriate HR demo entry. |
| `src/config/demo.js`, `.env.example` | HR jobs state scenario switch. |
| `tests/hr-jobs.test.js` | HR account, scope, projection, filtering/sorting and failure checks. |

No dependency was added. Approved product pages were not otherwise edited.

## Local startup and review

From `frontend/`, run `npm install` if dependencies are absent, then `npm run dev`.
Open `http://127.0.0.1:5173/login`, sign in with the HR credentials above, then
click Manage HR jobs. Inspect search/status/sort controls, both version labels on
Software Engineer, and pagination. A Candidate navigating to `/hr/jobs` sees 403.

Set `VITE_DEMO_HR_JOBS_SCENARIO=empty` or `error` in `frontend/.env.local` and
restart Vite to inspect empty/loading-error states. Try Again repeats the mock
read. Restore `normal` to show the standard list.

## Verification at H01 completion

All 51 Node tests pass and the Vite production build passes. New tests cover HR
username/email login, reserved registration, Candidate rejection, managed-only
jobs, draft/published separation, explicit projection, combined filters, sorting
without mutation and empty/error scenarios.

Edge checks cover unauthenticated/Candidate guards, HR login/dashboard entry,
the eight managed jobs, disabled future actions, search/status/sort/reset,
pagination/page size, refresh, logout/reload, empty/error retry and production
preview. Responsive widths checked: 1440, 1024, 768, 390 and 320 pixels.
Verified on 2026-10-10 with no browser console errors or external requests.
Repository checks passed for 292 files excluding existing IDE metadata; all 153
frontend source/built text files were free of Han characters. `git diff --check` passed.
Check browser errors/external requests, documentation links/layout, whitespace,
and Han text in frontend source and built output. Existing `.idea/vcs.xml`
metadata contains pre-existing Han text and is preserved.

H01-H05 are approved. Research integration is complete for the current frontend demo scope. Await user review before another work area.
