# Frontend My Simulations

C07 is `/candidate/cases`. P01-P04 and C01-C06 are approved. C07 is approved. H01 is implemented
and awaits separate user review before H02.

## Reference and scope

The reference is C07 on PDF page 12, including its corrections, and the My
simulations requirements in Third Meeting Preparation. The page shows only
owned retained cases, processing/Human statuses, upload dates and deletion dates.
Continue opens the state-appropriate page; completed cases open C06.
Delete requires confirmation. Expired and purging/deleted cases are unreadable.

The desktop table and mobile cards reuse CandidateLayout and Element Plus
controls. Rows sort by newest upload, falling back to creation time before any
upload; the list supports 5, 10 or 20 cases per page. It includes the job version
and separately labelled Objective 0-100 and ML 0-1 columns. There are no new
search, export, score-editing or backend features outside the prototype scope.

## State and result handling

| Case state | Continue destination |
| --- | --- |
| AWAITING_UPLOAD, UPLOADED, EXTRACTING | C03 upload/extraction. |
| REVIEW_REQUIRED | C04 fact review. |
| CONFIRMED, AUTOMATED_RUNNING | C05 progress. |
| AUTOMATED_COMPLETE | C06 results. |
| FAILED_RETRYABLE, FAILED_FINAL before confirmation | C03 failed extraction stage. |
| FAILED_RETRYABLE, FAILED_FINAL after confirmation | C05 failed assessment stage. |
| Unknown state | Continue unavailable; no guessed destination. |

Continue rechecks the current state, ownership and expiry at click time rather
than trusting a stale row. The list updates from reactive local state and checks
expiry every second. Removing the last row on a page clamps pagination to a valid
page. Expired cases disappear from the list and existing detail routes deny reads.

The default list contains cases actually created in the current demo session;
there is no automatic sample history. It starts empty after a fresh login.
Browser reload or sign out clears Pinia; Refresh on this page only retries the
local history read. Persistence and real reload recovery await backend work.

List rows explicitly project public fields. They contain no CV text, file names,
drafts, evidence, research values, reviewer identities/reasons, internal weights
or cutoffs. All automated scores and Human summaries are unavailable until the
three automated stages complete with valid outputs. Missing scores are displayed
as an em dash, not zero. Pending/Not assigned Human slots never become Reject.
Submitted primary reviews show their saved Accept/Reject, without the reason in
the list. Full public reasons remain on C06. No new scoring or inference occurs.

## Deletion and existing-page connections

C06 and C07 share SimulationDeleteDialog. It explains removal of the case/facts,
optional details/results and cancellation of pending mock reviews. Cancel keeps
the data. Confirmed deletion marks the case inaccessible before local cleanup;
the row disappears immediately and success is reported only after cleanup.
Previously open routes and pending mock work cannot restore the deleted case.
A rejected demo deletion preserves data and shows an English retry message.

Candidate navigation now enables My simulations and highlights case pages.
C06's My simulations button opens C07, and successful C06 deletion returns here.
C03/C04 Cancel and Delete also return here instead of their temporary Jobs target.
Other approved-page behavior remains as implemented.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/candidate/cases/index.vue` | List, states, pagination, resume and deletion. |
| `src/components/simrecrut/CaseHistoryCard.vue` | Mobile public case summary card. |
| `src/components/simrecrut/SimulationDeleteDialog.vue` | Shared C06/C07 confirmation. |
| `src/utils/case-history.js` | Explicit summary, state destinations and upload sorting. |
| `src/store/modules/cases.js` | Creation/upload timestamps and owned retained history reads. |
| `src/router/index.js`, `src/layouts/CandidateLayout.vue` | C07 route and navigation. |
| `src/views/candidate/cases/upload.vue`, `review.vue`, `result.vue` | Required C07 redirects/entry. |
| `src/config/demo.js`, `.env.example` | History failure scenario switch. |
| `tests/case-history.test.js` | Routes, public fields, ownership, expiry, sorting and read/delete races. |

No dependency, database, backend authentication or real API implementation was added.

## Local preview

From `frontend/`, run `npm install` if needed, then `npm run dev`.
Open `http://127.0.0.1:5173/login` and sign in with `demo` / `SimRecrut123!`.
Browse a job -> View Details -> Start Simulation. The case appears under the
My simulations navigation link immediately. Continue it through upload, review
and simulation, then return to the list to inspect updated statuses/results.
Start a New Simulation opens Published Jobs and creates nothing until a job is
selected. Empty-state Browse Jobs uses the same destination.

Set `VITE_DEMO_HISTORY_SCENARIO=error` in `frontend/.env.local` and restart Vite
to inspect the loading error and Try Again. Use `delete-error` to inspect a
rejected deletion. Restore `normal` for the default flow. These switches do not
seed case data or implement a server permission policy.

## Verification

At C07 completion, all 46 Node tests and the Vite production build passed. New tests verify every
supported Continue destination, extraction/assessment failure distinction,
owner/expiry/purge filtering, no early/private results, Human semantics, sorting,
loading failure preservation and deletion/sign-out races.

Edge checks cover empty state, full upstream entry, 13 mixed test-only records,
pagination, state routes, C03/C04/C06 deletion returns, row cancellation/deletion,
expiry updates, inaccessible old result routes, reload/logout and production
preview. Responsive widths are 1440, 1024, 768, 390 and 320 pixels. Small screens
show cards; larger screens keep table scrolling inside its panel.
Error and delete-error browser scenarios preserve existing case data.
Verified on 2026-10-10 with no browser errors or external requests in the normal
flow. Repository checks passed for 287 files excluding existing IDE metadata;
all 147 frontend source/built text files were free of Han characters.
`git diff --check` passed.

Also verify browser errors, external requests, documentation links/layout,
whitespace and Han text in frontend source/built output. Existing `.idea/vcs.xml`
metadata contains pre-existing Han characters and is not edited in this unit.

C07 is approved. Stop after H01 for user confirmation before H02. No
Researcher, Reviewer or Admin page is implemented in this step.
