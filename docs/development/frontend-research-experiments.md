# Previous Frontend Research Experiments

This guide records the standalone R01 before WANG integration. Its source is
archived in `frontend/reference/previous-r01/` and is no longer active. Use
[Integrated Research frontend](frontend-research-module.md) for current routes,
files, behavior and checks. The sections below are historical.

R01 is `/research/experiments`, implemented by
`src/views/research/experiments/index.vue`. H01-H05 are approved. The next unit
in the prototype is R01, not H06. Stop after R01 for review before R02.

## Reference and scope

PDF page 18 defines the authorised experiment list, statuses and frozen versions.
Its correction supersedes the illustrated creation modal, editing/deletion and
unrestricted execution. Third Meeting Preparation specifies separate R02
creation and R03 detail routes. Neither route is implemented in this unit.
Create New Experiment and View are disabled with an English availability note.

ResearchLayout follows the existing header/sidebar/content/footer structure and
reuses BrandMark and Element Plus. Desktop uses a contained scrollable table;
mobile uses cards. Summaries show ID, name, description, one job and fixed
version, subject count, creation date, status, weight configuration, protocol
and Neutral/Biased model references. Dates use UTC. Draft, Running and Completed
are fixed example states; Refresh does not execute or advance an experiment.

Local search matches name, description and ID. Status and job filters combine
with search. Sorting is by newest or oldest creation time. Pagination uses
5, 10 or 20 rows. Filter and page-size changes reset pagination; access changes
clamp the current page. Refresh keeps current filters; Reset Filters clears
them and restores newest-first sorting. Filtering covers only loaded authorised
mock summaries, not unseen server records.

## Demo data and access

Seven accessible examples include six owned experiments and one current explicit
grant. Two hidden fixtures exercise ungranted and expired access. The summary
selector requires a Researcher role plus ownership or a live grant. Job filter
options derive only from accessible summaries. Expired or revoked grants remove
rows using a one-second local clock, without API polling.

Explicit projection excludes creator identity, grants, subject records, raw CVs,
research attributes, results and model paths. Nested version references are
copied. Fixtures are independent of Candidate uploads and HR demo data.
No results, demographic effects or model calculations are generated here.

The reserved Researcher demo account accepts username `researcher` or email
`researcher@simrecrut.test`, password `SimRecrut123!`. Registration cannot shadow
that account. The temporary dashboard provides the necessary navigation entry.
Candidate and HR accounts reach Access Denied for R01. Sign out resets fixture
state; reload clears the session. Pending loads cannot restore the list after
navigation or sign out. These are frontend demonstrations, not real identity
or backend authorisation checks. Future APIs must enforce scope before returning
summaries. No backend implementation, real request or dependency was added.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/research/experiments/index.vue` | List, filters, pagination and state recovery. |
| `src/layouts/ResearchLayout.vue` | Research workspace layout. |
| `src/mock/research-experiments.js` | English fixtures, scope/projection and filtering. |
| `src/store/modules/research-experiments.js` | Session state and mock read boundary. |
| `src/mock/auth.js`, `src/store/modules/auth.js` | Reserved account and logout reset. |
| `src/router/index.js`, `src/views/DemoDashboardView.vue` | Protected route and demo entry. |
| `src/config/demo.js`, `.env.example` | Normal, empty and error scenarios. |
| `tests/research-experiments.test.js` | Scope, expiry, projection, filters and account checks. |

## Local review and verification

Run `npm run dev` from `frontend/`, then sign in at
`http://127.0.0.1:5173/login` with the Researcher account. Choose Browse research
experiments. Try combined filters, Reset Filters, sorting, pagination and
Refresh. Create and View remain disabled. Put
`VITE_DEMO_RESEARCH_EXPERIMENTS_SCENARIO=empty` or `error` in `.env.local` and
restart Vite to inspect empty/error states; restore `normal` afterwards.

All 80 Node tests and the production build pass. The six new tests verify
owned/granted scope, expiry/revocation, explicit copied projection, combined
filters and stable sorting, read-only load scenarios, logout reset, Researcher
login and reserved registration names/email.

Browser checks verify dashboard entry, disabled future actions, frozen versions,
filters/reset/sorting/pagination, retained Refresh filters, grant expiry, logout
during loading, Candidate/HR rejection, empty/error/retry, production preview
and session loss on reload. Widths are 1440, 1024, 768, 390 and 320 pixels.
There is no body overflow, console error or external request.

Repository and whitespace checks pass excluding the existing IDE metadata.
Frontend source and build text contain no Han characters. Existing
`.idea/vcs.xml` Han metadata was preserved. R02 and later units await approval.
