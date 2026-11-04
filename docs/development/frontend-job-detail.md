# Frontend Job Detail

C02 is the Candidate detail page at `/candidate/jobs/:jobId`. P01-P04 and C01
have been reviewed and approved. C02 was approved before C03 development.
The implementation uses the prototype's C02 layout (PDF page 7) and corrections.
It provides public information only and removes the unconfirmed Save Job action.

## Layout and behavior

The page reuses CandidateLayout and provides a Jobs breadcrumb, title, company,
location, employment type and publication date. Job Description and Requirements
appear alongside Key Information and Additional Information on desktop; mobile
stacks the panels. Public requirements include responsibilities, qualifications,
experience, education and language. Mock deadlines and published versions appear
in Key Information. All content and feedback are English.

View Details on C01 now opens the selected job. All eight listed mock jobs have
matching details. Jobs in the header, the breadcrumb and Back to Jobs return to
C01. The list currently reloads its default filters on return. Start Simulation
now creates an in-memory case for the displayed version and opens the implemented
[C03 upload demo](frontend-cv-upload.md). Creation loading/errors are shown in
English. My simulations opens C07; no backend case is created.

Loading shows a skeleton and status message. Unknown, retired or unavailable jobs
show Job Not Available with a safe Back to Jobs link. A simulated load failure
shows an English error, Try again and Back to Jobs. Errors and unavailable states
do not retain information from a previously displayed job. Rapid route changes
ignore stale responses; navigating away also invalidates pending loads.

## Files and data boundary

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/candidate/jobs/detail.vue` | Route-based loading, detail panels, states and recovery. |
| `src/mock/job-details.js` | Public detail profiles, delayed mock loading and explicit projection. |
| `tests/job-details.test.js` | Version consistency, all list entries, unavailable states and projection checks. |
| `src/router/index.js` | Protected JobDetail route. |
| `src/components/simrecrut/JobCard.vue` | Enabled View Details entry with selected jobId. |
| `src/views/candidate/jobs/index.vue` | Updated demo notice for the enabled detail page. |
| `src/config/demo.js`, `.env.example` | Optional detail state preview configuration. |

The loader resolves the current Published summary from the existing mock list.
It ignores version and redirect query parameters; the displayed published
version cannot be replaced with the newer draft. The public detail projection
includes jobId, jobVersionId, version, title, description and requirements plus
the prototype's public metadata. Internal fields such as weights, cutoffs and
status flags are omitted. Requirement arrays are copied, so UI edits cannot
mutate the source profiles.

Requirements use a simple local UI shape because the preparation document leaves
the real requirements schema untyped. The eventual approved API schema will
determine integration. The generated jobVersionId is a demo identifier only;
later case creation must use the backend-provided displayed version identifier.
No real API, authentication, backend authorization, database or case creation
is implemented in this phase.

## Local preview

Follow [local setup](local-setup.md). Sign in, select Browse published jobs, then
View Details on a card. Direct entry without a demo session and refreshing a
protected detail route return to Login because the session is held in memory.

Optional `frontend/.env.local` configuration:

```text
VITE_DEMO_JOB_DETAIL_SCENARIO=unavailable
```

Use `normal` (default), `unavailable`, or `error`, then restart Vite. Retry repeats
the configured error; restore normal and restart for a successful detail load.
For an invalid job example, use `/candidate/jobs/missing-job` in the signed-in
tab. No visible scenario controls are added to the product.

## Verification on 2026-10-10

- All 16 Node tests passed, including four new detail tests. All eight list jobs
  resolve to the same current published title and version.
- Production build passed without warnings.
- Edge browser checks passed for card entry, description and requirements,
  public version display, missing and retired job recovery, route changes,
  list search regression, ignored query values, refresh and sign out.
- Loading, configured unavailable and configured failure states passed; retry
  and keyboard activation of Back to Jobs passed.
- No horizontal overflow at 1440, 1024, 768, 390 and 320 pixels. Desktop, mobile,
  unavailable and error screenshots were inspected.
- Production preview passed for normal detail and unavailable recovery. No
  console errors or external requests were observed in the normal flows.
- Authored frontend files and generated production files passed the Han scan.
  Repository checks excluding preserved IDE metadata and `git diff --check`
  passed. The full repository retains the existing `.idea/vcs.xml` language
  failure; that file was not edited.

The responsive cards and information layout were compared with the
[official RuoYi-Vue3 profile source](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/system/user/profile/index.vue)
on 2026-10-10. No upstream page or Chinese content was copied.

C02 and C03 are approved. C04 is approved; C05 is approved; C06 is approved; C07 is approved; H01 now awaits separate review.
