# Frontend HR Job Versions

H03 is `/hr/jobs/:jobId/versions`. H01 and H02 are approved. Only H03 and
necessary version-entry connections are implemented in this unit. H04 is approved; H05 details are approved; R01-R06 now use adapted WANG pages. Later pages await user confirmation.

## Reference and scope

PDF page 15 contains H03 and its correction. Third Meeting Preparation defines
a list of immutable published versions and the current draft, a read-only detail
panel, and creating a new draft from a published version. The image's Compare
Versions and Archived features are not confirmed baseline functions and are
omitted. Publication stays in the approved H02 wizard.

The page reuses HrLayout, the existing job store and Element Plus. Desktop uses
a horizontally scrollable table; mobile uses cards. The summary distinguishes
the current published version from the current draft. Rows show version, status,
creation time in UTC, creator, change summary and actions. The draft is first;
published history follows newest first. Pagination supports 5, 10 or 20 rows.
Refresh returns to page one. Draft-only jobs can view and edit their saved draft.

View Version opens a read-only panel within the same route, with Basic
Information, Requirements and Weights tabs. It displays saved description,
employment information, five requirement groups, accepted equivalents,
mandatory/inclusion/visibility/review flags and supplied source evidence.
Weights display the original saved percentages and active-category flags.
Incomplete draft values are shown explicitly, not replaced with valid values.
Published details contain no editable fields or Publish action. Draft details
link to H02 for editing and validation.

New Draft from the current published version is available in the summary.
A published detail panel can also copy the selected historical version. Creation
copies the full saved form, retains the source version reference, assigns a new
version number above all published versions and creates revision one. Navigation
to H02 happens only after the local mock operation succeeds. Editing that draft
cannot change the published source. An existing draft disables both clone
buttons; the store checks again at execution and a conflict offers Open Existing
Draft. Duplicate attempts cannot create a second draft. There is no delete,
archive, comparison or direct publication shortcut.

## Mock data and boundaries

The existing H01 summaries are preserved. The four initially published jobs now
have seven explicit English mock publication snapshots. Software Engineer has
published 1.0, 1.1 and 1.2 plus its existing draft 1.3. Snapshot descriptions and
reviewed professional requirements are demonstration data. The current published
marker follows the job's published version, including after H02 publishes a
new version. All prior snapshots remain unchanged when history is appended.

New publications record a version ID, timestamp, creator, change summary, full
configuration and fractional weights. New and cloned drafts record their creation
time; the original draft fixtures do not supply one, so their date displays
Not recorded in this demo. The page never fabricates that missing timestamp.
A read returns a copy, and list rows use an explicit summary projection.

The route requires the HR demo role. Store reads and clone operations separately
check managed ownership and the source version's membership in the job. These
are frontend demonstration checks, not backend authorization. Loading, detail
and clone responses are invalidated on navigation/sign out. Leaving a detail
while it loads cannot restore the closed panel. No delayed clone can create data
after sign out. Reload/sign out restores initial fixtures and clears new drafts
and publications. Candidate fixtures remain independent.

The future API boundary needs managed version-list/detail reads and draft creation
with sourceJobVersionId, server ownership/revision checks and an idempotency key.
The current UI uses local mock operations only. No backend, database, real API,
authentication/token validation, AI integration or new dependency is implemented.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/hr/jobs/versions.vue` | H03 list, read-only detail, clone actions and recovery. |
| `src/mock/hr-job-versions.js` | Initial snapshot history, form-copy/version helpers and failure delay. |
| `src/store/modules/hr-jobs.js` | Owned history/detail reads, single-draft cloning and publication metadata. |
| `src/views/hr/jobs/index.vue` | Enable H01 Versions buttons. |
| `src/views/hr/jobs/wizard.vue` | H02 publication success links to H03. |
| `src/router/index.js` | HR-only H03 route. |
| `src/config/demo.js`, `.env.example` | Version scenario flag. |
| `tests/hr-job-versions.test.js` | History, scope, immutable copies, cloning, publication and failures. |
| `tests/hr-job-wizard.test.js` | Logout now restores initial history fixtures. |

## Local review

From `frontend/`, run `npm run dev`. Open `http://127.0.0.1:5173/login` and use
`hr` or `hr@simrecrut.test`, password `SimRecrut123!`. Choose Manage HR jobs,
search Software Engineer, then Versions. Verify three published rows and draft
1.3, source evidence, weights and the disabled clone action while a draft exists.

For a new-draft flow, open Data Analyst versions and choose New Draft from 1.0.
The H02 form includes the copied reviewed requirements and 40/30/10/10/10 weights.
Validate and publish through H02, then choose View Job Versions. Version 1.1
becomes current; version 1.0 remains available and unchanged. View an older
published version to copy that configuration into the next draft.

Set `VITE_DEMO_HR_VERSIONS_SCENARIO` in `frontend/.env.local`, then restart Vite.
Values are `normal`, `empty`, `load-error`, `detail-error` and `clone-error`.
Failures provide English recovery text and keep published data intact. A clone
failure stays on H03; retry with the same source. Restore `normal` afterwards.

## Verification at H03 completion

All 63 Node tests pass and the Vite production build passes. Browser checks cover
H01/H02 entry points, current/draft separation, all read-only detail tabs and
source evidence, latest/historical cloning, H02 publication and prior-history
preservation, duplicate/ownership/role guards, draft-only jobs, pagination,
refresh, reload/logout and production preview. Widths checked are 1440, 1024,
768, 390 and 320 pixels. Mobile tab labels fit within the detail panel.

Separate browser checks cover empty/loading-error states, detail retry, failed
clone retry, a draft conflict occurring after the button was clicked, canceling
pending details, sign out during clone and rapid cross-job route changes.
No browser console errors or external requests were observed.

Repository language/layout/documentation checks pass for 301 authored files,
excluding existing IDE metadata. All 168 frontend source/built text files were
checked. Frontend source and built output have no Han characters, and
`git diff --check` passes. The pre-existing `.idea/vcs.xml` Han content was preserved.

H01-H05 are approved. Research integration is complete for the current frontend demo scope. Await user review before another work area.
