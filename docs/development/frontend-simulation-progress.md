# Frontend Simulation Progress

C05 is `/candidate/cases/:caseId/progress`. P01-P04 and C01-C04 are approved.
C05 is approved. C06 is approved; C07 is approved; H01 now awaits separate user review.

## Design and scope

The prototype on PDF page 10 provides the stepper, status panel, simulation log,
parameters and progress summary. Its corrections override the illustration's
percentage and estimated duration: show stages only, do not re-extract a CV,
and do not provide Pause. The requirements specify a manual Run Simulation
after CONFIRMED and separate Objective, Neutral ML and Biased ML statuses.
The existing CandidateLayout and CaseProgressStepper keep prior page styling.
Unused prototype navigation for other roles is not added to the Candidate area.

All states are frontend mocks. There are no API calls, model execution or real
authentication. C06 now attaches fixed illustrative scores/decisions after
completion. A live backend must eventually supply status,
model/version compatibility, frozen configuration and persisted results.
The parameters panel shows only known case context; it invents no bias weights.

## Behavior

- Continue to Simulation on C04 opens the progress page without starting a run.
- Run Simulation accepts CONFIRMED only. Synchronous state changes block duplicates.
- Fixed mock methods move from Waiting to Running to Complete. All three must
  complete before AUTOMATED_COMPLETE. The C06 fixed result snapshot is then created.
  Human review remains Pending.
- A retryable Neutral ML failure preserves Objective completion. One local retry
  resumes unfinished stages only. A final failure has no retry action.
- Confirmed facts and job version are retained; failed methods produce no score
  or decision. Upload and extraction retry actions reject confirmed cases.
- Reopening upload or review retains confirmation locks for running, completed
  and failed assessments. No new editable draft or PDF extraction is started.
- The local log records stage events and Paris timestamps without raw CV text.
- The completion state extends the demo retention deadline to 30 days.
- Unknown, expired and inaccessible cases show a safe unavailable panel.
  Unconfirmed cases return to preparation; unsigned visitors return to Login.
- Navigating within the tab preserves the run in Pinia. Refresh or sign out
  clears all cases; no reload recovery or persistence is claimed.
- View Results opens [C06](frontend-simulation-results.md). My simulations opens [C07](frontend-my-simulations.md).

The proposed backend retry contract in the requirements is not implemented or
assumed available. The retry action described here is a defined local mock only.
Future integration must use server retryability and ownership/status responses.

## Files and local preview

| File under `frontend/` | Purpose |
| --- | --- |
| `src/views/candidate/cases/progress.vue` | C05 layout, statuses, log and navigation. |
| `src/store/modules/cases.js` | Mock stages, guarded run/retry and confirmation locks. |
| `src/router/index.js` | Protected demo progress route. |
| `src/components/simrecrut/CaseProgressStepper.vue` | Completed run indicator; prior steps retain their defaults. |
| `src/config/demo.js`, `.env.example` | Simulation scenario switch. |
| `src/views/candidate/cases/review.vue` | C05 entry and read-only confirmed states. |
| `src/views/candidate/cases/upload.vue` | Retained upload lock after assessment starts. |
| `tests/simulation.test.js` | State, duplicate, retry, final failure and logout checks. |

From `frontend/`, run `npm install` if dependencies are absent, then `npm run dev`.
Open `http://127.0.0.1:5173/login`; sign in with `demo` and `SimRecrut123!`.
Browse jobs -> View Details -> Start Simulation -> choose a sample PDF -> Upload
and Extract -> Review Extracted Facts -> acknowledge review -> Confirm Facts ->
Continue to Simulation -> Run Simulation -> View Results.

Set `VITE_DEMO_SIMULATION_SCENARIO=retryable` or `final` in `.env.local`, restart
Vite and repeat the flow to inspect failures. Default is `normal`.
No dependency or backend code was added.

## Verification and boundary

Run `npm test` and `npm run build` from `frontend/`.
Browser verification covers entry, preparation guard, states, same-tab resume,
confirmation locks, failure/retry, missing/expired cases, refresh/sign out,
production preview and responsive widths 1440, 1024, 768, 390 and 320 pixels.
Check browser errors, external requests and authored/built content for Han text.

Verified on 2026-10-10: all 33 Node tests passed, Vite production build passed,
and Edge browser checks passed with no console errors or external requests.
The repository check passes outside the pre-existing IDE metadata; `.idea/vcs.xml`
contains existing Han text and was left untouched. Frontend source and dist have
no Han text. No extra dependency or backend implementation was introduced.

C05 is approved. C06 is approved. C07 is approved. Stop after H01 for user review.
