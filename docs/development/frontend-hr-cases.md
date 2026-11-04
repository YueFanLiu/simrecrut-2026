# Frontend HR Authorized Cases

H04 is `/hr/results`, implemented by `src/views/hr/cases/index.vue`.
H01-H05 are approved. R01-R06 now use adapted WANG pages. Later pages
await user confirmation.

## Reference and scope

PDF page 16 contains H04 and its correction. Third Meeting Preparation specifies
cases for managed jobs or explicit grants, Refresh, a job filter retained in the
query, and a future Open Case link to H05. The original image's Add Authorized
Case, CV upload, editing and Active/Inactive controls are superseded by the
correction. This page cannot create, authorise, upload, delete, activate or edit
a case. Case names and personal creator fields from the old image are omitted.

The page reuses HrLayout and Element Plus. Desktop has a scrollable table;
mobile uses case cards. Summaries show case ID, saved job title/version, creation
time, canonical processing status, Standard/Instructed-Bias Human status,
access basis and retention deadline. Times are UTC; an unset purgeAt is labelled
Not set yet. No score, hiring decision, CV content or direct Candidate identifier
is rendered in this list. Pending is a human-review state, never Reject.
Processing failures remain technical states and are not hiring decisions.

Local search matches loaded case IDs and job titles. A job filter combines with
search and is kept as `jobId` in the URL query. It survives Refresh, internal
navigation back, and direct entry after a demo session is established. Unknown
or inaccessible job IDs return no matches without disclosing job metadata.
The local-filter scope is stated next to the controls; it does not search unseen
backend pages. Sort supports newest/oldest creation time. Pagination supports
5, 10 or 20 rows. Filter/sort/page-size changes reset the page; reduced access
clamps the current page. Refresh repeats the mock read and retains filters.

Open Case now connects to H05, rechecking access before navigation and retaining
the selected jobId query. H05 independently rechecks scope before projecting
details. See [HR Case Details](frontend-hr-case-detail.md).

## Mock scope and privacy

`store/modules/hr-cases.js` holds a separate fixed mock case collection. It does
not read the Candidate store or create access to live Candidate uploads.
Eight accessible examples cover three managed Software Engineer versions, Data
Analyst, Project Manager, Product Manager and an explicitly granted external
Customer Support job. Processing examples include completed, running, fact
review, ready-to-run and retryable failure. Human reviews cover Pending,
Submitted and Not assigned without inventing a decision.

Six additional fixtures exercise exclusion: an ungranted case, an expired grant,
a past retention deadline, DELETE_REQUESTED, PURGING and DELETED privacy states.
A case is listed only with active privacy and either a managed job or a current
explicit grant. A role by itself does not grant access to every case. Grants
and retention are rechecked against the current time, including during a loaded
view. Null grant expiry represents an indefinite grant; null purgeAt means the
retention deadline has not yet been set. Invalid deadlines fail closed.

The list projection explicitly copies permitted summary fields and human-status
values. Candidate identifiers, raw CV, professional facts, research attributes,
access-grant internals and scores are not list fields. The frontend's HR route
and mock loader reject Candidate identities. These are demonstration checks,
not real authentication or backend authorisation.

A one-second local clock removes newly expired cases/grants; this is not API
polling. The timer is cleared on unmount. Pending loads cannot restore a view
after leave/sign out. Logout restores the initial HR fixtures and clears all
session-only Candidate and HR data through their existing stores.

Future backend integration must provide an authorised HR CaseSummary list,
managed/granted access checks, privacy/retention state, canonical processing and
human-review statuses, and filter contracts. No backend service, database,
real API request, token verification, AI integration or dependency was added.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/hr/cases/index.vue` | H04 list, query filter, pagination and recovery. |
| `src/mock/hr-cases.js` | English examples, access/privacy projection and local filtering. |
| `src/store/modules/hr-cases.js` | Session fixture state and async mock load boundary. |
| `src/layouts/HrLayout.vue` | Enable Case Results and indicate the active sidebar entry. |
| `src/views/hr/jobs/index.vue` | Update H01 availability text for the new case entry. |
| `src/router/index.js` | HR-only `/hr/results` route. |
| `src/store/modules/auth.js` | Reset the HR cases store on sign out. |
| `src/config/demo.js`, `.env.example` | HR cases state scenario flag. |
| `tests/hr-cases.test.js` | Scope, expiry, projection, filters and load scenarios. |

## Local review

From `frontend/`, run `npm run dev` and open `http://127.0.0.1:5173/login`.
Use `hr` or `hr@simrecrut.test`, password `SimRecrut123!`. Choose Manage HR jobs,
then Case Results in the sidebar. The eight-case list initially shows five rows.
Choose Software Engineer to inspect three saved versions and the jobId query;
combine with a case ID search, change sorting, and try 10 rows per page.

Direct HR-session URL: `/hr/results?jobId=hr-job-004`. Reload clears the demo
session and returns to Login. Candidate access to `/hr/results` returns 403.
The external Customer Support example demonstrates explicit grant access.
Open Case opens the read-only H05 detail view.

Set `VITE_DEMO_HR_CASES_SCENARIO=empty` or `error` in `frontend/.env.local`, then
restart Vite. Empty shows no accessible cases; error offers Try Again. Restore
`normal` afterwards. These flags only affect the local frontend fixture view.

## Verification at H04 completion

All 68 Node tests and the Vite production build pass. Tests verify managed/granted
scope, role-only rejection, expiry boundaries, inactive privacy exclusion,
explicit projection, copy isolation, pending/failure semantics, combined local
filters, deterministic sorting without source mutation, load errors and logout.

Browser checks cover the sidebar entry and active state, role guards, managed
and granted cases, disabled H05 actions and absence of case mutation controls,
query/search/filter combinations, unknown-job recovery, sort/page-size/paging,
Refresh and browser-back retention, live retention/grant expiry, logout during
loading, reload and production preview. Widths: 1440, 1024, 768, 390, 320 pixels.
Separate checks cover empty and loading-error states with retry. No browser
console errors or external requests were observed.

Repository language/layout/documentation and whitespace checks pass excluding
the existing IDE metadata. Frontend source and production text files contain no
Han characters. The pre-existing `.idea/vcs.xml` Han text was preserved.

H01-H05 are approved. Research integration is complete for the current frontend demo scope. Await user review before another work area.
