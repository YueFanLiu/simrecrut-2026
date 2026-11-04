# Frontend Review Task Details and Submission

V02 is `/reviewer/tasks/:reviewId`. Only this page is added after approval of V01.
The PDF page 25 correction removes Uncertain, suitability scoring and Save Draft.
The meeting requirements call for professional facts, public job requirements,
rubric and controlled attributes/instructions only for Instructed-Bias tasks.

## Behaviour

The page reuses HrLayout or ResearchLayout and Element Plus. Desktop has profile,
evaluation and task/job/guideline columns; narrower screens stack these panels.
The list's Open Task now navigates to this page, with My Review Tasks active.

Only currently assigned, authorised retained tasks are readable. Standard details
omit controlled attributes entirely. Neither condition exposes identity, raw CV,
model scores, thresholds, counterpart profiles or other reviewers' decisions.
An explicit projection copies five professional fields and public mock requirements.
Pending reviews expose no decision/reason, even if unexpected source fields exist.
Submitted reviews show only the current assignee's own locked decision and reason.
Missing stored submissions remain explicitly unavailable and locked.

Accept/Reject and a trimmed 1-2000 character reason are required. Submit Decision
opens a confirmation dialog. Cancel retains input. Confirm Submission shows a
loading state, rechecks live assignment access and updates the original record.
Repeated or concurrent submissions cannot overwrite the first result. Error keeps
input for retry. Grant loss removes the profile and form; a local one-second clock
also detects expiry. Route changes clear unsaved input; logout resets fixtures.
No Save Draft, Uncertain, suitability score, extra rating or backend was introduced.

R03/R04 assignments resolve to their original records. Paired tasks read only the
assigned target's professional snapshot. Standard R tasks can submit normally.
Imported R assignments do not retain bias instructions. When missing, the page
shows an English explanation and disables submission rather than inventing an
instruction. The seeded instructed-bias task has an explicit synthetic Referral
instruction for demonstration. It is not a real hiring policy. Research projections
still omit submitted reasons and decisions. No real Candidate result is fabricated.

## Files

| File under frontend | Responsibility |
| --- | --- |
| `src/views/reviewer/tasks/detail.vue` | Detail panels, validation, confirmation, loading and locked states. |
| `src/mock/review-task-detail.js` | Validation, original-record resolution and blind detail projection. |
| `src/store/modules/review-tasks.js` | Delayed mock load/submission with access and overwrite checks. |
| `src/views/reviewer/tasks/index.vue` | Enable Open Task. |
| `src/router/index.js`, `src/layouts/HrLayout.vue`, `src/layouts/ResearchLayout.vue` | Route guard and active navigation. |
| `src/config/demo.js`, `.env.example` | Normal/load-error/submit-error scenarios. |
| `tests/review-task-detail.test.js` | Nine validation, blindness, access, locking and R integration tests. |

## Local review and verification

From `frontend`, run `npm install` if necessary, then `npm run dev`. Log in with
`hr` or `researcher` and `SimRecrut123!`; open My Review Tasks and task 001.
Change `VITE_DEMO_REVIEW_DETAIL_SCENARIO` in `.env.local` and restart Vite to check
load-error or submit-error; restore normal afterwards. No external API is called.

All 98 Node tests and the production build pass. Browser checks exercise list
navigation, HR/Researcher submission, required/blank/maximum-length validation,
cancel/confirm/locked results, blind list summaries, Standard/Instructed-Bias
isolation, revoked and unassigned access, Candidate rejection, load/submit errors,
retry/preserved input, production preview and reload. Layout is checked at 1440,
1024, 768, 390 and 320 pixels. No body overflow, console errors or external requests
were observed. Authored files and frontend build text have no Han characters;
existing `.idea/vcs.xml` metadata remains unchanged. No backend code or dependencies
were added. Stop after V02 and wait for user confirmation.
