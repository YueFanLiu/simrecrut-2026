# Frontend Requirements Compliance Audit

Audit completed: 2026-10-11. Scope: all 31 prototype units, frontend only, using synthetic local data.

Follow-up: [Frontend Audit Remediation](frontend-audit-remediation.md) records the subsequent fixes and retest. The findings below describe the original audit state.

## Outcome

The project broadly meets the approved frontend demonstration scope. It must not be described as fully compliant with the complete production requirements. One reproducible language defect, three stale notices, an IDE language exception and several documented mock integration limits remain. This audit changes no approved product page.

## Sources and precedence

- Original 31-unit prototype compilation supplied by the user, dated 2026-10-10: all embedded page images and correction notes were inspected.
- Original Third Meeting Preparation document: frontend route table, privacy boundaries, scoring presentation and workflow requirements were compared with the implementation.
- The user's explicit frontend-only scope takes precedence over instructions in those documents. Real authentication, backend permissions, databases, HTTP APIs and model execution are outside this audit's implementation scope.
- Prototype correction notes take precedence over contradictory first-round artwork. The comparison is about required structure and behaviour, not pixel-identical reproduction of the draft images.
- The user requested an original Vue project inspired by RuoYi. The document's suggestion to reuse the full RuoYi starter is not a requirement to copy it or introduce its backend authentication.

## Verification

| Check | Observed result |
| --- | --- |
| Frontend stack | Vue 3, Vite, Element Plus, Vue Router and Pinia; shared layouts and reusable components |
| `npm test` | 143 tests passed, 0 failed, 0 skipped; rerun on 2026-10-11 |
| `npm run build` | Passed; Vite 7.3.7, 1942 modules, build completed in 5.64 seconds |
| Page coverage | All 31 units loaded in the browser and compared with the prototype/requirements |
| Responsive checks | 1440, 768, 390 and 320 pixels; 124 page/viewport checks; no document-level horizontal overflow |
| Rendered body language | No Han text in the 124 normal page states; native browser validation is a separate failing state below |
| Browser console | No warnings or errors captured for the audit tab |
| Repository checker | Failed with 24 reported lines, all in existing `.idea/vcs.xml`; no other errors reported |
| Frontend network boundary | Source search found no fetch, Axios or XMLHttpRequest calls, database password references or real JWT handling in `frontend/src` |
| Browser persistence | Remember Me stores the identifier only. Reloading a protected page returns to Login; mock session/data are memory-only |

Responsive measurements detect whole-page overflow, not every clipped child or accessibility issue. This is not a production security audit, cross-browser compatibility certification or complete permutation test of every dialog and state.

## Page matrix

Every row received four viewport checks. "Aligned" refers to the frontend demonstration scope and the limitations below.

| Unit | Route / shared entry | Required structure and observed result |
| --- | --- | --- |
| P01 Login | `/login` | Brand, identifier, password visibility, Remember Me, validation/loading and mock role navigation; aligned |
| P02 Register | `/register` | Candidate registration form and English authored validation; aligned |
| P03 Access Denied | `/403` | Recovery page and permitted workspace navigation; aligned |
| P04 Not Found | `/404` and unknown routes | Recovery page; aligned |
| C01 Published Jobs | `/candidate/jobs` | Public jobs, search/filter/sort; stale history notice remains |
| C02 Job Detail | `/candidate/jobs/:jobId` | Public version details and Start Simulation; no unsupported Save Job action |
| C03 CV Upload & Extraction | `/candidate/cases/:caseId/upload` | PDF validation, extraction stages and controlled retry; real extraction excluded |
| C04 Fact Review & Evidence | `/candidate/cases/:caseId/review` | Five professional groups, evidence, acknowledgement and confirmation lock; stale C05 notice remains |
| C05 Simulation Progress | `/candidate/cases/:caseId/progress` | Three automated stages, explicit Run and results navigation; reload recovery unavailable |
| C06 Simulation Results | `/candidate/cases/:caseId/result` | Three automated and two human slots; separate 0-100 and 0-1 scales; pending humans have no invented decision |
| C07 My Simulations | `/candidate/cases` | Owned cases, continuation, retention and confirmed demo deletion; aligned |
| H01 Job Management | `/hr/jobs` | Managed jobs and draft/version navigation; no unsupported delete/archive action |
| H02 Job Setup | `/hr/jobs/new`, edit, requirements and weights entries | Shared seven-step wizard; initial browser state and validation/publication unit tests checked |
| H03 Job Versions | `/hr/jobs/:jobId/versions` | Immutable published history and version inspection; aligned |
| H04 Authorised Cases | `/hr/results` | Read-only authorised case list; no candidate upload or grant creation |
| H05 Case Detail | `/hr/results/:caseId` | Fixed professional snapshot, versions, contributions and permitted assessment views; aligned |
| R01 My Experiments | `/research/experiments` | Authorised list, filters and detail navigation; aligned |
| R02 Create Experiment | `/research/experiments/new` | Seven required settings; fails English-only validation in a Chinese-language browser |
| R03 Experiment Detail | `/research/experiments/:experimentId` | Six tabs, controlled variants, local validation and independent review assignment; actual outputs unavailable |
| R04 Research Subjects | `/research/cases` | Reviewed profiles and authorised candidate fixtures; read-only professional details |
| R05 Models | `/research/models` | Neutral/Biased model version registry and compatible model selection; no online training |
| R06 Metrics | `/research/metrics?experimentId=EXP-001` | Frozen versions, metric/group/denominator placeholders; missing outputs explicitly Unavailable |
| V01 My Review Tasks | `/reviewer/tasks` | Assigned HR/Researcher responsibility, list and detail navigation; aligned |
| V02 Independent Review | `/reviewer/tasks/:reviewId` | Blind professional profile, Accept/Reject, reason, confirmation and immutable submission; aligned |
| A01 User Management | `/admin/users` | Synthetic directory, status controls and protected current/last Admin; aligned |
| A02 Roles & Permissions | `/admin/roles` | Four account roles and menu/action matrix, separate case grants; stale administration notice remains |
| A03 Model Versions | `/admin/models` | TESTED to APPROVED to ACTIVE to RETIRED metadata lifecycle; synthetic releases only |
| A04 Frozen Protocols | `/admin/protocols` | Read-only details and configuration, missing configuration explicit; aligned |
| A05 Service Health | `/admin/system-health` | Java, MySQL, Redis, ML and workspace checks remain Unknown without an API; aligned |
| A06 Failed Jobs | `/admin/failures` | Safe codes/stages, filters and disabled unsupported Retry; aligned |
| A07 Audit Logs | `/admin/audit` | Read-only safe metadata, applied filters, pagination and empty state; synthetic events only |

## Reproduced browser workflows

- Candidate: sign in, select Software Engineer, create a case, upload a synthetic one-page PDF, complete mock extraction, review facts, acknowledge and confirm, verify locking, continue to progress, run all three stages, inspect five result slots, open history and delete the disposable mock case with confirmation.
- HR: managed job list, existing draft wizard entry, published versions, authorised case list and professional case detail.
- Reviewer: submit blank evaluation to verify English decision/reason errors; select Accept and enter a synthetic reason; inspect confirmation; confirm submission; verify the form is replaced by locked own decision/reason and other assessments stay hidden.
- Researcher: list/detail navigation; empty experiment form submission; inspect all six detail tabs; switch research subject sources and inspect authorised professional details; inspect models and metrics.
- Admin: open every administration unit; inspect read-only protocol details; verify unknown service health and disabled failure retry; search audit events by a nonexistent actor and observe the correct empty state.
- Reload: refresh a protected page and verify return to Login, consistent with the disclosed memory-only demo.

The H02 seven-step publication flow, complete R02 creation-to-validation flow and every Admin edit/release dialog were not all rerun in this resumed browser pass. Their existing logic tests passed. The earlier overall-review document records a separate historical audit; its broader counts are not added to this run's counts.

## Findings requiring cleanup

### F01: Native validation can display Chinese

Priority: medium. File: `frontend/src/views/research/ExperimentCreate.vue`, form at line 13 and first required input at line 21.

Reproduction: sign in as the Researcher demo user, open Create Experiment, leave all seven fields empty and click Create draft experiment. The current browser displays a native Chinese required-field tooltip. This tooltip is generated by the browser and is absent from `document.body.innerText`, so a source/body character scan alone misses it.

Recommendation: use explicit English form validation, disable native constraint popups with `novalidate`, and ensure invalid submissions still cannot create drafts. Audit other native required inputs as part of that repair. Setting the document language alone does not reliably control the browser's native validation language.

### F02-F04: Stale implementation notices

Priority: low. These contradict working navigation:

- `frontend/src/views/candidate/jobs/index.vue:55`: says My simulations is coming later, although C07 exists.
- `frontend/src/views/candidate/cases/review.vue:239`: says C05 is not implemented, although progress and simulation navigation work.
- `frontend/src/views/admin/roles/index.vue:81`: says Audit Logs and later administration pages are not implemented, although A03-A07 exist.

Recommendation: replace these with accurate English demo limitations without altering the approved flows.

### F05: Strict repository language check is not clean

The checker reports 24 lines containing Han text in existing IDE metadata `.idea/vcs.xml`. The audit preserves that pre-existing changed file. Frontend source and authored runtime text pass the character scan, but the entire checked repository does not meet a literal zero-Han requirement.

Recommendation: exclude personal IDE metadata from the final submission or clean it deliberately before delivery, then rerun the full repository check. Ignored private files were not read or scanned.

## Full-document requirements not yet met by the mock design

These are implementation boundaries, not reasons to introduce backend code during this frontend audit:

1. **Professional facts are retained globally in mock state.** `frontend/src/store/modules/cases.js` keeps drafts, confirmed professional data and optional research attributes in Pinia until appropriate cleanup, deletion, logout or reload. The DOCX privacy design calls for page-scoped professional facts/evidence rather than full CV snapshots in global state. Data here is synthetic; before using real CV extraction, separate page-scoped facts from summary-only global state.
2. **Reload cannot resume a case or experiment.** Full requirements specify recovery from durable server state. The current session and mock stores clear on reload. This is disclosed in the UI and compatible with the original temporary demo scope, but does not implement production recovery.
3. **Cross-role catalogs are independent.** HR publication does not populate Candidate jobs; Candidate cases do not populate HR/Research fixtures; Admin model releases do not change Research model examples; role edits do not alter actual demo login/router roles. Individual page flows work, but they are not one connected multi-role simulation.
4. **Research outputs are unavailable.** R03 validates local configuration only. R06 does not provide evaluated metrics, predictions or group denominators. Explicit Unavailable states correctly avoid fabricating scientific evidence, but the complete experiment comparison flow is not demonstrated.
5. **Operational data is static.** A07 does not record page actions. A05 has no runtime health API, so even the separately verified Aiven connection is not reflected in that page. The frontend does not connect directly to MySQL.

## Evidence and next steps

Browser evidence was saved outside the repository in the audit artifact directory: `compliance-browser-evidence.json`, `compliance-console.json` and `compliance-results-desktop.jpg`. The JSON contains the 31 loaded units and their four measured viewport states. The synthetic PDF contains no personal information.

Recommended order: repair F01, replace the three stale notices, resolve the submission language exception, then agree whether the next milestone needs connected shared Mock Data or actual backend integration. Recheck only affected flows after those repairs. Do not interpret the passing unit tests as proof of complete requirements compliance.

Local startup: `cd frontend`, `npm ci` if dependencies are missing, then `npm run dev`. The current development server listens on `http://127.0.0.1:5173/`.
