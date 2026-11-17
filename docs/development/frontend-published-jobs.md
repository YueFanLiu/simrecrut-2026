# Frontend Published Jobs

C01 is the Candidate job list at `/candidate/jobs`. P01-P04 have been reviewed
and approved. C01 was reviewed and approved before C02 development.
This phase provides frontend interactions with local mock data only.

## Layout and behavior

The prototype's C01 page (PDF page 6) provides the brand and Candidate navigation,
keyword search, category/location/type filters, sorting and job summary cards.
Cards show title, company, location, tags, summary, publication date and current
published version. The desktop grid has three columns, tablet two and mobile one.
Eight mock jobs demonstrate pagination with six cards per page. Search and select
changes reset pagination. Search matches title, company and summary, ignoring case
and surrounding spaces. Filters intersect; sorting supports latest, oldest and
English title order. These operations are explicitly labeled as a local demo;
future API support must be confirmed before enabling them with real data.

Loading, no published jobs, no matching jobs, and load failure with retry have
English states. Clear Filters restores the default list and latest sorting.
View Details now opens the implemented [C02 Job Detail](frontend-job-detail.md).
My simulations opens the C07 case list.

## Files and responsibilities

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/candidate/jobs/index.vue` | List state, filters, pagination and feedback. |
| `src/layouts/CandidateLayout.vue` | Reusable Candidate header, content and footer. |
| `src/components/simrecrut/JobCard.vue` | Reusable public job summary card. |
| `src/mock/jobs.js` | Delayed mock loader and explicit Candidate projection. |
| `src/utils/job-list.js` | Non-mutating filtering, sorting and English dates. |
| `src/router/index.js` | Protected demo list route. |
| `src/config/demo.js`, `.env.example` | Optional empty/error mock scenario. |
| `src/views/DemoDashboardView.vue` | Browse published jobs navigation entry. |
| `src/views/error/NotFoundView.vue` | Allow safe back navigation to the implemented list. |
| `tests/jobs.test.js` | Publication, projection, filtering and mock-state checks. |

Only current Published versions enter the list. Historical, Draft and Retired
fixtures are excluded. The explicit projection omits status flags and any
unlisted internal fields, including weights and cutoffs. A newer draft does not
replace a current published version. This is mock behavior, not backend security.
Later integration can replace `loadPublishedJobs` with the approved public list
endpoint. No backend, database, token, permission service or network API is added.

## Local preview

Follow [local setup](local-setup.md), sign in, and select Browse published jobs on
the temporary Dashboard. The Pinia session is in memory; direct entry without a
session or refresh returns to Login. Dashboard and Sign out remain available.

Optional `frontend/.env.local` configuration:

```text
VITE_DEMO_JOBS_SCENARIO=empty
```

Use `normal` (default), `empty`, or `error`, then restart Vite. Error retry repeats
the configured failure; change the scenario and restart to restore successful
loading. These switches are development configuration, not controls in the UI.

## Verification on 2026-10-10

- All 12 Node tests passed, including six new job-list tests.
- Production build passed without warnings.
- Edge browser checks passed for filters, sorting, pagination reset, disabled
  future actions, loading, empty list, no matches, error and retry.
- Protected route entry, refresh, sign out, Dashboard navigation, 404 safe back
  to Jobs and existing 403 recovery passed.
- Production preview list flow passed; no console errors or external requests
  were observed in the normal development and production flows.
- No horizontal overflow at 1440, 1024, 768, 390 or 320 pixels. Desktop, mobile,
  no-match, empty and failure screenshots were inspected.
- Authored frontend files and generated production files passed the Han scan.
  The repository check excluding preserved IDE metadata passed. The full check
  retains the existing `.idea/vcs.xml` language failure; that file was not edited.

The shared-header/content structure and list filtering/pagination approach were
compared with official RuoYi-Vue3 sources, without copying the complete project:
[layout](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/layout/index.vue)
and [list page](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/system/user/index.vue).
The prototype corrections govern current publication and Candidate-visible data;
the preparation document's backend instructions remain outside this phase.

C01-C03 are approved. C04 is approved; C05 is approved; C06 is approved; C07 is approved; H01 now awaits separate review.
