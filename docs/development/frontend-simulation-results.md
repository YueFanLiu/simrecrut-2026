# Frontend Simulation Results

C06 is `/candidate/cases/:caseId/result`. P01-P04 and C01-C05 are approved.
This unit adds C06 and the necessary View Results entry from C05 only.
C06 is approved. C07 is approved; H01 now awaits separate user review.

## Prototype and requirements

The reference is PDF page 11, including its correction, and the Candidate result
requirements in Third Meeting Preparation. The first illustration incorrectly
uses four separate background-bias results; the corrected methods are Objective
Rule, Neutral ML, Biased ML, Standard Human and Instructed-Bias Human.
Candidate-visible labels are exactly:

- Objective Fit Score, on a 0-100 display scale.
- Neutral ML Acceptance Score, on a 0-1 scale.
- Biased ML Acceptance Score, on a 0-1 scale.
- Standard Human Decision, with no numeric score.
- Instructed-Bias Human Decision, with no numeric score.

The existing CandidateLayout and CaseProgressStepper provide shared navigation
and styling. Summary, Detailed Comparison and Explanations are views within this
one page, not new page units. The case sidebar contains the retained public job
summary, completion time and deletion deadline.

The prototype's combined chart is replaced by separate Objective and ML score
panels with explicit scale labels. No mixed-scale subtraction, combined score,
percentage conversion or real hiring probability is presented. The comparison
table omits the prototype's internal HR weights and contribution breakdown;
these are explicitly unavailable to Candidate users in the requirements.

## Mock behavior and public fields

C05 creates a fixed illustrative result snapshot only after all three automated
mock methods complete. Objective is 78/100 (Accept), Neutral is 0.74 (Accept),
and Biased is 0.41 (Reject). These values and the public strengths/gaps are
unrelated to the selected CV, confirmed facts, job or research values. There is
no actual matching, scoring, model inference, reviewer action or API request.

The result projection lists allowed fields explicitly. It excludes HR weights,
rule/model cutoffs, internal contributions, logits, model paths, raw CV content
and reviewer identities. It returns a copied public job summary from case
creation, rather than loading a potentially changed job version at result time.
Optional research attributes appear only in the Biased explanation when the
case's existing demo capability permits them. Neutral has no research summary.

Two separate primary human slots initially show Pending. Not assigned and
Pending return null decision/reason and are never converted to Reject. Submitted
mock reviews contain Accept/Reject and a reason. Additional research repeats are
not included. Refresh Human Results stays on this page and keeps automated
outputs unchanged; it does not rerun the simulation or imply a real reviewer.

Missing, nonfinite or out-of-range automated outputs produce an English error;
there is no replacement zero, inferred decision or partial success display.
Unfinished/failed cases return to Progress, and unavailable, expired or foreign
cases show a safe unavailable state. Unsigned visitors return to Login.
State survives same-tab navigation only; refresh/sign out clears all demo cases.

## Deletion and next-page boundary

Delete My Simulation Data opens a modal describing removal of the structured
case/results and cancellation of pending mock reviews. Keep My Data cancels.
Confirmed deletion immediately marks the case inaccessible, clears page content
and shows a short local deletion state before removing the whole case from
Pinia. Pending reads cannot restore it. Reopening the previous result URL, using
browser back/forward or opening the upload/review page cannot read deleted data.
An intentionally rejected demo deletion leaves the data available and shows an
English retry message. This is memory cleanup only, not a server purge claim.

My simulations opens [C07](frontend-my-simulations.md). Successful deletion returns
to that list, using the shared C06/C07 confirmation modal. Choose Another Job also opens Published Jobs.
No reviewer, experiment, export or backend pages are added by this unit.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/candidate/cases/result.vue` | C06 states, tabs, refresh and deletion modal. |
| `src/components/simrecrut/ResultCard.vue` | Reusable score/decision/status card. |
| `src/components/simrecrut/ScoreScalePanel.vue` | Accessible meters with explicit separate scales. |
| `src/mock/simulation-results.js` | Fixed results, primary review examples and public projection. |
| `src/store/modules/cases.js` | Completion snapshot, guarded reads/refresh and local deletion. |
| `src/views/candidate/cases/progress.vue` | Enabled C06 entry after automated completion. |
| `src/router/index.js` | Protected demo result route. |
| `src/config/demo.js`, `.env.example` | Result scenario switch. |
| `tests/simulation-results.test.js` | Projection, validity, human semantics, guards and deletion races. |
| `tests/simulation.test.js` | Verified completed runs create the result snapshot. |

No dependency or backend implementation was added.

## Local preview

From `frontend/`, run `npm install` if dependencies are absent, then `npm run dev`.
Open `http://127.0.0.1:5173/login` and sign in with `demo` / `SimRecrut123!`.
Browse jobs -> View Details -> Start Simulation -> choose a sample PDF -> Upload
and Extract -> Review Extracted Facts -> acknowledge review -> Confirm Facts ->
Continue to Simulation -> Run Simulation -> View Results.

Use `VITE_DEMO_RESULTS_SCENARIO` in `.env.local` and restart Vite to inspect:

| Value | Scenario |
| --- | --- |
| `normal` | Three example automated results, two Pending human slots. |
| `unassigned` | Both human slots are Not assigned. |
| `submitted` | Both primary mock reviews have submitted decisions and reasons. |
| `human-update` | Refresh changes Standard to Submitted; Instructed-Bias stays Pending. |
| `load-error` | Result loading fails; Try Again retries the mock read. |
| `refresh-error` | Human refresh fails; previous results remain visible. |
| `delete-error` | Deletion is rejected before cleanup; the case stays available. |

Restore `normal` for the default flow. All scenarios remain explicitly mock data.

## Verification

At C06 completion, all 40 Node tests and the Vite build passed. C07 adds history
checks; see its guide for the current verification count.
Edge checks cover the full upstream-to-results path, the five exact card labels,
Pending semantics, separate score scales, tabs, refresh, navigation, deletion
cancel/confirm, browser back/forward, unavailable/expired/auth states and the
production preview. Responsive widths checked: 1440, 1024, 768, 390 and 320 pixels.
The comparison table scrolls within its panel without making the page overflow.
Separate browser checks cover all six non-default result scenarios.
Verified on 2026-10-10 with no browser console errors or external requests.
The repository check passed for 281 files excluding pre-existing IDE metadata;
all 138 frontend source/built text files were free of Han characters.
`git diff --check` passed.

Check browser errors, external requests, repository links/layout, whitespace
and Han characters in frontend source and built output. Existing IDE metadata
in `.idea/vcs.xml` contains Han text and is preserved; project-owned frontend
content must remain English.

C06 is approved. Stop after H01 and wait for user confirmation before H02.
