<title>SimRecrut — Third Meeting Preparation</title>

# Team and project purpose

| Member | Name | Main work |
|-|-|-|
| A | MouZheng  LI | Data, models and research analysis |
| B | Zheng LI | Backend, database and Objective Rule |
| C | YueFan LIU | Frontend pages and user actions |
| D | YinJI WANG | CV extraction, integration and deployment |

SimRecrut is a recruitment simulator for studying how decision methods respond to the same professional case. A Candidate chooses a job, checks the facts extracted from a CV, and receives three automated results plus two human-review results. We compare the Objective Rule, Neutral AI, Biased AI, Standard Human and Instructed-Bias Human. Controlled changes help us observe whether background information changes a result while professional qualifications stay the same.

This report defines the planned application, database and experiments. Accept and Reject are simulator results; they are not real employer hiring decisions. Model performance and proposed benefits will be measured after implementation.

Project code: [SimRecrut on GitHub](https://github.com/YueFanLiu/simrecrut-2026).

Schedule and tasks: [Live Lark Gantt chart](https://ijppwkmdd5yg.jp.larksuite.com/base/Th8abtDnEafjH0sNhLcjr8dFpzh?table=tbl1CdOED2YDYCII&view=vew2hRFW2F).

# Functional flow: people, actions and results

SimRecrut compares three automated results and two human-review conditions for the same job and professional information. These are research simulations, not real employer hiring decisions. This section describes the intended workflow and does not claim that every step is already implemented.

## People and review responsibilities

<whiteboard token="U4PBw3msThG5XHbCfIxjrQbYpNd"></whiteboard>

| Role | Main actions | Access boundary |
|-|-|-|
| Candidate | Browse published jobs; create a simulation case; upload a CV; review and confirm facts; run the simulation; see results; delete own data. | Own retained cases and public job requirements. No internal HR weights, scoring cutoffs, reviewer identities or other candidates' data. |
| HR | Create job drafts; check extracted requirements; choose required/optional criteria and mandatory conditions; set weights; publish a fixed version; inspect authorised results. | Managed jobs and explicitly authorised cases. HR does not change the research rule cutoff during a normal job edit. |
| Researcher | Select authorised cases; create controlled variants; choose compatible versions; run comparisons; assign reviews; analyse and export results. | Authorised research subjects and experiments. A Candidate case is not automatically a permanent research or training profile. |
| Reviewer | Open assigned tasks; inspect the permitted job and professional data; submit Accept/Reject and a reason. | The Reviewer must also be an authorised HR or Researcher for that case. Reviewer is a functional responsibility, not a separate identity class. |
| Admin | Manage accounts and permissions; register versions; activate compatible approved models; inspect service health, failures and audit metadata. | No Candidate professional content by default, and no manual rewriting of assessment decisions. |

Standard and Instructed-Bias are **review conditions**, not two account roles. A Reviewer may receive either condition. Candidate-visible review slots contain one primary review per condition; additional research repeats are stored separately.

A, B, C and D describe the development team's work allocation. Frontend, backend, extraction, Data/AI and storage describe implementation work or technical participants. None is an additional application user role. A swimlane may represent a person, software service or storage, but its header must say which kind it is.

## Complete online business flow

<whiteboard token="KcyCwMWJZhnEijbBXYOjo7OSp8f"></whiteboard>

The swimlane headers distinguish people from software and storage. Read the three stages in order: job preparation and extraction; confirmation and automated results; human review and deletion.

| Step and responsible participant | Input | Action and saved state | Visible output / next step | Error, retry and end condition |
|-|-|-|-|-|
| 1. Admin prepares access and models | Accounts; permissions; approved models, schemas and protocols | Grant role and resource access. Check artifacts, checksums, preprocessing, thresholds and compatibility before model activation. | Authorised portals and one active Neutral plus one active Biased model are available. | Deny unauthorised requests before execution. Block incompatible activation; correct the configuration and check again. Historical results remain unchanged. |
| 2. HR prepares a job | Title and job description; known requirements | Create a DRAFT. Extract proposed requirements. HR compares each proposal with the source, corrects errors, excludes company-only descriptions, marks required/optional, defines mandatory conditions and active assessment areas. | Reviewed Skills, Experience, Education, Languages and Projects requirements. | Missing or conflicting requirements return to HR review. Extraction failures offer a safe retry. A draft cannot be selected by Candidates. |
| 3. HR publishes | Reviewed requirements; acceptable matches; weights | Check nonnegative weights, at least one active area, active total 100%, inactive weights 0. Save fixed requirements, weights and matching-rule reference in a published job version. | Public job requirements become selectable after the save succeeds. | Invalid settings block publication; no silent weight normalisation. Database failure produces no success message. Retry publication with the same operation key. Later edits create a new version; old cases keep the original version. No automatic model retraining occurs. |
| 4. Candidate starts a case | Selected published job version | Check ownership and job state; create a case and upload session with status AWAITING_UPLOAD and a 24-hour expiry. | A case ID and upload screen. | If the selected version is unavailable, return to the job selection. Case creation and file upload are separate operations. |
| 5. Candidate uploads a CV | One PDF | Check PDF content, size at most 10 MB, at most 20 pages and no encryption/password. Store the accepted file in restricted temporary storage. Move UPLOADED to EXTRACTING. | Progress screen; active work is polled every 2 seconds. | Invalid files require a different file; repeatedly retrying the same invalid file is not useful. Upload or storage failures show a technical error, never Reject. |
| 6. System extracts facts | Temporary PDF and expected fact schema | Read embedded text; use OCR only if needed. Remove direct identifiers before external extraction. DeepSeek returns facts only. Validate JSON structure, types, allowed values and required structural fields. Store a temporary review draft with values, source excerpts, source locations and extraction version. | REVIEW_REQUIRED, with facts beside evidence. The raw CV is not exposed as a downloadable permanent file. | Use one transport/service retry for temporary network errors, 429/5xx or timeout, and one schema repair attempt. If still unsuccessful, show FAILED_RETRYABLE or FAILED_FINAL and a clear retry/replace action. No score is created. |
| 7. Candidate checks the facts | Extracted professional facts and evidence | Correct values, add missing facts and mark corrections USER_SUPPLIED. Keep optional school prestige, referral, gender and ethnicity in a separate research section. Missing optional values remain UNKNOWN and are never guessed. | An edited draft ready for confirmation. | Unresolved facts needed by active job criteria block confirmation and scoring. The Candidate corrects the fields; optional research unknowns do not block the workflow. |
| 8. Candidate confirms | Valid professional draft; optional research values; operation key | Revalidate schema and required facts. Save professional data without direct identifiers and separate research attributes in one transaction; mark CONFIRMED and set the confirmation-plus-30-day deletion deadline; commit. Only then delete raw CV and intermediate text. | Confirmed case ready to run; raw-file cleanup status is recorded. | Required fact errors return to review. Save failure stops the next step and offers retry. Duplicate confirmation returns the original outcome. Cleanup failure does not undo valid confirmation: retain saved facts and retry cleanup. |
| 9. Candidate runs the simulation | Confirmed data; saved job version and HR weights | Check authorisation, required facts, valid weights, matching rules and model/schema/protocol compatibility. Resolve and freeze both active model versions on this case. Set AUTOMATED_RUNNING. | Matching begins with fixed inputs. | Invalid inputs or incompatible versions block the run with a reason. Temporary service failures may be retried; incompatible settings need correction. Unconfirmed facts are never scored. |
| 10. Backend matches facts to the job | Confirmed professional facts; fixed job requirements | Calculate five match values and whether every mandatory condition passes. Save evidence-backed matching output. | Shared professional inputs for all three automated computations. | Matching failure leaves results incomplete. Retry the failed stage without overwriting successful saved outputs. |
| 11. System computes three results | Match values, published HR weights, mandatory outcome and fixed versions | Run Objective Rule, Neutral ML and Biased ML independently after matching. Objective applies the fixed rule cutoff and uses no research attributes. Neutral receives no research block; Python builds the all-zero mask. Biased receives the optional categorical research values using the frozen schema. | Objective fit score and decision; Neutral acceptance score and decision; Biased acceptance score and decision. | Model unavailability, invalid response or schema mismatch is a technical failure, not Reject. Each valid result is saved separately. Retry only failed work; keep prior successful results. |
| 12. System completes automated results | Saved matching and all three automated outputs | Mark AUTOMATED_COMPLETE only when all required outputs are saved. Replace the existing deadline with simulation completion plus 30 days. | Candidate sees three automated cards immediately and two human cards marked Not assigned or Pending. HR/Researcher see only their permitted detail. | Never invent a failed or missing result. Objective fit scores and ML acceptance scores have different meanings and must not be subtracted as if they shared one scale. |
| 13. Researcher assigns reviews | Authorised retained case; same professional and job versions; eligible reviewers | Create one primary Standard and one primary Instructed-Bias task for Candidate-visible results. Check reviewer access and duplicate assignments. The primary biased review uses the full configured experimental scenario. | Each assigned task starts PENDING with decision NULL. | No assignment means Not assigned. An unfinished task remains Pending, never Reject. Extra research repeats do not replace the primary Candidate result. |
| 14. Reviewer completes a task | Assigned condition, professional facts without direct identifiers, public job requirements and rubric | Open the task. Standard shows the professional case; Instructed-Bias adds the explicit research scenario. Hide automated results and other reviewers' answers. Submit Accept/Reject and a reason; save atomically and mark SUBMITTED. | A locked submitted review; its permitted decision and reason appear on the same Candidate result page. | Wrong reviewer, missing reason or invalid state prevents submission. Retry transient save errors using the same operation key. Do not show a submitted result before the save succeeds. If one person reviews both conditions, hide earlier answers and automated results until both reviews are submitted. |
| 15. Candidate and Researcher compare | Three automated results and any submitted primary human reviews | Keep five separate result slots: Objective, Neutral ML, Biased ML, Standard Human and Instructed-Bias Human. Researcher may analyse controlled pairs and group metrics with sample sizes and version information. | Completed five-result comparison, or an honest partial comparison while a human task is pending. | A disagreement is a research observation, not proof of real discrimination. A controlled pair is invalid if anything except the selected research factor changes or the Objective result changes. |
| 16. Candidate changes information | Revised professional facts | Edit the temporary draft only while REVIEW_REQUIRED. After confirmation, create a new case for revised facts, even if scoring has not started. | Results remain linked to the inputs that produced them. | Never put changed professional data beside an old result as if that result had been recalculated. |
| 17. Candidate cancels or a temporary case expires | Unconfirmed case; cancellation or 24-hour deadline | Close the session and delete the temporary draft, raw CV, extracted text and redacted text. | CANCELLED or EXPIRED; no simulation decision. | Cleanup failures stay visible to operations and are retried until temporary content is removed. |
| 18. Candidate requests deletion or retention expires | Own retained case; confirmed deletion request or 30-day deadline | Hide the case, revoke content access, cancel pending reviews and mark privacy status PURGING. Remove files, research attributes, linked case/experiment content, human reasons, matching, automated results and professional facts without direct identifiers. | After all cleanup succeeds, delete the case row and return DELETED. Keep only a non-content deletion receipt. | If any cleanup fails, the case remains inaccessible in PURGING and cleanup retries. Do not report final deletion or hard-delete its tracking row too early. Purged cases disappear from readable case history. |

## Supporting workflows

<whiteboard token="TfxIww87JhUgajbiAjqjIiAtphf"></whiteboard>

<whiteboard token="A2h0w7Wcwh9ka7bwrKijxRzdpbd"></whiteboard>

<whiteboard token="HT4OwBZ4nh6abtbeA9Xj6a2lpDb"></whiteboard>

## Research and model work remain separate

Researcher chooses authorised subjects and fixed job, weight, model and protocol versions. A controlled pair changes exactly one selected research factor while professional data and all other settings stay identical. The system validates this before execution, stores both outputs and checks Objective invariance. Group analysis includes sample counts and the model, protocol and threshold settings. Candidate-linked research content remains subject to the Candidate deletion policy.

Offline dataset preparation, label generation, model training and validation are development/research work. CV upload, ordinary simulation and HR job editing do not launch training. New model or protocol versions do not rewrite historical results.

## Common rules across all steps

Every protected request checks the logged-in account, role, resource ownership or grant, and current state. A role alone does not grant access to every case. Confirmation, publication, running, review submission and privacy deletion use operation keys so repeats do not create duplicate records. A temporary failure may be retried; invalid input must first be corrected. Unknown is not false, Pending is not Reject, and a technical failure is never a hiring decision.

The API-facing case sequence is AWAITING_UPLOAD → UPLOADED → EXTRACTING → REVIEW_REQUIRED → CONFIRMED → AUTOMATED_RUNNING → AUTOMATED_COMPLETE. FAILED_RETRYABLE, FAILED_FINAL, CANCELLED and EXPIRED are processing outcomes. Human-review state and privacy-cleanup state are tracked separately.

Removing a name does not make a retained case fully anonymous: ownership and access records can still link it to an account. Keep those links restricted and apply the same deletion policy to linked research content.

# Frontend pages, buttons and routes

<whiteboard token="DuHkwNodKhVvu9bu89mjijUjpEb"></whiteboard>

The frontend has five work areas: Candidate, HR, Research, Review tasks and Admin. These are navigation areas, not five independent account roles. A Human Reviewer is an HR or Researcher who has been assigned a review and has access to its case. Standard and Instructed-Bias are review conditions, not roles.

The frontend uses the official RuoYi-Vue3 JavaScript starter: Vue 3, Vite, Element Plus, Vue Router and Pinia. Feature API modules use Axios through the shared request client. [R25] The view files and API extensions below define the implementation plan.

## One naming scheme

Browser routes have no API prefix: /candidate/jobs. All new application HTTP calls have the prefix /api/v1: GET /api/v1/candidate/jobs. In the tables, API paths are written relative to /api/v1; that prefix must be added once by the API module/request configuration. Existing RuoYi login and account APIs keep their existing paths. The browser calls Spring Boot only.

Use these implementation locations:

| Location | Responsibility |
|-|-|
| frontend/src/router/modules/simrecrut.js | Browser routes for the five work areas |
| frontend/src/views/candidate/, hr/, research/, reviewer/, admin/ | Pages listed below, grouped by work area |
| frontend/src/api/simrecrut/candidateJobs.js; candidateCases.js | Published jobs, owned cases and Candidate actions |
| frontend/src/api/simrecrut/hrJobs.js; hrCases.js | HR job configuration, versions and authorised cases |
| frontend/src/api/simrecrut/researchExperiments.js | Research selection, experiments, assignments and metrics |
| frontend/src/api/simrecrut/reviewerTasks.js | Assigned reviews and decision submission |
| frontend/src/api/simrecrut/admin.js | Model releases, health, failures, protocols and audit |
| frontend/src/utils/request.js | Existing shared HTTP client and login token handling |

In the page tables, a view such as candidate/jobs/index.vue means frontend/src/views/candidate/jobs/index.vue. Vue Router parameters use :caseId; OpenAPI uses {caseId} for the same identifier. Browser result is singular; its HTTP endpoint results is plural. They serve different purposes and do not need identical names.

## Sign in, menus and access checks

Reuse the installed RuoYi sign-in page, token store, user/permission loading and logout action. Confirm the exact sign-in view and authentication paths in the selected RuoYi branch. /login and the existing forbidden/not-found pages should be verified against the selected RuoYi repository before wiring redirects; do not invent a second authentication API.

Proposed navigation sequence:

1. The router checks whether the user is signed in. If not, it opens the existing sign-in route and stores a safe, internal return route.
2. After sign-in, load the user's roles and permissions using the existing RuoYi flow. Build only allowed menus. A user with several responsibilities may see several areas.
3. Check route permissions before loading page data. Use permission directives to hide or disable unavailable actions. Exact permission strings are implementation choices and must match backend checks.
4. Spring Boot checks the token, role, resource access and current resource state on **every request**. A hidden menu or button does not protect an API.
5. Only after the server grants access does the page render the case, job, experiment or task. Never trust a case ID in the address bar as proof of ownership.

| Area | Menu/route requirement | Server access check | What the UI must protect |
|-|-|-|-|
| Candidate | Candidate permission | Current user owns the case; jobs are published | No other Candidate's cases, HR weights, internal thresholds, reviewer identities or raw CV |
| HR | HR permission | User manages the job or has an explicit case grant | Published job versions are read-only; no deleted identifiers or raw CV |
| Research | Researcher permission | Experiment/dataset access; explicit access for live Candidate cases | Purged cases cannot be reused; research access does not remove retention rules |
| Review tasks | HR or Researcher plus review permission | Task is assigned to current user; target still exists; case grant is valid | Before submission, server omits all automated results and other Human answers |
| Admin | Relevant Admin permission | Operation-specific administrative check | Operational access alone does not grant Candidate professional-content access |

The server takes the acting user from the security context. The frontend must not claim its identity with candidate_user_id, created_by or a trusted reviewer-user field. reviewerUserId in a Research assignment is the **target reviewer**, not the acting user's identity.

Proposed default destinations are /candidate/jobs, /hr/jobs, /research/experiments, /reviewer/tasks and /admin/system-health, depending on permissions. With several areas, show an area selector or preserve the last permitted area. Keep this a navigation choice, not a new role.

## Candidate pages

API modules: candidateJobs.js and candidateCases.js.

| Page and browser route | Proposed view file | Purpose | Main buttons/actions and next route | Backend API |
|-|-|-|-|-|
| **Find a job** — /candidate/jobs | candidate/jobs/index.vue | List published jobs with public summary, filters and pagination | **View job** → /candidate/jobs/:jobId; **My simulations** → /candidate/cases | GET /candidate/jobs?page=1&pageSize=20 |
| **Job details** — /candidate/jobs/:jobId | candidate/jobs/detail.vue | Show description and public requirements | **Start simulation** creates a case using the displayed jobVersionId; after success → /candidate/cases/:caseId/upload; **Back to jobs** → /candidate/jobs | GET /candidate/jobs/{jobId}; POST /candidate/cases |
| **Upload your CV** — /candidate/cases/:caseId/upload | candidate/cases/upload.vue | Upload one PDF to an already created case; show extraction progress | **Choose PDF** stays; **Upload** → extraction state on this page; when REVIEW_REQUIRED, **Review extracted facts** → /candidate/cases/:caseId/review; **Cancel and delete** → confirmation modal, then /candidate/cases | POST /candidate/cases/{caseId}/cv; GET /candidate/cases/{caseId}/status; DELETE /candidate/cases/{caseId} |
| **Review your CV** — /candidate/cases/:caseId/review | candidate/cases/review.vue | Correct professional facts and see short evidence/page references | **Save changes** stays; **Save optional details** stays; **Confirm and continue** → /candidate/cases/:caseId/progress after successful confirmation; **Cancel and delete** → modal, then history | GET/PUT /candidate/cases/{caseId}/draft; GET/PUT /candidate/cases/{caseId}/research-attributes; POST /candidate/cases/{caseId}/confirm; DELETE /candidate/cases/{caseId} |
| **Simulation progress** — /candidate/cases/:caseId/progress | candidate/cases/progress.vue | Show confirmed, running or failed state; resume safely after a reload | At CONFIRMED, **Run simulation** starts the three automated methods; after AUTOMATED_COMPLETE, **View results** → /candidate/cases/:caseId/result; **My simulations** → history. **Retry** needs the missing retry contract described below | POST /candidate/cases/{caseId}/run; GET /candidate/cases/{caseId}/status |
| **Your results** — /candidate/cases/:caseId/result | candidate/cases/result.vue | Show three automated results and two separate Human result cards | **Refresh Human results** stays; **Delete my simulation data** → modal → history; **My simulations** → /candidate/cases; **Choose another job** → /candidate/jobs | GET /candidate/cases/{caseId}/results; DELETE /candidate/cases/{caseId} |
| **My simulations** — /candidate/cases | candidate/cases/index.vue | List only the user's retained cases, with processing/Human status and deletion date | **Continue** or **View results** → state-appropriate route; **Start a new simulation** → /candidate/jobs; optional **Delete** row action uses the same confirmation modal | GET /candidate/cases; GET /candidate/cases/{caseId}/status when resuming; DELETE /candidate/cases/{caseId} |

**Optional details are a section, not an extra required page.** Place school prestige, referral, gender and ethnicity in a separate section on the review page labelled “Optional information for the bias simulation.” Show the value, source and UNKNOWN choice. Saving uses the separate research-attributes endpoint. UNKNOWN does not block confirmation or switch off the Biased model. The browser never computes a demographic effect or guesses a missing group.

The review page uses page-local data for skills, experience, education, languages, projects and evidence. Clear it when leaving the workflow. Do not keep the full CV snapshot in a global Pinia store. There is no permanent raw-CV download button.

Loading a route must not silently create a case. Start simulation requires a selected published jobVersionId, and the upload route always uses the ID returned by case creation.

### Candidate state and button rules

| Server state | Page behaviour | Allowed next action |
|-|-|-|
| AWAITING_UPLOAD | Upload form | Upload valid PDF, or delete/cancel |
| UPLOADED, EXTRACTING | Processing state on upload page | Poll status; do not allow duplicate upload or confirmation |
| REVIEW_REQUIRED | Review page | Save professional facts/optional details, confirm, or delete |
| CONFIRMED | Progress page with a clear ready state | Run simulation; do not reopen the editable temporary draft |
| AUTOMATED_RUNNING | Progress page | Poll status; no duplicate run action |
| AUTOMATED_COMPLETE | Result page | View/refresh results or delete; Human cards can still be pending |
| FAILED_RETRYABLE | Error panel at the failed stage | Offer retry only when a defined server operation supports that stage |
| FAILED_FINAL | Error panel | Explain the input/service problem; start a new case if appropriate |
| CANCELLED, EXPIRED, inaccessible/purged case | Stop polling; clear page-local case data | Return to history or job list |

Poll active processing every two seconds. Stop the active timer on REVIEW_REQUIRED, completion, failure, cancellation, expiry and component unmount; stop it at CONFIRMED until Run starts. stage gives the processing detail; processingStatus controls the main workflow. “READY_FOR_REVIEW” can be a stage hint; it does not replace the canonical status REVIEW_REQUIRED.

Confirmation and running are separate API calls. If a future one-button “Confirm and run” is used, wait for successful confirmation before calling Run. If Run fails, the already confirmed snapshot remains valid. Do not upload or confirm again automatically.

Show PDF-only, 10 MB, 20 pages and no encrypted PDF before upload. Client checks are helpful, but server file/content checks remain authoritative. Invalid files and extraction failures are errors, never a Reject result.

The result page uses these exact labels: **Objective Fit Score**, **Neutral ML Acceptance Score**, **Biased ML Acceptance Score**, **Standard Human Decision**, **Instructed-Bias Human Decision**. Label the Objective display scale 0–100 and ML scales 0–1. Do not subtract scores on different scales. A model score is not a real hiring probability.

Delete requires a confirmation modal. Explain that structured case/results will be removed and pending reviews cancelled. On DELETED or PURGING, remove the case from normal navigation and clear cached content. A pending privacy purge is not a readable historical case.

## HR pages

API modules: hrJobs.js and hrCases.js. The browser page /hr/results reads the API resource /hr/cases; do not use the older conceptual HTTP /hr/results.

| Page and browser route | Proposed view file | Purpose | Main buttons/actions and next route | Backend API |
|-|-|-|-|-|
| **Jobs** — /hr/jobs | hr/jobs/index.vue | List jobs the HR user manages | **New job** → /hr/jobs/new; **Edit draft** → /hr/jobs/:jobId/edit; **Versions** → /hr/jobs/:jobId/versions; **Case results** → /hr/results | GET /hr/jobs |
| **New job** — /hr/jobs/new | hr/jobs/wizard.vue | Enter title/description and create a draft | **Create draft** → /hr/jobs/:jobId/edit; **Cancel** → /hr/jobs | POST /hr/jobs |
| **Job setup** — /hr/jobs/:jobId/edit | hr/jobs/wizard.vue | Shared seven-step wizard: description, extracted requirements, HR review, mandatory checks, weights, validation, publish | **Save draft** stays; **Extract requirements** starts extraction; **Next** → /hr/jobs/:jobId/requirements; **Validate** stays with errors/warnings; **Publish** → versions after success | PUT /hr/jobs/{jobId}; POST /hr/jobs/{jobId}/extract-requirements; POST /hr/jobs/{jobId}/validate; POST /hr/jobs/{jobId}/publish. Draft reload API is missing; extraction-status needs adding to the machine-readable HTTP specification |
| **Requirements** — /hr/jobs/:jobId/requirements | hr/jobs/wizard.vue with requirements step | Review the five groups, mandatory flags and public visibility; show evidence when supplied | **Save requirements** stays; **Back** → edit; **Next: weights** → /hr/jobs/:jobId/weights | GET/PUT /hr/jobs/{jobId}/requirements |
| **Weights** — /hr/jobs/:jobId/weights | hr/jobs/wizard.vue with weights step | Set five HR weights; show total as a percentage | **Save weights** stays; **Back** → requirements; **Validate** stays and opens final wizard steps; **Publish** → versions | GET/PUT /hr/jobs/{jobId}/weights; POST /hr/jobs/{jobId}/validate; POST /hr/jobs/{jobId}/publish |
| **Job versions** — /hr/jobs/:jobId/versions | hr/jobs/versions.vue | List immutable published versions and the current draft | **View version** opens a read-only panel; **Create new draft** → edit only after a new-draft API succeeds; **Back to jobs** → /hr/jobs | GET /hr/jobs/{jobId}/versions. Full version detail and clone-to-draft APIs are missing |
| **Case results** — /hr/results | hr/cases/index.vue | List cases for managed jobs or explicit grants | **Open case** → /hr/results/:caseId; **Refresh** stays; job filter stays in query | GET /hr/cases |
| **Case details** — **Proposed** /hr/results/:caseId | hr/cases/detail.vue | Show professional snapshot without direct identifiers, matching values, HR weights, contributions, three automated scores, Human status and version references | **Back to results** → /hr/results; **Refresh** stays. Do not add a score-edit button | GET /hr/cases/{caseId} |

The requirements and weights URLs select steps in one wizard; they are not separate copies of the job editor. Validation and publishing can be panels in the wizard, so no new /validate or /publish browser pages are needed.

Store/send weights as fractions whose total is 1. Show the same values as percentages totalling 100%. Reject negative values, all-zero values and invalid totals; do not silently normalize. Disable Publish until the server reports a valid job, and revalidate on publication. Mandatory rules and unresolved requirements are also server checks.

Published versions remain read-only. “Edit” must create a new draft rather than update the published version. The existing PUT /hr/jobs/{jobId} only edits a draft; it does not define the clone operation. Send draft revision on supported updates and show a reload/compare choice on a stale-edit conflict. The completion contracts below define the full-draft and version-detail reads needed for direct links.

## Research pages

API module: researchExperiments.js. Candidate content keeps its deletion deadline when used in research. It does not enter the training dataset.

| Page and browser route | Proposed view file | Purpose | Main buttons/actions and next route | Backend API |
|-|-|-|-|-|
| **Experiments** — /research/experiments | research/experiments/index.vue | List authorised experiments | **New experiment** → /research/experiments/new; **Open** → /research/experiments/:experimentId | GET /research/experiments |
| **New experiment** — /research/experiments/new | research/experiments/create.vue | Choose type, job version, weights, protocol, models, subjects and one controlled attribute | **Create experiment** creates configuration, then → detail; subject/variant steps continue there once an ID exists. **Cancel** → experiment list | POST /research/experiments; selection/read APIs for jobs, models, protocols and profiles need completing |
| **Experiment details** — /research/experiments/:experimentId | research/experiments/detail.vue | Configuration, subjects, variants, running status and controlled comparison in tabs | **Add subjects** stays; **Create variants** stays; **Run experiment** stays while running; **Compare variants** opens comparison tab; **Assign reviewers** opens assignment panel; **View metrics** → /research/metrics?experimentId=:experimentId; **Back** → experiments | GET /research/experiments/{experimentId}; POST .../{experimentId}/subjects; POST .../{experimentId}/variants; POST .../{experimentId}/run; POST .../{experimentId}/reviews needs adding to the machine-readable HTTP specification |
| **Research cases** — /research/cases | research/cases/index.vue | Select reviewed research profiles or explicitly authorised retained Candidate cases | **View case** opens a read-only panel; **Add to experiment** → experiment detail after subject addition; **Assign primary reviews** stays with assignment confirmation | POST /research/experiments/{experimentId}/subjects; POST /research/cases/{caseId}/reviews. List/detail/eligible-reviewer APIs are missing |
| **Model information** — /research/models | research/models/index.vue | Read compatible model/schema/protocol metadata for experiment selection | **View model** opens read-only panel; **Use in new experiment** → /research/experiments/new with a checked selection | Research-readable model API is missing. Do not call Admin-only model endpoints merely to populate this page |
| **Experiment metrics** — /research/metrics | research/metrics/index.vue | Show performance, group counts, acceptance rates, TPR/gaps, Top-K and score distributions for a selected experiment | **Select experiment** stays with ?experimentId=...; **Refresh** stays; **Back to experiment** → /research/experiments/:experimentId | GET /research/experiments; GET /research/experiments/{experimentId}/metrics |

Use experimentId consistently in browser routes and HTTP requests. The experimentId query on the existing metrics route is a **proposed navigation detail**, not a new API.

Create requests must include name, experimentType, jobVersionId, weightConfigCode, protocolVersion, neutralModelVersion and biasedModelVersion, in the create request. These fields are required to restore a complete frozen experiment. Core meeting flow uses controlled pairs, group comparison and Top-K. Bonus types listed in OpenAPI should be shown only when their actual server/model support is enabled.

The comparison tab shows the same professional facts, job, weights and model versions on both sides, then highlights the one changed research attribute. More than that controlled change makes the pair invalid. Show server-reported compatibility errors before running. There is no dedicated experiment-validation API yet: local checks may explain obvious form problems, but the server must validate creation, variants and Run. A green UI badge alone cannot certify the experiment.

Assignment distinguishes **PRIMARY** from **RESEARCH_REPLICATE**. The Candidate sees at most one Primary Standard and one Primary Instructed-Bias result. Research replicates are not substituted for those main cards. The Candidate-facing Instructed-Bias assignment uses the complete scenario. Research pairs may isolate one factor.

Always show group sample counts, model/protocol references and the threshold condition with metrics. The metrics response needs typed fields for these values. Do not fabricate missing counts or show an undefined metric as zero. Export, delete-experiment and edit-experiment buttons are not in the current HTTP contract and are not part of the baseline page.

## Assigned Human review pages

API module: reviewerTasks.js. The same route and view support both conditions. The server supplies the assigned condition and permitted information.

| Page and browser route | Proposed view file | Purpose | Main buttons/actions and next route | Backend API |
|-|-|-|-|-|
| **Review tasks** — /reviewer/tasks | reviewer/tasks/index.vue | List only current user's assigned tasks, with job, condition, status and date | **Open task** → /reviewer/tasks/:reviewId; **Filter status** stays; **Refresh** stays | GET /reviewer/tasks?status=PENDING&page=1&pageSize=20 |
| **Review a case** — /reviewer/tasks/:reviewId | reviewer/tasks/detail.vue | Show professional facts, public job requirements and rubric; add controlled attributes/instructions only for the assigned Instructed-Bias condition | **Accept** or **Reject** selects a decision; **Submit decision** stays and switches to submitted/read-only state; **Back to tasks** → /reviewer/tasks | GET /reviewer/tasks/{reviewId}; POST /reviewer/tasks/{reviewId}/submit |

Require a decision and a reason of 1–2000 characters. The frontend does not send a new condition or reviewer identity with submission. The backend verifies assignment, access, target existence and PENDING/OPENED state again. On deleted/expired targets, stop and return to tasks; do not submit a Reject automatically.

Before completing the assigned conditions, do not request or render Objective, Neutral, Biased or other Human results. If one reviewer has both conditions for a target, apply this restriction to both submissions and every alternate result endpoint. This must be enforced by the response DTO, not by hiding HTML already containing the data. After submission, the normal review is immutable. Show the saved decision and timestamp. The current contract does not guarantee a post-submit comparison response, so do not promise an automatic reveal of all AI scores.

## Admin pages

API module: admin.js for SimRecrut operations. User/role management reuses the existing RuoYi API modules and components.

| Page and browser route | Proposed view file or reuse | Purpose | Main buttons/actions and next route | Backend API |
|-|-|-|-|-|
| **Users** — /admin/users | admin/users/index.vue thin wrapper, or installed RuoYi user view | Manage accounts using existing account rules | **Search**, **Add user**, **Edit**, **Enable/disable** use existing RuoYi dialogs and permissions; **Roles** → /admin/roles | Existing RuoYi user APIs; confirm exact paths in the selected RuoYi branch |
| **Roles and permissions** — /admin/roles | admin/roles/index.vue thin wrapper, or installed RuoYi role view | Manage allowed actions and menu permissions | **Edit permissions**, **Assign role**, **Save** stay; **Users** → /admin/users | Existing RuoYi role APIs; verify against the selected repository |
| **Models** — /admin/models | admin/models/index.vue | List versions, type, status, schema, protocol, cutoff and evaluation information | **View model** opens detail panel; **Activate** stays after server checks; **Retire** is unavailable until its contract is completed | GET /admin/models; POST /admin/models/{modelVersion}/activate; model detail needs a complete machine-readable HTTP definition; retirement is a gap |
| **Protocols** — /admin/protocols | admin/protocols/index.vue | Read frozen protocol summaries | **View protocol** opens read-only panel; **Refresh** stays. No Edit Frozen button | GET /admin/protocols; GET /admin/protocols/{versionCode} needs a complete machine-readable HTTP definition |
| **System health** — /admin/system-health | admin/system-health/index.vue | Show backend, MySQL, Redis, ML service, loaded model references and temporary-workspace check | **Refresh** stays; **View failures** → /admin/failures; **Models** → /admin/models | GET /admin/system-health |
| **Failures** — /admin/failures | admin/failures/index.vue | Show resource ID, stage, safe error code, retryability, time and cleanup state | **Refresh** stays; **View technical details** opens a metadata panel; **Audit** → /admin/audit | GET /admin/failures. A manual retry operation is not defined |
| **Audit log** — /admin/audit | admin/audit/index.vue | Search non-content events by action, actor, type, time and status | **Search**, **Clear filters**, **Refresh** stay | GET /admin/audit needs adding to the machine-readable HTTP specification |

Model activation requires approval, artifacts/checksum, schema, protocol, cutoff and compatibility checks. It must not alter historical case results. Training happens offline. Registration records an already prepared package; approval and activation are separate actions. Health and failure pages do not include Candidate professional facts, research attributes, raw CV text or Human reasons by default.

## Shared action behaviour

Use the shared request client, but adapt it explicitly to SimRecrut's success envelope: code = 0, message, requestId, data. Preserve the existing login/account response handling. Do not assume the installed RuoYi success code equals the new API code. Async operations return HTTP 202; the UI enters a processing state rather than showing completion.

Use one idempotency key per logical confirmation, run, publication, review submission, deletion, experiment run or model activation. Reuse the same key for an unchanged request retry. A changed payload needs a new key. Disable duplicate clicks while pending. Case creation has no declared idempotency header, so do not silently repeat a failed create request as if it were guaranteed safe.

| Response | UI action |
|-|-|
| 401 | Clear protected page data and reuse the existing sign-in flow |
| 403 | Show access denied; do not fall back to another role's endpoint |
| 404, retention/purge access error | Clear stale content and offer the relevant list page |
| 409 state/version conflict | Explain the conflict; reload current state before another edit/run |
| 422 validation failure | Keep entered values and show errors beside the affected fields |
| 503 or retryable=true | Explain service failure; offer only a supported retry action |

Use text and icons as well as colour for Accept, Reject, Pending and Error. Keep error requestId available for support without displaying internal stack traces. Protected data should not survive logout, role changes or a failed access recheck. Browser back-navigation must reload/recheck sensitive resources instead of exposing stale cached case content.

## APIs to complete before page integration

The following operations are proposed additions, with concrete paths and access rules. They close reload, selection, retry and model-retirement gaps. They must be added to the HTTP specification and implemented before the corresponding buttons are enabled.

### Proposed API extensions: concrete integration targets

Every row below is a **proposed API extension**. All paths include the full prefix; these are concrete integration targets to implement.

| Proposed API extension | Page/button supported | Minimum request/response and access rule |
|-|-|-|
| GET /api/v1/hr/jobs/{jobId} | Reload Job setup | Return editable draft title, description, status, revision and relevant version IDs; managed-job access required |
| GET /api/v1/hr/jobs/{jobId}/versions/{jobVersionId} | View version | Return immutable version configuration, reviewed requirements and weights; verify the version belongs to this managed job |
| POST /api/v1/hr/jobs/{jobId}/drafts | Create new draft | Send sourceJobVersionId; return the new draft ID and revision. If one editable draft already exists, return 409 with its ID; never create a second editable draft. Use an idempotency key |
| POST /api/v1/candidate/cases/{caseId}/retry | Retry failed processing | Empty body; server chooses the resumable failed stage from stored state; owner only; idempotent; never reset a completed decision or manufacture missing input |
| GET /api/v1/research/job-versions | Choose job version | Return research-authorised published version IDs, public title and matching-rule reference; do not reuse Candidate role access implicitly |
| GET /api/v1/research/models | Model information / model picker | Return permitted version, type, schema, protocol, cutoff and evaluation summary; read-only Research permission |
| GET /api/v1/research/protocols | Protocol picker | Return permitted frozen protocol references and compatibility metadata; read-only Research permission |
| GET /api/v1/research/profiles | Choose persistent research subjects | Paged reviewed-profile summaries; dataset access checked on the server |
| GET /api/v1/research/profiles/{profileId} | Read research profile panel | Return reviewed, permitted research data; no live-Candidate ownership bypass |
| GET /api/v1/research/cases | Research cases list | Paged summaries of explicitly authorised, retained Candidate cases; omit purging/purged content |
| GET /api/v1/research/cases/{caseId} | Read authorised case panel | Return only permitted structured content and current review summaries; recheck retention and grant |
| GET /api/v1/research/cases/{caseId}/eligible-reviewers | Assign Primary reviewers | Return eligible HR/Researcher account IDs and safe display names for this target; do not expose the unrestricted account directory |
| GET /api/v1/research/experiments/{experimentId}/eligible-reviewers | Assign research reviewers | Return eligible reviewers within the experiment/subject access scope; final assignment must recheck eligibility |
| POST /api/v1/research/experiments/{experimentId}/validate | Validate experiment before Run | Optional preflight; return compatibility/invariant errors and warnings; Run still repeats validation |
| POST /api/v1/admin/models/{modelVersion}/retire | Retire model | Admin only; idempotent; define replacement/availability checks for an active core model; preserve historical results |

The existing experiment GET should be expanded to return configuration and authorised subject/variant/result/review summaries. That is a response-schema extension to an existing route, not another proposed browser page. The schemas should provide exact field names before frontend code relies on them.

The HTTP specification also needs complete definitions for HR extraction status, experiment review assignment, Admin model/protocol detail and audit reads. Expand experiment reads with frozen configuration, subjects, variants and review summaries; add safe requirement evidence and role-specific result fields. Responses for metrics must carry denominators, versions and cutoffs. Candidate result responses need a public job summary. These are integration requirements, not implemented features.

For the safe Admin retry shown in the workflow, add proposed POST /api/v1/admin/failures/{taskId}/retry: require operation-specific Admin permission, look up the recorded task, reject non-retryable or completed work, reuse its fixed versions and resume only its failed stage. Record a non-content audit event. Keep the Retry button unavailable until this operation is implemented; an operational permission does not grant unrestricted CV access.

## Complete model-release and page contracts

| API or field addition | Required behaviour |
|-|-|
| POST /api/v1/admin/models | Register metadata for an offline model package placed in the restricted artifact store: version, type, artifact location/checksum, schema, preprocessing, protocol, training run, cutoff and evaluation report. Validate the server-side path and hash; verify the offline validation and final-test reports, then return model version and TESTED status. Reject incomplete or failed packages. Use Admin model-write permission and an idempotency key. |
| POST /api/v1/admin/models/{modelVersion}/approve | Require TESTED status, read the recorded checks and evaluation evidence, reject incompatible/incomplete packages, record the approving user/time and return APPROVED status. Approval does not make the model active. |
| POST /api/v1/admin/models/{modelVersion}/activate | Preflight inference readiness, then switch the active core slot atomically. Return ACTIVE status. Preserve the versions used by existing cases. |
| POST /api/v1/admin/models/{modelVersion}/retire | Reject retirement of an active core version unless a compatible replacement is activated atomically. Retire metadata; preserve referenced artifacts and results. |
| GET /api/v1/hr/jobs/{jobId}/extraction-status | Return jobId, revision, processing status, stage, safe error code/message, retryable and updatedAt. HR owns the draft. |
| Job requirement fields | Add assessmentIncluded, evidenceExcerpt, evidenceLocation and structured warnings/conflicts. mandatory is a separate gate; publicVisible controls Candidate display. Add activeCriteria to the fixed job configuration. |
| GET /api/v1/admin/audit | Paged safe events; defined filters actionCode, resourceType, actor, from, to and outcome. Return no CV facts, research values or human reasons. |
| POST /api/v1/research/experiments/{experimentId}/reviews | Each assignment identifies variantId, condition, purpose and eligible reviewerUserId. Research repetitions are not Candidate-visible Primary reviews. Return count and assigned review IDs. |
| Search controls | Add keyword to job-list contracts and jobId to HR case filters if those controls are shown. For small already loaded lists, label local filtering clearly; it does not search unseen pages. |

The Admin Models page has Register package, View, Approve, Activate and Retire actions. Registration uses recorded package metadata, not a training or arbitrary-file execution action. Buttons follow the model state and the server permission. Retrying a failed background task returns HTTP 202 with taskId, resourceId, stage and queued/running status; it does not claim the task has finished.

## Interface fields and permitted views

The following tables define what each page can receive. Baseline field names are retained; completion fields make the contracts explicit before page integration. Required success fields must appear in the HTTP schema. Public responses are built from allowed fields rather than returning database rows.

### Shared response and identity rules

| Interface item | Existing fields and exact meaning | Minimum completion | Visibility |
|-|-|-|-|
| BaseEnvelope | code, message, requestId are required; operation data is declared separately | Require the operation's data on successful responses. SimRecrut success code is zero; do not reuse an incompatible RuoYi success-code assumption | Each caller sees its own response metadata |
| ErrorResponse | code, message, requestId, retryable are required; details is optional/free-form | Define safe detail fields per error stage; no stack traces, SQL, CV text or secrets | Only the current operation's safe error |
| Paged list | rows, total, page, pageSize exist in the Candidate/HR paged envelopes | Make pagination metadata required for those lists; not every Research/Admin list currently uses this envelope | Rows must be filtered by server access |
| Public IDs | jobId, caseId, experimentId and reviewId reference the ULID schema | Validate path IDs and resource relationship; never treat knowing an ID as authorisation | Only permitted resources |
| Acting identity | Authentication token, resolved in Spring security context | Never accept a request's candidate/reviewer/creator value as proof of the caller's identity | Server-owned; reviewerUserId in assignment identifies the target reviewer |
| Idempotency | Idempotency-Key is a required header on declared repeat-safe operations | Scope keys to user, resource and operation; unchanged retry returns the original outcome | Key is request metadata, not a user ID |

### Candidate and HR data

| DTO/interface | Existing minimum fields | Requiredness/gap | Visible to |
|-|-|-|-|
| JobSummary | jobId, title, currentVersion, summary, publishedAt | All declared but not required in the baseline contract; jobId/title/currentVersion are minimum list-navigation data | Candidate for published jobs; HR endpoint must filter managed jobs |
| JobDetailEnvelope data | jobId, jobVersionId, version, title, description, requirements | Make these required for a successful detail load; requirements is currently untyped | Candidate sees public requirements only, no weights or cutoffs |
| CreateCaseRequest | jobVersionId | Required; extra properties disallowed | Candidate submits published version only |
| CreateCaseData | caseId, processingStatus, expiresAt | All required; processingStatus is AWAITING_UPLOAD. Use this processingStatus field consistently | Owner Candidate |
| CV upload | file multipart part | Required; PDF constraints are validated on server | Owner's upload; raw file is not returned |
| CaseStatusEnvelope data | caseId, processingStatus, stage, retryable, updatedAt | Fields declared, not required. Add safe failure information as an explicit extension | Owner Candidate; no HR weights/research content |
| CaseSummary | caseId, jobTitle, processingStatus, humanStatus, createdAt, purgeAt | Require navigable ID/state/title; purgeAt may be null before the retention deadline is set | Owner or authorised HR list, depending on endpoint |
| ReviewDraftEnvelope data | caseId, professional, unresolvedRequiredFields | professional is free-form; define typed skill/experience/education/language/project fields. EvidenceField exists but is not linked into a full professional schema | Owner Candidate while REVIEW_REQUIRED |
| EvidenceField | value, status, evidenceExcerpt, evidenceLocation | Status enum includes OBSERVED, DERIVED, USER_SUPPLIED, UNKNOWN, CONFLICT; snippets must exclude direct identifiers | Candidate's reviewed draft; do not expose full CV text |
| UpdateDraftRequest | professional | Required, but inner fields are free-form; add strict professional schemas and edit-state checks | Owner Candidate; corrections become USER_SUPPLIED |
| ResearchAttribute | type, value, source, candidateConfirmed | type/value/source required; candidateConfirmed optional. Type enum covers school, referral, gender and ethnicity; value is currently unrestricted text | Owner may see own values/source. HR/Research only where specifically permitted; never Objective/Neutral input |
| UpdateResearchAttributesRequest | attributes; each item type and value | Required. Server assigns source; client does not supply trusted provenance. Define family-specific value enums | Owner Candidate, before confirmation |
| Confirm response | caseId, processingStatus | processingStatus must be CONFIRMED; response properties should be required | Owner Candidate; no internal snapshot IDs needed |
| FiveResultsEnvelope data | caseId, automatedStatus, objective, neutralMl, biasedMl, human, privacy | Add permitted job summary; define when each result can be absent/null instead of inventing partial decisions | Owner Candidate |
| Objective visible result | score, displayScore, decision, strengths, gaps | score is 0–1; displayScore is 0–100; require score/decision only when successful | Candidate; no internal weights, cutoff or contribution vector |
| MlVisibleResult | acceptanceScore, decision, label, attributeSummary | Score is 0–1. attributeSummary belongs to the Biased view and must remain absent from Neutral | Candidate sees own public simulation score/decision; not logit, model cutoff or artifact path |
| HumanResult | status, decision, reason | Pending/unassigned means null decision and reason. Require status. Do not expose reviewer identity | Candidate-visible Primary results only |
| Privacy result | purgeAt and canDeleteNow; DeleteCaseEnvelope status | Delete status is DELETED or PURGING; never claim final deletion for PURGING | Owner Candidate |
| CreateJobRequest | title, description | Both required; no PDF part or job-file field | HR with create permission |
| UpdateJobRequest | title, description, revision | Inherits required title/description; revision is currently optional but should be required for protected stale-edit handling | HR managing an editable draft |
| JobRequirement | criterionType, requirementCode, payload, mandatory, publicVisible | All required. payload needs criterion-specific validation. Assessed inclusion/active flags/evidence are extensions | HR; Candidate gets only a public projection |
| JobWeights | skills, experience, education, languages, projects | All required; each 0–1; enforce total one and active/inactive rules in the service | HR and authorised Research; never Candidate public detail/results |
| JobValidationEnvelope data | valid, errors, warnings; errors contain field/code/message | Require valid and arrays; make error-field shape explicit | Managing HR |
| JobVersionsEnvelope data | jobVersionId, version, status, publishedAt | Summaries only, not full version configuration | Managing HR |
| HrCaseDetailEnvelope data | Currently only a free-form object | Proposed typed minimum: caseId, job/version references, professional snapshot, match values, mandatory result, HR weights/contributions, three automated results, Human status and recorded versions | Authorised HR case access; no raw CV, deleted identifiers or model files |

### Research, Reviewer and Admin data

| DTO/interface | Existing minimum fields | Requiredness/gap | Visible to |
|-|-|-|-|
| CreateExperimentRequest | name, experimentType, jobVersionId, weightConfigCode, protocolVersion, neutralModelVersion, biasedModelVersion | All seven required. controlledAttribute/customWeights exist but need conditional requirements for their experiment/configuration types | Authorised Researcher |
| ExperimentData | experimentId, status | Only two fields defined, neither required; cannot support configuration reload by itself | Authorised experiment users |
| Proposed experiment detail extension | Existing identifiers plus frozen job, weights, protocol and model references; subjects, variants, results and review summaries | New nested fields require precise schemas. Include each variant's ID and changed attribute so assignment/comparison can target it | Authorised Researcher; omit inaccessible/purged Candidate content |
| AddExperimentSubjectsRequest | researchProfileIds, candidateCaseIds | Arrays exist but neither required; require at least one nonempty valid subject set and specify mixed-set behaviour | Researcher with target and source access |
| GenerateVariantsRequest | attribute, values | Both required; at least two values; values need family-specific validation | Authorised Researcher |
| CountEnvelope data | count | Baseline contract name is count, not createdVariants | Caller of subject/variant/assignment operation |
| ExperimentMetricsEnvelope data | Free-form object only | Proposed typed minimum: experimentId, versions, threshold condition, reference-label definition, group label, n, selected count/rate, qualified count, true positives and TPR; pair count/flip count; Top-K K/pool/selected counts. Null plus a reason for undefined metrics | Authorised Researcher; aggregate exports must respect deletion/access rules |
| AssignCaseReviewsRequest | assignments; optional instructedBiasScenario.instructionCode | assignments required; each needs condition, purpose, reviewerUserId, candidateVisible. Validate one Primary per case/condition, eligible reviewer and full primary biased scenario | Researcher assigning the target case |
| Proposed experiment-review assignment | variantId plus condition, purpose, reviewerUserId and candidateVisible per assignment | Add variantId to the assignment schema of this completion route | Authorised Researcher; research replicates are not Candidate primary results |
| Reviewer task list item | reviewId, condition, status, jobTitle, assignedAt | All declared; define required fields. No automated results | Assigned HR/Researcher only |
| ReviewerTaskEnvelope data | Free-form object only | Proposed typed minimum: reviewId, condition, status, permitted professional facts, public job requirements, rubric, allowed instruction/scenario and retained-target status | Assigned reviewer with current target access; no automated or other-review answers before the study permits them |
| SubmitReviewRequest | decision, reason | Both required; decision ACCEPT/REJECT; reason 1–2000 characters | Assigned reviewer; no condition or reviewer identity supplied by the caller |
| SubmittedReviewEnvelope data | reviewId, status, decision, submittedAt | status SUBMITTED; make required. Saved reason may be returned as a defined extension or retained from the accepted request | Assigned reviewer; Candidate sees only its permitted Primary projection |
| ModelData | version, type, status, featureSchema, protocolVersion, threshold | Declared, not required. Evaluation/check/approval details are missing; require those when the UI promises to show them | Admin; Research needs its own authorised read projection |
| ProtocolListEnvelope | Array of free-form objects | Define versionCode, status and safe rule/schema/bias-protocol references; detail route also needs schema | Admin; research-readable projection is proposed |
| HealthEnvelope data | backend, mysql, redis, mlService, activeNeutralModel, activeBiasedModel, tempWorkspaceWritable | Require component status keys, with explicit absent/unavailable model state | Admin operations; no secrets or case content |
| FailureListEnvelope | Array of free-form objects | Proposed taskId, resourceId/type, stage, errorCode, retryable, status, occurredAt and cleanupStatus. taskId must identify the exact retryable task | Admin operations only; no CV content/Human reason |
| Proposed retry result | taskId, resourceId, stage, status, retryable, requestId | Define before enabling Admin Retry; report scheduled/running separately from successful completion | Permission for the specific operation |
| Audit read | Prose-only route | Proposed typed items: actionCode, actor reference, resource type/ID, time, outcome and safe metadata; no content snapshots | Admin audit permission |

### Internal model-service contract

These fields come from the internal service design; the public HTTP schema does not define the internal FastAPI routes. Keep this distinction visible in the implementation checklist.

| Internal message | Exact field names in the transport contract | Validation/visibility |
|-|-|-|
| Neutral request | requestId, caseId, modelVersion, featureSchemaVersion; professional containing m, w, mw, g | Arrays each length five; finite values, valid ranges and weight sum; weighted values match products within tolerance; g is 0 or 1. No research block is accepted |
| Biased request | Same professional/identity fields; research containing schoolPrestige, referral, gender, ethnicity | Python encodes the saved 16-category order; missing values are explicit UNKNOWN, never inferred demographics |
| Prediction response | requestId, caseId, modelVersion, featureSchemaVersion, logit, acceptanceScore, modelThreshold, decision, commonThreshold, commonThresholdDecision, status | Validate identity/version/schema/range/status before persistence. Candidate projection removes logit and thresholds; HR/Research visibility depends on the authorised projection |
| Transport naming | acceptanceScore | Use camelCase for every HTTP field. Python may use local snake_case names, but its serializer emits only the defined transport names. |
| Category encoding | Transport value MENA means middle_eastern_or_north_african | Map MENA to the fifth ethnicity position in the saved order. Validate each family against its frozen enum; unknown is explicit UNKNOWN. |

## Nested fact and requirement fields

These are explicit completion schemas. Define these fields in the shared HTTP types and the versioned fact schema; do not leave professional or requirement payloads as unrestricted objects.

| Record | Fields and meaning | Validation |
|-|-|-|
| Professional profile | skills, experience, education, languages and projects arrays | No name, contact details, photograph, birth date, exact address or runtime path. Empty/unknown and confirmed absence have different statuses. |
| Skill item | skillCode; EvidenceField for the observed wording | Normalise through the frozen alias table. One skillCode contributes once. |
| Experience item | roleFamilyCode, startMonth, endMonth, isCurrent, supportedSkillCodes; reviewedRelevantYears when only a duration is known | Months use YYYY-MM. Current intervals end at the saved assessment reference month. Merge overlapping relevant periods; duration is months divided by 12. A reviewed duration cannot be added again to the same dated period. |
| Education item | degreeLevelCode, subjectCode; EvidenceField for each | Codes must occur in the frozen degree/subject equivalence tables. School category is kept only in separate research attributes. |
| Language item | languageCode, levelCode; EvidenceField | Reviewed level order and equivalence mapping. Ambiguous terms such as fluent remain unresolved until reviewed; do not invent a CEFR level. |
| Project item | projectId, supportedConditionCodes, supportedSkillCodes; short evidence references | A project condition contributes once regardless of the number of examples. Do not preserve an unrestricted CV paragraph as project metadata. |
| Requirement common fields | criterionType, requirementCode, assessmentIncluded, mandatory, publicVisible, payload; safe evidence and warnings | Inclusion, mandatory and visibility are independent. One criterion/requirement code per job version. |
| Skills payload | assessedSkillCodes | A nonempty reviewed set if Skills is active. Allowed equivalents come from the matching-rule version. |
| Experience payload | targetYears, relevantRoleFamilyCodes; minimumYears if a mandatory minimum is used | Positive targetYears; relevant years follow the reviewed job-family map. A mandatory minimum is checked separately from the capped score. |
| Education payload | minimumDegreeLevelCode; acceptedSubjectCodes or unrestrictedSubject flag | Valid degree ordering; no prestige or institution-name scoring. |
| Languages payload | Required language items, each with languageCode and minimumLevelCode | Nonempty when active; no duplicate language. |
| Projects payload | assessedConditionCodes | Nonempty reviewed conditions when active; matches use evidence, not a general subjective project-quality score. |
| Fixed job configuration | activeCriteria, five named weights, matchingRuleVersion, reviewed requirements and revision | At least one active area; inactive weights zero; active weights sum to one. Published values are immutable. |

Add the metadata field assessmentReferenceMonth, in YYYY-MM format, and freeze it at confirmation. Offline corpora also save a fixed reference month. Reuse it for every retry so experience calculations do not change with the retry date. The reference is processing metadata, not an extra learned input. Use one named schema for these nested fields across extraction validation, review DTOs, persistence and model preparation.

# Detailed project architecture and code structure

<whiteboard token="UYrDwa5Gth9N7IbR3qfjcc48p1e"></whiteboard>

SimRecrut separates the browser, the Java business backend, storage, online model inference and offline model development. The browser uses the RuoYi Vue 3 frontend. Spring Boot is the application backend: it owns public APIs, access checks, business rules, database changes and cleanup. FastAPI runs model inference on the internal network. Offline training is a separate process that produces checked model packages.

This is an MVC-like separation across a frontend/backend application. Vue pages form the View. Spring MVC controllers receive HTTP requests and return permitted response fields. The application Model includes business services, domain classes, row entities and MyBatis data access. Application services coordinate complete user actions. MyBatis maps database rows; it does not make business decisions. A trained AI model is a separate prediction component, not the meaning of the Model layer in MVC.

## Five main parts

| Part | Technology and location | Responsibility and boundary |
|-|-|-|
| 1. Frontend | RuoYi Vue 3, Vue Router, Pinia, Element Plus, Axios and Vite; frontend/src/ | Pages, navigation, forms, progress and the five result cards. Feature API functions call the shared HTTP client. The browser never connects directly to MySQL, Redis, DeepSeek or FastAPI. |
| 2. Business backend | Java and Spring Boot; backend/src/main/java/fr/isep/simrecrut/ | Public APIs, authentication and access checks, case and job workflows, rules, reference validation, short transactions, background work and deletion. This is one business backend; packages are internal layers rather than separate microservices. |
| 3. Data storage | One relational MySQL database, Redis and restricted server files | MySQL stores durable business records, including the existing RuoYi identity tables. Redis holds cache/session data. Restricted files hold temporary CV processing content. None is publicly served as an unrestricted download. |
| 4. Online model service | Python, FastAPI, Pydantic and PyTorch; ml/service/ | Validate fixed-version requests, build the model inputs and return predictions. It has no application MySQL credentials, public browser route or training action. |
| 5. Offline data, training and evaluation | Python, pandas, scikit-learn and PyTorch; datasets/, scripts/, ml/training/, ml/evaluation/ | Prepare research data, split by person, generate controlled examples, train two models, select with validation and evaluate held-out data. Produce versioned files and evidence for the registry. Live browser requests never start this work. |

Nginx serves the built frontend and forwards /api/v1/ unchanged to Spring Boot. Existing RuoYi login/account endpoints retain their selected installation's routes. The same prefix must not be added twice by the browser client. Page URLs and HTTP API paths remain different: the browser result page selects a Vue component, while its results API retrieves authorised data.

## Technology stack and runtime units

| Part | Choice | Responsibility |
|-|-|-|
| Browser | Official RuoYi-Vue3 frontend, Vue 3, Vue Router, Element Plus, Vite and Axios through its shared request client [R25] | Pages, forms, navigation, upload progress and status polling |
| Frontend state | Pinia, including the existing RuoYi authentication store | Login state, current case ID and small status summaries; detailed CV data remains page-scoped |
| Public entry | Nginx | Serve the built frontend and forward /api/v1/ requests to Spring Boot |
| Business backend | Java, Spring Boot, Spring MVC, Spring Security and RuoYi | Access checks, case workflow, matching, Objective rules, results and deletion |
| Database access inside Java | MyBatis with its Spring Boot integration [R21] | Map mapper methods to parameterised SQL and row/DTO results |
| Relational database service | One MySQL database | Store durable business rows; enforce primary keys, unique keys and selected CHECK constraints; no SQL foreign keys |
| Database changes | Flyway | Apply migrations for SimRecrut sim\_\* tables; retain existing RuoYi user and role tables |
| Cache | Redis | RuoYi runtime data and short-lived progress/cache values |
| CV processing | Apache PDFBox; Tesseract 5 with English/French data through an OCR adapter | Read embedded PDF text; use OCR when the extracted text is not usable |
| Structured extraction | DeepSeek, called only by the backend | Turn redacted text into fields; never score or decide Accept/Reject |
| Backend validation | Bean Validation and Jackson | Check incoming DTOs and parse validated structured data |
| Background tasks | Spring ThreadPoolTaskExecutor, MySQL task records and Spring scheduling | Extraction, assessment and cleanup; no message broker is required for the initial version |
| Model service | Python, FastAPI, Pydantic and PyTorch | Validate requests, build model inputs, load the Neutral and Biased models and return predictions |
| Offline training | Python, pandas, scikit-learn and PyTorch; CUDA when available | Prepare controlled training data, train, evaluate and save checked model versions |
| Local deployment | Docker Compose with separate backend, ML, MySQL and Redis services | Keep internal services and storage off the public web route |

Spring Boot configures the Spring MVC web layer, dependency injection, security integration, transactions, executor and scheduling. Bean Validation checks request fields; Jackson converts JSON. MyBatis is a separate persistence library integrated with Spring Boot. PDFBox, Tesseract and the DeepSeek API are separate tools used for CV extraction. FastAPI and PyTorch run in the Python model service. The backend mainly uses Spring facilities and these named integrations. [R21, R22, R25, R26, R27, R28, R33]

Use the official RuoYi-Vue3 JavaScript frontend and a compatible RuoYi-Vue Spring Boot backend. Pin both revisions and the compatible Java/Spring dependency set before implementation. This plan does not invent version numbers that have not been selected. The backend source root below is a concrete logical source root: if the chosen RuoYi distribution uses Maven modules, place the same packages in its SimRecrut application module and document that physical module path once.

The routing and tool behaviour are described in Vue Router [R30], RuoYi [R25], MyBatis [R21], Spring transactions [R22], FastAPI [R24], PDFBox [R26], Tesseract [R27], DeepSeek structured output [R28], Flyway [R29], Nginx [R31] and Docker Compose [R32]. The folders and service boundaries are project choices.

## Repository layout

| Repository location | Content |
|-|-|
| frontend/src/ | Vue pages, routes, form state and API functions |
| backend/src/main/java/fr/isep/simrecrut/ | Java feature packages and layers described below |
| backend/src/main/resources/mapper/ | MyBatis SQL mappings, grouped by business feature |
| backend/src/main/resources/db/migration/ | Versioned database changes |
| backend/src/test/java/fr/isep/simrecrut/ | Backend and integration checks |
| ml/service/ | Online inference only: FastAPI request validation, exact feature encoding, approved-version loading and prediction |
| ml/training/; ml/evaluation/; ml/schemas/; ml/tests/ | Independent offline training/evaluation programs, frozen input schemas and checks; no public training endpoint |
| scripts/data_cleaning/; scripts/pairing/; scripts/validation/ | Reviewed data preparation and shared-reference checks |
| datasets/raw/; datasets/clean/; datasets/manifests/ | Original sources, prepared research data and provenance |
| datasets/examples/golden_cases/ | The same deterministic reference cases for Java and Python |
| model_artifacts/neutral/ and model_artifacts/biased/, each with a version subfolder | Read-only weights, preprocessing, schema, thresholds and evidence |
| runtime/cases/, each case with a server-generated ID subfolder | Temporary live upload workspace; not served by Nginx or committed to Git |
| docs/ | System/API specifications, research protocol and release instructions |

runtime/cases/ is excluded from Git and is never served by Nginx. Research datasets have a separate storage and access policy. A live Candidate upload does not automatically become a training record. Model folders hold weights, feature schema, preprocessing, thresholds, training settings and test/metric files. FastAPI loads approved versions read-only and keeps the two models in memory.

## Frontend layers and files

Use one feature API folder, src/api/simrecrut/, and one page folder per role. The following filenames are proposed implementation names.

| Location relative to frontend/src/ | Purpose |
|-|-|
| router/index.js; router/modules/simrecrut.js; permission.js | Existing router, SimRecrut route mapping and access guard |
| views/candidate/; views/hr/; views/research/; views/reviewer/; views/admin/ | The pages listed in the page section |
| components/simrecrut/CvUploadCard.vue; CaseProgressStepper.vue | Reusable upload and progress views |
| components/simrecrut/EvidencePanel.vue; FiveResultsPanel.vue | Evidence review and the five clearly separated result cards |
| composables/useCasePolling.js | State-aware two-second polling and cancellation on page exit |
| api/simrecrut/ | The seven feature API modules listed with the pages |
| store/modules/simrecrut.js; utils/request.js | Small cross-page state and the existing shared request client |

Use JavaScript modules and Vue single-file components consistently. Reuse RuoYi permission.js, Pinia authentication/permission stores and utils/request.js. The named request/response fields are defined in the API contract; they remain required when the frontend uses JavaScript.

| Layer | Concrete place | Rule |
|-|-|-|
| Page routing | router/modules/simrecrut.js | Map browser paths to Vue pages and declare required roles/permissions |
| Navigation guard | permission.js with the existing RuoYi guard | Prevent inappropriate navigation; the backend still checks every request |
| Pages | views/candidate/cases/review.vue, corresponding role folders | Read form state, show API data and request actions; no scoring formulas |
| Shared UI | components/simrecrut/ | Reuse upload, evidence, progress and result components |
| Page workflow | composables/useCasePolling.js | Poll active cases every two seconds; stop on completion, review-required, failure, cancellation, expiry or page exit |
| API functions | api/simrecrut/candidateCases.js and other feature files | Define endpoint path, HTTP method and request/response types |
| Shared HTTP client | utils/request.js | Add the existing login token, send Axios requests and handle the SimRecrut response envelope and safe errors |
| State | store/modules/simrecrut.js plus existing auth store | Keep small cross-page state; clear detailed review data when leaving the workflow |

HR job edit, requirements and weights routes reuse views/hr/jobs/wizard.vue; they do not duplicate the wizard into separate pages. Admin health uses views/admin/system-health/index.vue.

A browser route such as /candidate/cases/:caseId/review selects a page. It is not the backend API. That page calls GET or PUT /api/v1/candidate/cases/{caseId}/draft. The result page is /candidate/cases/:caseId/result, while its data endpoint ends with /results.

Use /api/v1 exactly once in outgoing URLs. One concrete option is for API functions to pass the full /api/v1/... path to a shared client whose base URL is the same origin. Existing RuoYi authentication routes keep their existing paths and response handling. Nginx forwards /api/v1/ without removing that prefix; Vue history fallback applies to page navigation, not failed API requests. The development proxy follows the same rule.

Every SimRecrut response includes code, message and requestId; a successful response uses code zero and carries the operation data. HTTP status also reflects the outcome. Adapt shared response handling for that namespace so an existing RuoYi success-code convention does not misclassify a successful SimRecrut request.

All Java paths below are relative to the proposed source root backend/src/main/java/fr/isep/simrecrut/. The same packages can sit in the selected RuoYi application module. Use assessment/casecore/ for case records and casefile/ for temporary files; case cannot be a Java package name because it is a reserved word.

## What each layer owns

| Layer | Proposed package example | What it owns | Allowed dependencies |
|-|-|-|-|
| Security | common/security/ | Resolve the existing authenticated user; check role and case access. CaseAccessService checks ownership, grants, grant expiry and privacy state. | RuoYi/Spring Security context, access-related mappers; no scoring or external extraction |
| Controller | candidate/controller/ | Public HTTP path and method, request binding, basic validation, response status and response DTO. | DTOs, current-user context and application services; never mapper, database entity, ML HTTP client or filesystem |
| DTO | candidate/dto/, role-specific DTO packages | The fields accepted or returned over HTTP. Request and response types are separate. | Simple value types, validation annotations and shared API types; no mapper calls or business operations |
| Application service | candidate/application/ | Coordinate a complete user action, repeat-request handling and work across capabilities. | Business service interfaces, focused concrete helpers, pure domain calculations, background-task service and integration interfaces |
| Business service interface | assessment/casecore/service/AssessmentCaseService.java | A stable capability such as confirming a case or freezing its run versions. It states inputs and outcomes without HTTP or SQL details. | Business value types and commands; no implementation classes or vendor types |
| Business service implementation | assessment/casecore/service/impl/AssessmentCaseServiceImpl.java | Apply state/access rules, own the short transaction for that capability, call mappers and convert row data into business values. | Its mappers/entities, domain rules and other declared service interfaces. Calls that cross the network run outside database transactions. |
| Domain | assessment/domain/matching/, assessment/domain/objective/ | Deterministic matching, mandatory checks, weight validation, score and decision. | Domain values only; no controller, mapper, research-attribute service, HTTP client or filesystem |
| Mapper | assessment/casecore/mapper/ | Parameterised selects, inserts, guarded updates and deletes. | MyBatis and persistence entities/query rows. No workflow decisions or external calls. |
| Entity | assessment/casecore/entity/ | A database-row representation, including internal IDs and storage fields. | Java value types and explicitly configured JSON types; no UI behaviour or scoring |
| Integration interface | ml/gateway/MlGateway.java, casefile/extraction/CvFactExtractionClient.java | The limited operation the business needs from another service. | Internal request/response values; no public controller objects or vendor response objects |
| Integration implementation | integration/ml/, integration/deepseek/, integration/ocr/ | HTTP/library calls, authentication for the dependency, timeouts, output checks and safe error translation. | HTTP/SDK libraries and the business-owned interface; never direct application database writes |

The normal call order is **Controller → Application → Business service interface → Service implementation → Mapper → MySQL**. Domain calculations and external-service interfaces are side calls from the appropriate application or service implementation. They are not extra database layers.

An interface is useful at a module boundary or where a dependency must be replaceable. We do not create an interface for every class. CandidateCaseApplicationService, AssessmentOrchestrator, ExtractionOrchestrator, MatchingService, ObjectiveDecisionService, validators and DTO converters can be ordinary concrete classes. PdfBoxTextExtractor is a concrete helper that uses PDFBox directly; it does not need a second PDF framework or an empty one-method interface. OCR has an adapter because its process/library integration can change. MyBatis supplies mapper implementations, so there is no hand-written SimAssessmentCaseMapperImpl or duplicate generic repository layer.

## Public API ownership

Browser routes, such as /candidate/cases/:caseId/review, select Vue pages. They are separate from HTTP endpoints, such as POST /api/v1/candidate/cases/{caseId}/confirm. Feature API functions under frontend/src/api/simrecrut/ call the existing shared RuoYi request client. Nginx forwards /api/v1/ to Spring Boot without stripping the prefix. Existing RuoYi login/account endpoints retain their existing paths.

| Public controller | Proposed path | API responsibility | Application entry |
|-|-|-|-|
| CandidateJobController | candidate/controller/ | Candidate published job list and details | candidate/application/CandidateJobQueryService |
| CandidateCaseController | candidate/controller/ | Create/list cases; upload; status; draft; optional research values; confirm; run; delete | candidate/application/CandidateCaseApplicationService |
| CandidateResultController | candidate/controller/ | Candidate five-result response | candidate/application/CandidateResultApplicationService |
| HrJobController | job/controller/ | Draft jobs, requirements, weights, validation, publication and versions | job/application/JobApplicationService |
| HrCaseController | assessment/result/controller/ | Authorised HR case list and detail | assessment/result/application/HrCaseQueryService |
| ResearchExperimentController | research/controller/ | Experiments, subjects, variants, execution and metrics | research/application/ResearchExperimentApplicationService |
| ResearchReviewAssignmentController | research/controller/ | Assign primary or research-replicate reviews | review/application/HumanReviewApplicationService |
| ReviewerTaskController | review/controller/ | Assigned tasks, permitted case view and submission | review/application/HumanReviewApplicationService |
| AdminModelController | modelregistry/controller/ | Model list/detail, activation and retirement | modelregistry/application/ModelManagementApplicationService |
| AdminProtocolController | protocol/controller/ | Read frozen protocol metadata | protocol/application/ProtocolQueryService |
| AdminOperationsController | audit/controller/ | Health, failures, audit metadata and authorised safe retry | audit/application/OperationsApplicationService |

The last three operations must not reuse unrestricted Candidate-content queries. Human reviewers are authorised HR or Researcher users with the required case/variant access; they are not a new authentication system. The backend takes the acting user from its security context, not from a trusted candidateUserId or reviewerUserId request field. A review-assignment request may name the target reviewer, but only after the assigning user's permission is checked.

## Business interfaces, implementations and mappers

These are the proposed module boundaries. The Impl classes are real planned implementation names, not claims about existing source files. Helper classes inside one module remain concrete unless a separate contract is useful.

| Capability | Interface path | Implementation path | Database relationships |
|-|-|-|-|
| Jobs and publication | job/service/JobService.java | job/service/impl/JobServiceImpl.java | job/mapper/SimJobMapper, SimJobVersionMapper, SimJobRequirementMapper → sim_job, sim_job_version, sim_job_requirement |
| Case state, confirmation and result writes | assessment/casecore/service/AssessmentCaseService.java | assessment/casecore/service/impl/AssessmentCaseServiceImpl.java | assessment/casecore/mapper/SimAssessmentCaseMapper, SimCaseSnapshotMapper, SimCaseResearchAttributeMapper, SimCaseAccessGrantMapper → case, snapshot, optional-attribute and grant tables |
| Saved result access | assessment/result/service/CaseResultProjectionService.java | assessment/result/service/impl/CaseResultProjectionServiceImpl.java | assessment/result/mapper/SimMatchingResultMapper, SimObjectiveResultMapper, SimMlResultMapper; read permitted human results through the review capability |
| Human assignment and decisions | review/service/HumanReviewService.java | review/service/impl/HumanReviewServiceImpl.java | review/mapper/SimHumanReviewMapper → sim_human_review; case/variant access is checked before assignment, opening and submission |
| Research experiments | research/experiment/service/ExperimentService.java | research/experiment/service/impl/ExperimentServiceImpl.java | research/experiment/mapper/SimExperimentMapper, SimExperimentSubjectMapper, SimExperimentSubjectAssessmentMapper, SimExperimentVariantMapper, SimExperimentResultMapper, SimExperimentMetricMapper → the six corresponding sim_experiment\* tables |
| Reviewed research data | research/dataset/service/ResearchDatasetService.java | research/dataset/service/impl/ResearchDatasetServiceImpl.java | research/dataset/mapper/SimDatasetSourceMapper, SimResearchProfileMapper, SimResearchProfileAttributeMapper → the three corresponding persistent research tables |
| Model selection and release | modelregistry/service/ModelRegistryService.java | modelregistry/service/impl/ModelRegistryServiceImpl.java | modelregistry/mapper/SimModelVersionMapper → sim_model_version; reads compatible protocol/schema/training metadata through the relevant module |
| Offline training metadata | training/service/TrainingService.java | training/service/impl/TrainingServiceImpl.java | training/mapper/SimCorpusVersionMapper, SimCorpusProfileMemberMapper, SimCorpusJobMemberMapper, SimTrainingCaseMapper, SimTrainingExperimentMapper, SimTrainingRunMapper → corpus membership, generated samples and training metadata; import validated offline manifests, never train in a live request |
| Existing account deletion hook | common/security/service/AccountDeletionGuard.java | common/security/service/impl/AccountDeletionGuardImpl.java | The existing RuoYi deletion transaction calls this hook to check incoming SimRecrut references, clear optional actor fields and clean eligible access/idempotency rows |
| Frozen protocol lookup | protocol/service/ProtocolService.java | protocol/service/impl/ProtocolServiceImpl.java | protocol/mapper/SimProtocolVersionMapper, SimMatchingRuleVersionMapper, SimFeatureSchemaVersionMapper, SimBiasProtocolVersionMapper → the four version tables |
| Privacy cleanup | privacy/service/PrivacyService.java | privacy/service/impl/PrivacyServiceImpl.java | privacy/mapper/SimPrivacyDeletionReceiptMapper → sim_privacy_deletion_receipt; coordinates deletion through the owning case, review, research and workspace components |
| Task state | async/service/AsyncTaskService.java | async/service/impl/AsyncTaskServiceImpl.java | async/mapper/SimAsyncTaskMapper → sim_async_task |
| Repeat-request control | idempotency/service/IdempotencyService.java | idempotency/service/impl/IdempotencyServiceImpl.java | idempotency/mapper/SimIdempotencyRecordMapper → sim_idempotency_record |

Within case services, CaseSnapshotService and ResearchAttributeService can remain focused concrete helpers. They do not each need another interface/implementation pair. JobValidationService, JobVersionService, ControlledVariantValidator, AuditService and read-only training-metadata queries can also remain concrete module helpers. An application service normally calls the owning business interface rather than reaching into another module's mapper.

Persistence classes are named after their rows: for example, assessment/casecore/entity/SimAssessmentCaseEntity and SimCaseSnapshotEntity; job/entity/SimJobVersionEntity; review/entity/SimHumanReviewEntity; modelregistry/entity/SimModelVersionEntity. Mapper XML lives under backend/src/main/resources/mapper/, grouped as job/, assessment/casecore/, assessment/result/, review/, research/experiment/, research/dataset/, modelregistry/, protocol/, async/, idempotency/ and privacy/. For example, mapper/assessment/casecore/SimAssessmentCaseMapper.xml belongs to the interface with the same class name. MyBatis maps rows to entities or explicit query DTOs; it does not choose which fields a public user is allowed to see.

The runtime uses the existing RuoYi sys_user, sys_role and sys_user_role. No duplicate SimRecrut user table is required. Internal references use signed BIGINT; public SimRecrut resource IDs use 26-character ULID strings. RuoYi user-ID signedness must be verified against the selected installation before migration, rather than assumed from a branch name.

AssessmentOrchestrator computes or obtains each result, validates it and calls AssessmentCaseService saveValidatedMatching, saveValidatedObjective or saveValidatedPrediction. The implementation owns the short result-write transaction, checks parent references, privacy and the current task lease, then uses the corresponding result mapper. CaseResultProjectionService remains the permitted read capability.

TrainingService imports offline manifests and run evidence through the six corpus/training mappers. It checks corpus membership, grouped partitions, sample identity, referenced protocol/schema and file hashes before storing metadata. ModelRegistryService uses these checked records when registering a tested package. Python training writes offline files; the Java service owns application-database metadata writes.

## A complete service and database call

For Candidate confirmation, CandidateCaseController accepts POST /api/v1/candidate/cases/{caseId}/confirm with the existing token and an idempotency key. It passes the authenticated identity and case ID to CandidateCaseApplicationService. That application service invokes IdempotencyService and then the AssessmentCaseService interface. Spring injects AssessmentCaseServiceImpl.

AssessmentCaseServiceImpl checks owner, active privacy state, expected processing state and required reviewed facts. Its short transaction uses SimAssessmentCaseMapper, SimCaseSnapshotMapper and SimCaseResearchAttributeMapper to save the professional snapshot, keep optional attributes in separate rows and change REVIEW_REQUIRED to CONFIRMED. A successful commit is the point at which those fields are safely stored. Only after commit does the application schedule or perform temporary-file removal through CaseWorkspaceService; a failed deletion is recorded for PrivacyServiceImpl to retry. The confirmation stays valid.

The controller returns HTTP 200, success code 0, message, requestId, and data containing caseId and processingStatus: CONFIRMED. No database entity is serialised directly. Run is a separate request: POST .../run returns 202, and AssessmentOrchestrator performs matching and predictions in the background. The page polls status and later requests the five-result response.

## Application-managed database relationships

Use one MySQL relational database. IDs still represent relationships between tables, but the selected design has **no physical SQL FOREIGN KEY constraints**. Primary keys, unique constraints, appropriate CHECK constraints and indexes remain useful database rules. Removing foreign-key declarations does not move business rules into the Mapper layer.

The owning business service must validate the referenced parent, access rights and current state in the same short transaction as the child write. When parent state or deletion can race with a child insertion, lock the parent record. Both insertion and deletion use the same parent-lock convention; a pre-check performed before the transaction is insufficient. Lock several resources in a stable order to reduce deadlocks, and handle retryable transaction conflicts safely.

| Situation | Backend rule |
|-|-|
| Create a case, review, variant, model or training record | Resolve public identifiers, check every required referenced record and its usable state, then write under the agreed parent lock. Do not trust the browser's claimed owner or version compatibility. |
| Update a draft or task | Use revision/state/lease-token guarded updates. A zero-row update is a conflict rather than silent success. |
| Remove referenced history | Apply the owning service's retention/restriction policy. Retire or archive a used job/model/protocol instead of leaving invalid references. |
| Delete Candidate content | Mark PURGING and deny access first. Stop tasks and pending reviews; explicitly delete dependent results, facts, grants, experiment-linked content and temporary files through their owning services. Recompute or remove affected experiment metrics. There is no automatic database CASCADE. |
| Remove an optional user reference | Apply the chosen service policy explicitly: clear the reference where history should remain, or refuse removal while required history still depends on that account. RuoYi account deletion must use the same integration policy. |
| Complete cleanup | Write only content-free receipt/audit information. Report DELETED after required cleanup succeeds; keep errors retryable and access blocked meanwhile. |
| Find inconsistent references | Run bounded consistency checks and repair through owning services. Imports, administrative maintenance and offline metadata registration use the same rules rather than bypassing them with direct unvalidated writes. |

MyBatis executes the service's parameterised SQL. It neither chooses which related records to remove nor independently decides whether a reference is valid. All application writes, including offline metadata registration, must pass through a validated backend operation or a controlled import service that applies the same transactions and locking rules. [R18, R22]

## Transactions, repeat requests and concurrent work

| Operation | Atomic database work | Work outside that transaction |
|-|-|-|
| Create case | Resolve a published job and frozen compatible protocol; create AWAITING_UPLOAD case and temporary metadata | Create the restricted workspace with explicit compensation: delete the new empty workspace if DB creation rolls back; failed setup never returns a usable case |
| Upload | Record validated stored-file metadata, advance the expected case state, create extraction task | Validate PDF contents/size/encryption/page count and write the file before accepting; compensate an orphaned write if the database step fails |
| Confirm | Verify current case state; write snapshot/optional attributes; mark CONFIRMED; store repeat-request outcome | Remove raw/intermediate files only after commit |
| Start assessment | Check CONFIRMED and active privacy state; resolve and freeze compatible model IDs; record task and AUTOMATED_RUNNING | Run matching and external prediction; save each result in a later short transaction |
| Publish job | Lock the editable job/version, check expected revision, validate requirements/weights, create or publish a fixed version and audit event | Requirement extraction and external calls happen before publication, not while its DB lock is held |
| Submit human review | Check assigned reviewer and open status; save decision, reason and timestamp; mark SUBMITTED and update the case's human summary | Return the permitted DTO after commit |
| Activate model | After successful preflight, recheck approval/compatibility and atomically retire the old active version and activate the checked replacement | Verify files/checksum/schema/threshold/test results and service readiness before the short activation transaction |
| Privacy delete | Mark case inaccessible, cancel pending reviews and revoke grants; after filesystem cleanup, remove linked content and commit a non-content receipt | Filesystem deletion can fail independently; keep PURGING and retry instead of claiming completion |

Spring transactions should be on methods invoked through a Spring-managed bean. Use a separate transactional service or TransactionTemplate for the short sections inside a long worker. Calling a transactional helper on the same object must not be relied on to create a new transaction. An async thread gets its own transaction scope; it does not inherit a request transaction. Never hold MySQL locks while waiting for OCR, DeepSeek or FastAPI.

**Repeat requests.** The unique database key is user ID, operation scope and idempotency key. Include the action and resource ULID in the scope, and hash the normalised request including its target. Current authentication, role and resource/privacy checks run before replaying a response; a deletion acknowledgement can be replayed only to the same authenticated requester. A repeated key with changed content returns 409; an exact completed repeat returns its stored safe response. For short writes, create/check the key, make the business change and save its safe response in one transaction. A concurrent duplicate waits for that transaction, then reads the result. For a long action, commit the task, fixed versions, state change and 202 response together; repeat requests return the same task outcome and never create another task. The default record expiry is 24 hours, with cleanup of safe response records. A deletion replay returns its saved deletion acknowledgement without loading deleted content.

**Guarded updates.** SimAssessmentCaseMapper advances a state only when its expected old state still matches and privacy is active. SimJobVersionMapper updates a draft only when revision_no and editable status match, then increments the revision. A zero-row update becomes a conflict; it is not silently treated as success. Requirement and weight edits must use the same version revision, not only the job-description endpoint.

**Saving results.** Matching and Objective each have one row per case; ML has one row per case/model type. Only validated successful ML predictions are inserted. Although the result table permits a FAILED status, its score and decision columns are required; therefore a technical failure is stored in the task/case error state and creates no fabricated score or Reject row. A retry skips saved success and uses the same case-frozen model version. Set the confirmation-plus-30-day deadline at confirmation. After all required automated results exist, set AUTOMATED_COMPLETE and replace that deadline with completion plus 30 days. Worker result writes must recheck active privacy state so an in-flight request cannot restore content after deletion starts.

**Reliable task ownership.** Keep the bounded Spring executor and a database task queue. The proposed next migration adds worker_id, lease_token, lease_until and heartbeat_at to sim_async_task. A worker claims a due queued/retryable row in a short locked transaction, increments the attempt count and assigns a new lease token. It periodically extends the lease. Completion is accepted only for the current token. After restart or lease expiry, a reconciler safely resumes the unfinished stage with the same versions. Dispatch occurs after the task transaction commits; a poller also finds committed queued tasks, so a crash between commit and dispatch loses no work. Claim limits and maximum attempts remain bounded. No Redis-only lock or in-memory flag is the durable owner.

## Fields returned to each caller

| Caller/response | Fields to include | Fields to exclude |
|-|-|-|
| Candidate job | Job public ID/version/title/description and public requirements | HR weights, rule threshold, private matching configuration |
| Candidate status | caseId, processingStatus, stage, retryable, updatedAt | CV text, dependency secrets and stack traces |
| Candidate draft | Reviewed professional fields, short permitted evidence, unresolved required fields | Raw PDF/download path and direct identifiers |
| Candidate five results | Objective score/display score/decision/public strengths and gaps; Neutral/Biased acceptance scores and decisions; permitted research-attribute summary; primary human status/decision/reason; deletion date | HR weights, rule/ML thresholds, bias deltas, reviewer identity, internal contributions and model file paths |
| Authorised HR detail | Professional snapshot, matches, mandatory result, HR weights, Objective contributions, saved scores, human status and version references | Raw CV, removed identifiers, model files and ungranted cases |
| Reviewer task before submission | Assigned case/variant, professional data, public job requirements, rubric and condition-specific instruction | Objective/Neutral/Biased outputs and other human answers |
| Research response | Authorised experiment configuration, fixed versions, controlled changes, group sample counts and metrics | Unauthorised or purged Candidate content |
| Admin operations | Service health, resource IDs, stage/error code, retryability, timestamps and cleanup status | Candidate professional content by default |

Build these DTOs with explicit fields in CaseResultProjectionServiceImpl or the owning review/query service. Do not load a complete entity graph and try to hide a few fields at the controller. For a pending human review, decision is absent/null rather than Reject. A submitted review requires a decision and timestamp. Internal database IDs, storage paths and generic unrestricted JSON maps are not public response models.

Normal JSON responses contain code, message, requestId and data; success code is 0. Error responses use a stable SIM-xxxx code, safe message, request ID and retryable flag with the appropriate HTTP status. Authentication failures use 401, forbidden actions 403, state/revision conflicts 409, unresolved business validation 422 and unavailable dependencies 503. The frontend shared client must handle this namespace correctly without changing the existing RuoYi login response convention.

## Internal services, JSON and number precision

casefile/extraction/CvFactExtractionClient is implemented by integration/deepseek/DeepSeekCvFactExtractionClient. It sends redacted text, schema version and fact-extraction instructions only. It validates the returned structure before building the review draft. It does not request scores, hiring decisions or invented demographic facts. Use the defined 5-second connection limit, 20-second read limit, one transport/service retry and one structured-output repair attempt. integration/pdf/PdfBoxTextExtractor directly uses PDFBox; integration/ocr/TesseractOcrAdapter implements the replaceable casefile/extraction/OcrAdapter. casefile/workspace/CaseWorkspaceService owns restricted case paths and local file operations.

ml/gateway/MlGateway is implemented by integration/ml/FastApiMlGateway. It alone handles Java-to-FastAPI HTTP. Calls use /internal/v1/predict/neutral and /internal/v1/predict/biased on the private ML service, not the browser's public /api/v1 namespace. There is no public proxy route to those endpoints. Dependency addresses, credentials and DeepSeek secrets are backend configuration, never browser fields or logged response data. The service has no application MySQL credentials.

Neutral receives only m, w, mw, g, identifiers and fixed schema/model versions; it has no research block. Python supplies the zero research mask. Biased additionally receives allowed categorical values, including UNKNOWN; Python owns their exact encoding order. The adapter checks request ID, case ID, requested model/schema version, finite scores/thresholds, valid decisions and success status. Model failures remain execution errors. Java never trains a model or selects its threshold during a live request.

| Stored value | Database type and Java choice | Handling rule |
|-|-|-|
| HR weights, matches, Objective contributions/score and rule threshold | DECIMAL(8,7) and BigDecimal | Parse decimal JSON values without converting through binary floating point; use seven decimal places and HALF_UP where calculation requires rounding |
| ML logits, acceptance scores, selected/model/common thresholds and experiment metrics | DOUBLE and Java double/Double | Preserve model numeric precision; reject NaN/infinity and invalid ranges; round only for screen display |
| Bias-effect components and noise | DECIMAL(9,8) and BigDecimal | Keep the configured eight-place values for offline/research records |
| Professional snapshots and fixed structured requirements | MySQL JSON with typed objects and a MyBatis TypeHandler | Validate against the named schema before storing; use explicit permitted fields |
| Variable metrics/configuration metadata | MySQL JSON with JsonNode where appropriate | Validate shape, size and permitted content before saving or returning a projection |
| Public IDs and timestamps | ULID string; UTC DATETIME(3) mapped consistently to Java time values | Never convert public IDs to JavaScript numbers; return timestamps with an explicit UTC/offset representation |

Weight inputs must be within range and representable at the accepted seven-place precision; reject excess non-zero precision instead of silently changing a user's weights. Their sum must equal one at that precision. To make Java/Python results reproducible, quantise matching outputs to seven places, multiply those fixed matches by fixed weights, round each contribution to seven places using HALF_UP, sum those contributions and compare with the stored seven-place rule threshold. Use the same procedure in shared reference cases on both sides. ML requests use those same fixed numeric values, with their documented floating-point tolerance for mw validation. UI display rounding must never be fed back into scoring.

JSON conversion is not raw string concatenation. Configure Jackson and the JSON TypeHandlers explicitly, reject unknown security-sensitive request properties, and validate flexible requirement payloads by criterion type. The professional snapshot has an allowlist and rejects name, email, phone, exact address and raw path fields. Keep optional research values in their own table. Temporary evidence or extracted text must not leak into generic audit/error JSON.

## Online ML and independent offline training

The backend first resolves reviewed professional facts, the fixed job/protocol/model versions and the permitted research values. It calls the internal Neutral/Biased routes through MlGateway. Python constructs the exact inputs, runs the approved loaded model and returns IDs, schema/version, scores, cutoffs and decisions. Neutral receives only the 16 professional values and supplies its own zero research mask. Biased also receives permitted categories for Python encoding. Both actual networks have 32 → 16 → 8 → 1 units and 673 parameters each.

The response returns to the backend. The adapter checks identity, schema/version, status, finite numbers and valid ranges. The business service rechecks privacy/state, then uses MyBatis to save the successful prediction in MySQL. FastAPI never saves application results itself. A technical failure remains a task/case error and never becomes an artificial Reject result. Safe retries keep the original versions and previously successful outputs.

Offline work prepares reviewed research datasets and records provenance, group membership and partitions before creating variants. Training and validation choose the weights and cutoffs; held-out evaluation follows only after choices are frozen. The resulting package contains weights, schema, preprocessing, cutoffs, checksums, training settings and evaluation evidence under model_artifacts/{neutral,biased}/{version}/. Registration records references and metadata rather than uploading large model files through a browser. Approval and activation are separate actions. FastAPI loads approved, checked packages read-only, while existing cases retain the versions needed for reproducible retries.

## Implementation completion

The database chapter defines the additional uniqueness, active-model, task lease, retention and deletion rules. Implement those rules as new migrations and service checks. The API completion and permitted-field tables above define the page integration boundary. Keep reference cases for decimal calculations, simultaneous actions, stale workers, deleted targets and role-specific response fields.

# Database design and storage

<whiteboard token="S4KMwaLI0hxFqGbDvowjfrGNpg9"></whiteboard>

The proposed physical schema has **36 SimRecrut tables and zero SQL foreign-key constraints**. It retains 36 primary keys, 34 additional unique constraints, 80 check constraints, the reference-ID columns and their indexes. The 65 relationships are logical business relationships enforced by Spring Boot services. Existing RuoYi identity tables are reused and are not counted among the 36. This is a proposed design; the migration has not been executed.

## Storage boundaries

| Storage | Contents | Responsibility and lifetime |
|-|-|-|
| MySQL | Jobs and fixed versions; confirmed facts without direct identifiers; separate research attributes; automated and human results; access grants; research/training/model metadata; tasks, idempotency, safe audit and deletion receipts. | Spring Boot services own writes through MyBatis. Case ownership and access records still link a case to an account, so removed direct identifiers do not mean full anonymity. |
| Redis | Login/session support, short-lived progress and optional caches. | Backend only. Losing Redis must not lose saved facts, results or durable cleanup work. Delete case-bearing cached responses and invalidate permissions during privacy cleanup. |
| Restricted runtime files | Temporary PDF, PDF/OCR text, redacted text, extraction response and review draft. MySQL stores safe relative paths and cleanup metadata. | Workspace service controls paths. After confirmation commits, remove raw/intermediate files. Cancelled cases and unconfirmed cases older than 24 hours are cleaned. Failed cleanup is retried. |
| Offline datasets | Original research sources, reviewed records, corpus manifests, generated training samples and test fixtures. | Offline research/training work. Preserve provenance and grouped person partitions. Never copy Candidate uploads into training automatically. |
| Model/configuration files | Weights, schemas, preprocessing, matching rules, manifests and evaluation evidence. | Offline training produces immutable packages. Server checks pass before registration as TESTED; approval changes TESTED to APPROVED; activation affects future runs. Historical referenced packages remain available. |

The proposed retention default stores confirmation plus 30 days even when automation never runs or remains failed. Successful automation replaces that deadline with completion plus 30 days. Earlier user deletion takes priority. This is the design rule to implement, not a claim about an existing scheduler.

## Physical rules and logical references

Each SimRecrut table keeps a signed auto-increment BIGINT primary key named id. Public resource IDs remain separate unique 26-character values with ASCII binary comparison. Versioned configuration records use unique version codes where defined. All reference IDs retain their current BIGINT type and nullability; they are ordinary columns, with no REFERENCES declaration and no database cascade.

Primary keys, unique constraints and checks remain database-enforced row/key rules once the proposed migration is installed. [R19, R20] They do not validate parent existence. Checks still control permitted status values, ranges, flags and the exclusive source/target choices. A permitted status value does not make every state transition legal. JSON columns enforce valid JSON syntax; services validate the business schema and remove direct identifiers from professional facts.

Use UTC millisecond timestamps. Map matching/rule DECIMAL(8,7) and bias-effect DECIMAL(9,8) to BigDecimal. ML scores, model thresholds and experiment metrics use DOUBLE; services reject non-finite or out-of-range values. NULL means absent or pending where allowed; it never means Reject. A pending human review has no decision. A successful automated result is written only after validating all required values.

The E/R lines are labelled **logical relationships, not database-enforced foreign keys**. A required reference means an existing child must identify one valid parent; it does not require every parent to have a child. A case has 0..1 snapshot, matching result and Objective result. An experiment subject has 0..1 baseline assessment. A training run has 0..N model versions because its reference is not unique. Ordinary child links are 0..N; a nullable parent reference is 0..1. Service transactions must make these relationships true.

Deletion receipts use a case-ID hash and have no case relationship. Audit events link only to an optional actor account. Generic task resource IDs are not entity foreign keys and are not drawn as such.

## Service transaction protocol

Every create, import, background-result write and reference change must use the same protocol. In a short transaction, the write service locks all referenced parent rows in shared mode, validates existence, allowed lifecycle state, permissions and compatible versions, then writes the child. It retains those parent locks until commit. If a reference changes, lock both the old and new parents. A lookup outside the transaction is insufficient. MySQL locking reads provide the row-lock mechanism. [R18]

A parent deletion service plans the required lock set and acquires it in the same global order, including an exclusive lock on the parent row. After obtaining the parent lock, the service checks every incoming logical relationship and applies its policy before deleting the parent. Reference writers cannot pass their parent lock while deletion holds this lock. If deletion waits for an earlier child writer, it rechecks the children after acquiring the lock. This closes the check-then-delete race without SQL foreign keys.

Use one fixed global table-and-ID lock order for multiple parents. Plan the dependency set, acquire locks in that order, then revalidate it; if the set changed, retry the transaction. Use bounded deadlock retries with the same idempotency identity. Keep all parent locks until the operation commits. MyBatis mappers perform the locked reads and writes within Spring-managed transaction boundaries. [R21, R22] Never hold a database transaction open during OCR, DeepSeek, inference or filesystem cleanup. Case access, processing and privacy state must be rechecked before saving results, in addition to task lease ownership.

All application-table writers follow this protocol, including RuoYi account deletion and administrative import. Python inference has no application database write access. Direct scripts and bulk loads cannot bypass the service validation path. Periodic integrity scans detect non-null references with missing parents, mismatched model types/versions and duplicate business assignments; they report and quarantine inconsistencies rather than silently guessing replacements.

## Explicit backend deletion policies

| Policy | Service action inside the final database transaction | Practical meaning |
|-|-|-|
| BLOCK_WHILE_REFERENCED | With the parent exclusively locked, check every matching child reference; reject hard deletion if any exists. | Preserve job, protocol, model, research-profile and training history. Retire or archive referenced versions instead. |
| DELETE_CHILDREN | With the parent locked, explicitly delete eligible dependent rows and their descendants before the parent. | This is service-owned child cleanup. MySQL does not cascade. |
| CLEAR_OPTIONAL_REFERENCE | With the parent locked, explicitly set the designated nullable child reference to NULL, then delete the parent. | Preserve the child when an optional owner, publisher, reviewer or audit actor account is removed. |

The service implements each deletion policy explicitly. Reference IDs are immutable identifiers; services reject primary-key changes. Every parent owner checks all incoming links before any deletion. When several policies apply, first establish that all BLOCK checks pass; only then perform deletes/clears, and commit them with the parent deletion. A failed operation rolls back all relational changes.

## Concrete service boundaries

| Responsibility | Interface and implementation | Integration rule |
|-|-|-|
| Case and automated-result writes | assessment/casecore/service/AssessmentCaseService.java; service/impl/AssessmentCaseServiceImpl.java | Add saveValidatedMatching, saveValidatedObjective and saveValidatedPrediction capabilities. AssessmentOrchestrator coordinates computation and calls these short transactions; the implementation validates parent references, privacy and lease before using the three result mappers. |
| Result reads | assessment/result/service/CaseResultProjectionService.java; service/impl/CaseResultProjectionServiceImpl.java | Reads permitted saved outputs through SimMatchingResultMapper, SimObjectiveResultMapper and SimMlResultMapper. It remains a read/projection capability, not the scoring writer. |
| Offline training metadata | training/service/TrainingService.java; service/impl/TrainingServiceImpl.java | Import and validate corpus membership, generated-case identities, training experiments and runs through the six training/corpus mappers. This capability does not run online model training. |
| Existing account deletion | common/security/service/AccountDeletionGuard.java; service/impl/AccountDeletionGuardImpl.java | Hook into the existing RuoYi account-deletion transaction. Lock the user, check all required historical links, delete designated access/idempotency children and clear four optional references. Reuse existing identity management; do not create a second identity system. |

ProtocolService, ResearchDatasetService, JobService, ModelRegistryService, ExperimentService, HumanReviewService, AsyncTaskService, IdempotencyService and PrivacyService keep their established interface/Impl boundaries. AuditService is a concrete audit helper under audit/service/ and uses audit/mapper/SimAuditEventMapper. Their implementations call MyBatis mapper interfaces; MyBatis supplies the mapper implementation. CaseAccessService remains the security helper for ownership, grants and privacy checks; AssessmentCaseService owns access-grant persistence. [R21]

## Table catalogue and relation ownership

All rows retain PK id. The reference list shows the exact ID column and target table. Parentheses give the backend action when that parent is deleted: **B** = block, **D** = explicitly delete this child, **N** = clear this nullable reference. A question mark marks an optional reference. These letters are service policies, not database actions. The named writer validates every listed reference using the shared-lock protocol. The parent table's owning service performs its incoming-link checks and cleanup; AccountDeletionGuard owns sys_user deletion.

| Table and purpose | Reference IDs and parent-deletion policy | Write/validation owner |
|-|-|-|
| sim_matching_rule_version — versioned matching definitions | No parent reference | ProtocolService |
| sim_feature_schema_version — model input layout | No parent reference | ProtocolService |
| sim_bias_protocol_version — controlled research effects | No parent reference | ProtocolService |
| sim_protocol_version — one consistent rule/schema/bias package | matching_rule_version_id → sim_matching_rule_version (B); feature_schema_version_id → sim_feature_schema_version (B); bias_protocol_version_id → sim_bias_protocol_version (B) | ProtocolService |
| sim_dataset_source — research-source provenance | No parent reference | ResearchDatasetService |
| sim_job — logical job | source_dataset_id? → sim_dataset_source (B); owner_user_id? → sys_user (N) | JobService |
| sim_job_version — one editable or published configuration | job_id → sim_job (B); matching_rule_version_id → sim_matching_rule_version (B); created_by → sys_user (B); published_by? → sys_user (N) | JobService |
| sim_job_requirement — reviewed detail used in matching | job_version_id → sim_job_version (D) | JobService |
| sim_research_profile — persistent reviewed research baseline | dataset_source_id → sim_dataset_source (B); reviewed_by? → sys_user (N) | ResearchDatasetService |
| sim_research_profile_attribute — research labels with provenance | research_profile_id → sim_research_profile (D) | ResearchDatasetService |
| sim_corpus_version — frozen training/evaluation selection | No parent reference | TrainingService |
| sim_corpus_profile_member — base profile's partition | corpus_version_id → sim_corpus_version (D); research_profile_id → sim_research_profile (B) | TrainingService |
| sim_corpus_job_member — jobs in a corpus | corpus_version_id → sim_corpus_version (D); job_version_id → sim_job_version (B) | TrainingService |
| sim_training_case — generated training sample | corpus_version_id → sim_corpus_version (D); research_profile_id → sim_research_profile (B); job_version_id → sim_job_version (B); protocol_version_id → sim_protocol_version (B); feature_schema_version_id → sim_feature_schema_version (B) | TrainingService |
| sim_training_experiment — development configuration | corpus_version_id → sim_corpus_version (B); protocol_version_id → sim_protocol_version (B); created_by → sys_user (B) | TrainingService |
| sim_training_run — one seed run | training_experiment_id → sim_training_experiment (B) | TrainingService |
| sim_model_version — loadable frozen artifact | training_run_id → sim_training_run (B); feature_schema_version_id → sim_feature_schema_version (B); protocol_version_id → sim_protocol_version (B) | ModelRegistryService |
| sim_assessment_case — one simulation | candidate_user_id → sys_user (B); job_version_id → sim_job_version (B); protocol_version_id → sim_protocol_version (B); neutral_model_version_id? → sim_model_version (B); biased_model_version_id? → sim_model_version (B) | AssessmentCaseService |
| sim_case_snapshot — confirmed professional facts without direct identifiers | case_id → sim_assessment_case (D) | AssessmentCaseService |
| sim_case_research_attribute — optional online research values | case_id → sim_assessment_case (D) | AssessmentCaseService |
| sim_case_temp_artifact — file lifecycle metadata | case_id → sim_assessment_case (D) | AssessmentCaseService |
| sim_case_access_grant — explicit case permission | case_id → sim_assessment_case (D); user_id → sys_user (D); granted_by → sys_user (B) | AssessmentCaseService |
| sim_matching_result — frozen professional matching | case_id → sim_assessment_case (D); matching_rule_version_id → sim_matching_rule_version (B) | AssessmentCaseService |
| sim_objective_result — deterministic rule result | case_id → sim_assessment_case (D); protocol_version_id → sim_protocol_version (B) | AssessmentCaseService |
| sim_ml_result — one model-type result per case | case_id → sim_assessment_case (D); model_version_id → sim_model_version (B); feature_schema_version_id → sim_feature_schema_version (B) | AssessmentCaseService |
| sim_experiment — fixed comparison configuration | job_version_id → sim_job_version (B); protocol_version_id → sim_protocol_version (B); neutral_model_version_id → sim_model_version (B); biased_model_version_id → sim_model_version (B); created_by → sys_user (B) | ExperimentService |
| sim_experiment_subject — one experiment's selected source | experiment_id → sim_experiment (D); research_profile_id? → sim_research_profile (B); candidate_case_id? → sim_assessment_case (D) | ExperimentService |
| sim_experiment_subject_assessment — shared professional baseline | experiment_subject_id → sim_experiment_subject (D) | ExperimentService |
| sim_experiment_variant — one controlled version | experiment_subject_id → sim_experiment_subject (D) | ExperimentService |
| sim_experiment_result — a variant/model output | variant_id → sim_experiment_variant (D); model_version_id → sim_model_version (B) | ExperimentService |
| sim_experiment_metric — group or experiment statistics | experiment_id → sim_experiment (D) | ExperimentService |
| sim_human_review — assigned review | case_id? → sim_assessment_case (D); experiment_variant_id? → sim_experiment_variant (D); reviewer_user_id → sys_user (B) | HumanReviewService |
| sim_async_task — durable background work | No parent reference | AsyncTaskService |
| sim_idempotency_record — duplicate-safe operation result | user_id → sys_user (D) | IdempotencyService |
| sim_audit_event — non-content operational history | actor_user_id? → sys_user (N) | AuditService |
| sim_privacy_deletion_receipt — evidence of cleanup | No parent reference | PrivacyService |

There are 65 logical links: 39 block-delete policies, 22 explicit child-delete policies and 4 optional-reference clearing policies. The four nullable account references are job owner, job publisher, research-profile reviewer and audit actor. Required identity links may block account hard deletion; deactivation remains available without removing history. Idempotency and access-grant rows have explicit cleanup policies where listed.

AssessmentCaseService owns case validation but cannot hard-delete a case on its own. PrivacyService coordinates the full operation: mark DELETE_REQUESTED/PURGING and deny access, revoke grants and worker leases, stop content writers, clear restricted files and case-bearing caches, then execute the relational cleanup transaction. Purging must retry previously failed PURGING cases as well as newly due cases.

Within that transaction, remove case-targeted reviews and access grants, snapshots, research attributes, file metadata, matching, Objective and ML results. For each case-linked experiment subject, remove variant-targeted reviews and variant results before variants, then remove baseline assessments and subjects. Invalidate or recompute affected experiment metrics; they are not children of the case. Finally write the non-content deletion receipt and delete the case. Preserve persistent research profiles, frozen job/model/protocol versions and unrelated experiments. Physical files are removed before their tracking metadata is lost. If cleanup fails, keep the case inaccessible and recoverable.

## Field and constraint catalogue

The proposed physical design has no SQL foreign keys. Every reference below is an ordinary ID checked by a Spring business service. All deletion, clearing and blocking statements describe explicit service actions; MySQL never cascades or clears these relationships. Primary keys, UNIQUE and CHECK declarations remain database rules. A nullable parent ID is validated when present.

### Table catalogue

All rows below have the common id primary key. “Required” and “optional” describe the actual column nullability. “Unique” lists the important additional keys. The deletion column describes backend deletion policy, not permission to delete a business record.

#### Rules, schemas and datasets

| Table and purpose | Important fields, keys and relationships | Deletion and business rules |
|-|-|-|
| sim_matching_rule_version — versioned matching definitions | Unique version_code; config_path, config_sha256, status; optional description, frozen_at. | Referencing protocols, job versions and matching results restrict deletion. Database allows DRAFT/FROZEN/RETIRED; service prevents changes to frozen content and checks the file hash. |
| sim_feature_schema_version — model input layout | Unique version_code; dimension_count, schema_path, schema_sha256, status; optional frozen_at. | Referencing protocols, training cases, models and ML results restrict deletion. Database requires a positive dimension count; service checks the exact encoding and field order. |
| sim_bias_protocol_version — controlled research effects | Unique version_code; four effect JSON objects, unknown_effect, status; optional calibration summary and freeze time. | Referencing protocols restrict deletion. Database requires the unknown effect to be zero; service validates the JSON mappings and freezes their meaning. |
| sim_protocol_version — one consistent rule/schema/bias package | Unique version_code; required logical references to matching rule, feature schema and bias protocol; t_rule, status. | All three parent deletions are restricted. Its own deletion is restricted while jobs' related processing, models, cases, training or experiments reference it. Database checks the rule cutoff range; service enforces compatible frozen components. |
| sim_dataset_source — research-source provenance | Unique public_id and name/version pair; required manifest_path; optional source URL, license, download time, hash, row count and metadata. | Imported jobs and research profiles restrict source deletion. The service checks provenance and file availability; a nullable license field does not by itself establish permission to use the source. |

#### Jobs

| Table and purpose | Important fields, keys and relationships | Deletion and business rules |
|-|-|-|
| sim_job — logical job | Unique public_id; source_type, title, status; optional dataset and owner-user logical references, source record ID. | Dataset deletion is restricted; deleting the optional owner sets it to NULL. Job versions restrict job deletion. Archive a used job. The service must keep HR-created/imported source fields consistent. |
| sim_job_version — one editable or published configuration | Unique public_id and job/version-number pair; required job, matching-rule and creator logical references; optional publisher; five weights, revision_no, description and public requirements JSON. | Required parents restrict deletion; optional publisher becomes NULL if removed. Requirements are explicitly deleted when a version is deleted, while cases, corpus members, training cases and experiments can restrict version deletion. Database checks each weight in 0–1; service checks the total, active areas, publication completeness and immutability. |
| sim_job_requirement — reviewed detail used in matching | Required job-version logical reference; criterion_type, requirement_code, JSON payload, mandatory, public_visible, order; optional source excerpt. | Delete explicitly with its version. Database checks criterion type and boolean flags. It does not prevent duplicate requirement codes or validate the payload's business schema. Only editable versions may be changed. |

#### Candidate cases and automated results

| Table and purpose | Important fields, keys and relationships | Deletion and business rules |
|-|-|-|
| sim_assessment_case — one simulation | Unique public_id; required Candidate user, job version and protocol logical references; optional Neutral/Biased model logical references. Separate processing_status, human_status, privacy_status; lifecycle timestamps, purge_after, safe error code. | All referenced-parent deletions are restricted. Case deletion requires explicit cleanup of runtime children and Candidate-linked experiment subjects. The service freezes model IDs at run start and computes the retention deadline. Model types and cross-version compatibility are not guaranteed by the logical references alone. |
| sim_case_snapshot — confirmed professional facts without direct identifiers | Required and unique case_id; schema_version, professional_json, creation time. | Delete explicitly with the case. One case has at most one snapshot, but may have none before confirmation. schema_version is a text value, not a logical reference. The service removes direct identifiers and prevents changes beside existing results. |
| sim_case_research_attribute — optional online research values | Required case logical reference; unique case/attribute pair; type, value, source, confirmation flag; optional mapping version. | Delete explicitly with the case. Database checks four attribute types and the three permitted online source types. The service validates value codes, applies UNKNOWN and keeps these values out of Objective and Neutral input. |
| sim_case_temp_artifact — file lifecycle metadata | Required case logical reference; artifact type, internal relative_path, status and delete_after; optional MIME, size, hash and deletion time. | Metadata is explicitly deleted with the case; physical files do not. The service validates path containment and must remove files before losing their tracking metadata. Artifact status distinguishes CREATED, DELETED and DELETE_FAILED. |
| sim_case_access_grant — explicit case permission | Required case, grantee-user and granting-user logical references; unique case/user/access-type; optional expiry. | Case and grantee deletion require explicit child cleanup; deletion of the granting user is restricted. The database checks access type. Services also require the correct role, unexpired grant and active privacy state. |
| sim_matching_result — frozen professional matching | Required, unique case logical reference; required matching-rule logical reference; five match values, mandatory pass flag, optional evidence JSON. | Case deletion requires explicit child cleanup; matching-rule deletion is restricted. Database checks the mandatory boolean, but not the five match ranges. The service validates ranges and provenance before saving. |
| sim_objective_result — deterministic rule result | Required, unique case logical reference; required protocol logical reference; rule cutoff used, five contributions, score and decision. | Case deletion requires explicit child cleanup; protocol deletion is restricted. Database checks cutoff/score range and Accept/Reject. The service verifies contributions, total and mandatory outcome using the same frozen job and matching result. |
| sim_ml_result — one model-type result per case | Required case, model and feature-schema logical references; unique case/model-type pair; DOUBLE logit, score and thresholds; decision, common-threshold decision and status. | Case deletion requires explicit child cleanup; model/schema deletion is restricted. Database checks ranges, types and decisions. Current nullable rules do not safely represent a failed prediction without a score; the success-only write rule must be applied. |

#### Research subjects and experiments

| Table and purpose | Important fields, keys and relationships | Deletion and business rules |
|-|-|-|
| sim_research_profile — persistent reviewed research baseline | Required dataset logical reference; unique public ID, dataset/source-record pair and base_profile_key; professional JSON, schema and review status; optional reviewer. | Dataset deletion is restricted; removed reviewer becomes NULL. Profile attributes require explicit child cleanup, but training, corpus or experiment use restricts profile deletion. These are research records, not ordinary Candidate accounts. |
| sim_research_profile_attribute — research labels with provenance | Required research-profile logical reference; unique profile/type/source triplet; value and source; optional provenance JSON. | Delete explicitly with the profile. Multiple sources may legitimately exist for one attribute. The service chooses the authorised source for a run and never overwrites a dataset label with a synthetic variant. |
| sim_experiment — fixed comparison configuration | Unique public ID; required job, protocol, Neutral model, Biased model and creator logical references; experiment type, optional controlled factor, frozen weight JSON, status and run times. | Referenced parents restrict deletion. Deleting the experiment requires explicit cleanup of subjects and metrics. Services enforce job/model/protocol compatibility, valid weights and immutability once the run starts. |
| sim_experiment_subject — one experiment's selected source | Required experiment logical reference; optional research-profile and Candidate-case logical references, with exactly one non-null. | Experiment and Candidate-case deletion require explicit child cleanup; research-profile deletion is restricted. The service checks research access, matching job version and subject deduplication; the current table has no source-per-experiment unique key. |
| sim_experiment_subject_assessment — shared professional baseline | Required and unique subject logical reference; five matches, five weights, mandatory outcome, Objective score and decision. | Delete explicitly with the subject. At most one baseline may exist, not a mandatory row from creation. Services require it before model comparisons and validate that variants do not change this baseline. |
| sim_experiment_variant — one controlled version | Required subject logical reference; unique subject/variant-number pair; label, optional changed attribute and four categorical research values. | Delete explicitly with its subject, taking results and targeted reviews with it. Database checks the changed-attribute name. Services compare variants and reject changes outside the selected factor. |
| sim_experiment_result — a variant/model output | Required variant and model logical references; unique variant/model pair; model type, DOUBLE score and two decisions. | Variant deletion requires explicit child cleanup; model deletion is restricted. Database checks score and decision domains. Services ensure the model and thresholds belong to the experiment's fixed configuration. |
| sim_experiment_metric — group or experiment statistics | Required experiment logical reference; metric code; optional group, scalar value, result JSON and parameters JSON. | Delete explicitly with the experiment. Its metric index is not unique. The service must define replacement/versioning rules and include sample counts and threshold settings. Metrics linked to deleted Candidate content must be removed, recomputed or demonstrably de-identified. |

#### Training and model registry

| Table and purpose | Important fields, keys and relationships | Deletion and business rules |
|-|-|-|
| sim_corpus_version — frozen training/evaluation selection | Unique version code; manifest path/hash, profile and job counts, split strategy JSON; optional freeze time. | Deletion requires explicit cleanup of membership rows and training cases, but training experiments can restrict it. Database checks nonnegative counts. Services verify the manifest and protect a frozen corpus. |
| sim_corpus_profile_member — base profile's partition | Required corpus and research-profile logical references; unique corpus/profile pair; TRAIN/VALIDATION/TEST partition. | Corpus deletion requires explicit child cleanup; profile deletion is restricted. The unique pair assigns one partition per profile in this table. It does not force a training-case row to use that same partition. |
| sim_corpus_job_member — jobs in a corpus | Required corpus and job-version logical references; unique corpus/job pair; held-out-test flag. | Corpus deletion requires explicit child cleanup; job-version deletion is restricted. Database checks the flag. Services ensure generated cases actually use members of that corpus. |
| sim_training_case — generated training sample | Required corpus, profile, job, protocol and feature-schema logical references; partition, weight configuration, variant code and target mode; matches, weights, four categories, rule score, labels, bias-effect components and optional noise. | Corpus deletion requires explicit child cleanup; other referenced parents restrict deletion. Checks cover partition, labels, mandatory flag, target mode and score ranges. There is no generated-sample unique key and no membership/partition consistency constraint. Services must validate generation and prevent repeat insertion. |
| sim_training_experiment — development configuration | Unique public ID; required corpus, protocol and creator logical references; model type, architecture JSON and optimiser JSON. | All parent deletions are restricted; training runs restrict its deletion. Services validate configuration compatibility and protect test data from tuning. |
| sim_training_run — one seed run | Required training-experiment logical reference; unique experiment/seed pair; status; optional epoch, selected DOUBLE threshold, metrics, artifact path and completion time. | Experiment deletion is restricted. Model versions restrict run deletion. A run may produce several model-version rows because training_run_id is not unique in the model table. |
| sim_model_version — loadable frozen artifact | Unique version code; required run, feature-schema and protocol logical references; type, artifact path/hash, DOUBLE thresholds, status and optional evaluation/activation/retirement metadata. | Referenced parents and historical consumers restrict deletion. Database checks type, status and threshold ranges. Services verify artifacts and compatibility, and must ensure only one active core model of each type. |

#### Human review and operations

| Table and purpose | Important fields, keys and relationships | Deletion and business rules |
|-|-|-|
| sim_human_review — assigned review | Unique public ID; optional case/variant logical references with exactly one target; required reviewer-user logical reference; condition, purpose, visibility, protocol text and status; nullable decision, reason, instruction and progress times. | Case/variant deletion requires explicit child cleanup; reviewer deletion is restricted. The current nullable composite unique key does not reliably prevent duplicate reviewer assignments. Services enforce assigned-user access, one primary slot, no pre-submission result leak and complete submissions. |
| sim_async_task — durable background work | Unique public ID; task/resource types; optional generic resource public ID; status, attempt limits, retry time and safe error metadata. No logical references. | AsyncTaskService explicitly cancels tasks for deleted resources, revokes leases and removes case-bearing payload, output and error content. Retain only permitted non-content operational metadata. The defined lease and recovery rules apply; a deleted resource cannot be resumed. |
| sim_idempotency_record — duplicate-safe operation result | Required user logical reference; unique user/operation-scope/key triplet; request hash; nullable HTTP status and response JSON; required expiry. | User deletion requires explicit child cleanup. No case logical reference exists. Services must distinguish a running operation from a finished one, reject changed content under the same key and prevent cached responses from restoring deleted case content. |
| sim_audit_event — non-content operational history | Optional actor-user logical reference; action, resource type, optional resource_ref_hash, status and optional safe details. | Removed actor becomes NULL. There is no logical case link. Services exclude CV facts, research values, review reasons and secrets from generic audit data. |
| sim_privacy_deletion_receipt — evidence of cleanup | Case-public-ID hash, deletion type, deletion time and cleanup status. No logical reference and no public-ID unique key. | Survives case deletion independently. The service writes only non-content evidence and makes repeated cleanup safe. COMPLETE must mean all required storage cleanup succeeded. |

### Exact retained unique and check declarations

Every table retains PK id. The keys below are retained declarations, with business-rule fixes identified separately. An empty cell means no additional declaration of that kind.

| Table | Retained UNIQUE column groups | Retained CHECK names |
|-|-|-|
| sim_matching_rule_version | version_code | ck_sim_matching_rule_status |
| sim_feature_schema_version | version_code | ck_sim_feature_schema_dim; ck_sim_feature_schema_status |
| sim_bias_protocol_version | version_code | ck_sim_bias_unknown_zero; ck_sim_bias_protocol_status |
| sim_protocol_version | version_code | ck_sim_protocol_t_rule; ck_sim_protocol_status |
| sim_dataset_source | public_id; name, version_label |  |
| sim_job | public_id | ck_sim_job_source_type; ck_sim_job_status |
| sim_job_version | public_id; job_id, version_no | ck_sim_job_weight_skills; ck_sim_job_weight_experience; ck_sim_job_weight_education; ck_sim_job_weight_languages; ck_sim_job_weight_projects; ck_sim_job_version_status |
| sim_job_requirement |  | ck_sim_job_req_type; ck_sim_job_req_mandatory; ck_sim_job_req_public |
| sim_research_profile | public_id; dataset_source_id, source_record_id; base_profile_key | ck_sim_research_profile_review |
| sim_research_profile_attribute | research_profile_id, attribute_type, source_type | ck_sim_research_attr_type; ck_sim_research_attr_source |
| sim_corpus_version | version_code | ck_sim_corpus_profile_count; ck_sim_corpus_job_count |
| sim_corpus_profile_member | corpus_version_id, research_profile_id | ck_sim_corpus_partition |
| sim_corpus_job_member | corpus_version_id, job_version_id | ck_sim_corpus_job_heldout |
| sim_training_case |  | ck_sim_training_partition; ck_sim_training_g; ck_sim_training_neutral_label; ck_sim_training_biased_label; ck_sim_training_objective_score; ck_sim_training_biased_score; ck_sim_training_target_mode |
| sim_training_experiment | public_id | ck_sim_training_experiment_type |
| sim_training_run | training_experiment_id, seed | ck_sim_training_run_status |
| sim_model_version | version_code | ck_sim_model_threshold; ck_sim_model_common_threshold; ck_sim_model_status; ck_sim_model_type |
| sim_assessment_case | public_id | ck_sim_case_processing_status; ck_sim_case_human_status; ck_sim_case_privacy_status |
| sim_case_snapshot | case_id |  |
| sim_case_research_attribute | case_id, attribute_type | ck_sim_case_research_attr_type; ck_sim_case_research_source; ck_sim_case_research_confirmed |
| sim_case_temp_artifact |  | ck_sim_case_artifact_type; ck_sim_case_artifact_status |
| sim_case_access_grant | case_id, user_id, access_type | ck_sim_case_access_type |
| sim_matching_result | case_id | ck_sim_matching_g |
| sim_objective_result | case_id | ck_sim_objective_t_rule; ck_sim_objective_score; ck_sim_objective_decision |
| sim_ml_result | case_id, model_type | ck_sim_ml_result_type; ck_sim_ml_score; ck_sim_ml_threshold; ck_sim_ml_common_threshold; ck_sim_ml_decision; ck_sim_ml_common_decision; ck_sim_ml_status |
| sim_experiment | public_id | ck_sim_experiment_type; ck_sim_experiment_controlled_attr; ck_sim_experiment_status |
| sim_experiment_subject |  | ck_sim_experiment_subject_one_source |
| sim_experiment_subject_assessment | experiment_subject_id | ck_sim_exp_subject_assessment_g; ck_sim_exp_subject_assessment_score; ck_sim_exp_subject_assessment_decision |
| sim_experiment_variant | experiment_subject_id, variant_no | ck_sim_experiment_variant_changed_attr |
| sim_experiment_result | variant_id, model_version_id | ck_sim_experiment_result_type; ck_sim_experiment_result_score; ck_sim_experiment_result_decision; ck_sim_experiment_result_common_decision |
| sim_experiment_metric |  |  |
| sim_human_review | public_id; case_id, experiment_variant_id, condition_type, reviewer_user_id | ck_sim_human_review_one_target; ck_sim_human_review_condition; ck_sim_human_review_purpose; ck_sim_human_review_candidate_visible; ck_sim_human_review_status; ck_sim_human_review_decision |
| sim_async_task | public_id | ck_sim_async_task_status; ck_sim_async_task_attempt |
| sim_idempotency_record | user_id, operation_scope, idempotency_key |  |
| sim_audit_event |  |  |
| sim_privacy_deletion_receipt |  | ck_sim_privacy_deletion_type; ck_sim_privacy_cleanup_status |

## Completion rules to implement

These additions complete business rules beyond the retained physical declarations. They are proposed migration/service changes, not already installed guarantees. They add no physical foreign keys.

| Area | Concrete field/key rule | Required service behaviour |
|-|-|-|
| Duplicate human assignment | Replace the ineffective nullable combined target key with separate unique case/condition/reviewer and variant/condition/reviewer keys. Keep the exclusive-target check. | Lock and validate the target and reviewer; reject duplicate assignment. MySQL unique keys permit multiple NULL values, so one key containing both exclusive nullable targets is insufficient. |
| Candidate-visible primary slot | Add nullable VIRTUAL generated primary_case_id for visible PRIMARY rows; make primary-case/condition unique. Require those rows to target a case. | Return only this slot to the Candidate. Keep research repetitions separate. |
| Review state | Check SUBMITTED requires a decision, nonblank reason and submission time; PENDING/OPENED decisions remain NULL. | Validate assigned-user access and a 1–2000 character reason. If one person reviews both conditions, hide AI and earlier answers through every result-reading route until both submissions exist. |
| Active model slot | Add a nullable VIRTUAL generated active_core_type for ACTIVE NEUTRAL/BIASED rows and make it unique. | Lock the core slot; retire the previous version and activate its checked replacement atomically. Validate correct model type, schema and protocol for every case reference. |
| Model release | Retain TRAINED, VALIDATED, TESTED, APPROVED, ACTIVE and RETIRED states. | Register an offline tested package only after server checks pass, saving TESTED. Approval requires TESTED and produces APPROVED; activation requires APPROVED. |
| Failed prediction | Keep success-only prediction writes; tighten the result-status check to SUCCESS in a later migration. | Store technical failures in task/case status and safe error metadata. Never insert zero or Reject as a substitute. Preserve successful outputs on retry. |
| Active job criteria | Add explicit per-version active criteria, with one setting per criterion. | Validate active criteria, positive matching denominators, confirmed mandatory facts, inactive weight zero and active weights summing to 1. An active zero-weight criterion is still distinct from an inactive one. |
| Published jobs | Add a conditional published-weight-sum check and require published_at; retain revision_no. | Lock/recheck editable status and revision. Later edits create a new version. Referenced historical versions cannot be hard-deleted. |
| Training membership | Keep ordinary corpus/profile/job IDs and add unique membership keys; add no foreign keys. | Under parent/member locks, require corpus-profile membership, identical partition and corpus-job membership before saving a generated sample. Preserve grouped person splits. |
| Generated sample identity | Add a stable unique identity over corpus, profile, job, weight setting, variant, target mode, protocol and schema. | Repeated generation verifies or returns the existing sample; conflicting content under the same identity fails. |
| Expiry and retention | Use creation +24 hours for unconfirmed cases. Set purge_after to confirmation +30 days; after successful automation use completion +30 days. | Include unconfirmed extraction failures and confirmed unfinished cases. Do not silently extend expiry on re-upload. Earlier deletion takes priority. |
| Worker ownership | Add worker_id, lease_token, lease_until and heartbeat_at, with state/lease-expiry index. | Claim atomically, renew leases and require the current token on save/complete. Expired or privacy-revoked workers cannot commit. Recover abandoned work safely. |
| Idempotency | Add explicit operation status and safe resource reference, retaining request hash, unique operation key and expiry. | Distinguish in-progress from complete; reject changed requests; check access/privacy before replay; clear case-bearing cached responses during deletion. |
| Experiment subjects | Add unique experiment/profile and experiment/case keys, with exclusive-source check. | Validate source existence, access and matching saved job before insertion; lock the source parent until commit. |
| Metric identity | Add a complete metric-run identity or complete unique identity including experiment, metric, group and parameter configuration. | Replace one run's metrics atomically. Remove or recompute results affected by deleted Candidate subjects. |
| Deletion receipt | Keep case hash without a physical or logical case link; add an operation identity for retry-safe final receipts. | COMPLETE means every required content store is clean. Write the final receipt and case deletion together only after cleanup succeeds. |

No database foreign keys means the service protocol is mandatory, not optional extra validation. Unique keys still protect concurrent duplicate slots, while business services protect parent existence, allowed references and deletion order. Domain-specific state checks remain necessary alongside generic reference checks.

## Migration and acceptance plan

The reviewable proposed schema removes all 65 foreign-key declarations and their actions while retaining reference columns, existing explicit indexes, all 36 primary keys, 34 additional unique constraints and 80 checks. It also adds 28 ordinary leading-reference indexes where the previous foreign keys would have caused implicit index creation. These indexes support parent-use checks and explicit cleanup; they enforce no relationship. It is an unapplied derived design. If a prior migration has already run, introduce a new migration that removes the existing named constraints; do not replace migration history. The business-rule additions above require their own reviewed migration changes.

Pin an enforcing MySQL version, verify the reused identity-column types and install the proposal in a clean test database. Confirm zero FOREIGN KEY entries and the expected PK/UNIQUE/CHECK counts. Exercise both service validation and the absence of automatic database deletion. Direct database insertion of an orphan may succeed by design; the application must reject it before committing.

Integration tests must cover concurrent child insertion versus parent deletion, reference reassignment, blocked history deletion, all explicit child-cleanup paths, nullable actor clearing, no duplicate primary review, model activation races, result-preserving retries and cancellation during processing. Test an account deletion through the RuoYi integration path as well as SimRecrut endpoints. Validate dependency cleanup in one final transaction and file/cache recovery after failure. Backups and restoration must respect deletion receipts so removed Candidate content does not silently re-enter normal access.

Static checks verify the derived declarations and complete 65-link inventory. Migration execution and concurrent integration tests remain implementation work.

# From upload to result

The Candidate-facing route and API sequence below connects the business flow to the real page and service layers. The full swimlane appears in Functional flow.

| Point | Browser action and HTTP request | What the system does | When it can continue |
|-|-|-|-|
| Select job | Start simulation → POST /api/v1/candidate/cases | Check published jobVersionId; create owned case and temporary expiry | Response contains caseId; navigate to its upload page |
| Upload | POST /api/v1/candidate/cases/{caseId}/cv | Validate PDF; restricted workspace; persist extraction task | 202 means accepted for processing, not extracted |
| Extract | GET .../{caseId}/status every 2 seconds | PDFBox → OCR if needed → redaction → DeepSeek facts → schema validation → draft/evidence | Only REVIEW_REQUIRED opens the review step |
| Correct | GET/PUT .../{caseId}/draft; separate research-attribute API | Candidate checks source evidence and fixes facts; optional unknowns stay unknown | All facts needed by active criteria are resolved |
| Confirm | POST .../{caseId}/confirm | Access/state/repeat-request checks; transaction saves confirmed snapshot; after commit, remove raw and intermediate text | Successful save gives CONFIRMED; cleanup can retry independently |
| Run | POST .../{caseId}/run | Freeze versions; validate compatibility; match; compute Objective plus two independent model outputs; save each | 202, then status polling; no model retraining |
| See results | GET .../{caseId}/results | Assemble three saved automated outputs and two primary Human slots | AUTOMATED_COMPLETE requires all three automated results; Human slots may remain pending |
| Human review | Research assignment → assigned reviewer submission | Hide automatic/other answers; save decision and reason; refresh permitted result cards | Unassigned and Pending have no Accept/Reject value |
| Delete | DELETE .../{caseId} or expiry | Revoke access; cancel pending reviews; clean linked content; retain only non-content receipt | Show processing until cleanup succeeds; report final deletion afterwards |

A reload resumes from server state. Repeating a valid operation uses its original operation key. A retry resumes failed work with the recorded versions. Invalid input goes back to correction, and service failure stays a technical error. The response requestId connects an error to operational logs without exposing CV text or an internal stack trace.

# Five decisions, data and model training

## What the five decisions mean

All five methods assess the same retained professional facts and the same published job version. Accept means the method accepts the case under its own rule or instruction. It is a simulator outcome.

| Result | How it is obtained | Score and decision | Record kept |
|-|-|-|-|
| Objective Rule | The Java rule engine calculates job matches, applies saved HR weights and checks mandatory conditions. | A professional fit score from 0 to 100. Accept requires all mandatory conditions and the rule cutoff. | Matches, contributions, mandatory checks, rule cutoff, decision and job/rule versions. |
| Neutral AI | The trained Neutral network receives professional inputs. The service fills all research positions with zero. | An acceptance score from 0 to 1; the saved Neutral cutoff converts it to Accept/Reject. | Input schema, model version, score, cutoff and prediction. |
| Biased AI | A separate network receives the same professional inputs plus the controlled research categories. It was trained on deliberately changed labels. | An acceptance score from 0 to 1; the saved Biased cutoff converts it to Accept/Reject. | The same fields as Neutral, with the restricted research condition. |
| Standard Human | An assigned reviewer sees the job requirements and professional case, then follows the standard rubric. | Accept/Reject and a short reason. There is no invented numeric score. | Reviewer, condition, case versions, decision, reason and submission time. |
| Instructed-Bias Human | An assigned reviewer sees the same case and the explicit experimental bias instruction. | Accept/Reject and a short reason under that instruction. | The same review fields, including the instruction/protocol version. |

Standard Human names a review condition. It does not guarantee that a person is free from bias. A missing assignment is **Not assigned**; an unfinished task is **Pending**; an execution problem is **Failed**. These states have no Accept/Reject value. A primary review supplies each Candidate-visible human result; repeated research ratings stay separate.

The Objective score and AI acceptance scores measure different things. A fit score of 72.5/100 and an AI score of 0.80 must be labelled separately. The AI prediction uses its learned score and cutoff. Although mandatory status is an input, a neural network can still predict Accept when a mandatory condition fails. Report that disagreement; do not silently replace the research prediction with the Objective decision.

## Objective Rule: exact matching and score

<whiteboard token="Mhk1wy8Q7hhaaVbPKK1jZzygpze"></whiteboard>

Use five categories in the fixed order Skills, Experience, Education, Languages and Projects. For candidate $c$ and job $j$, write their match vector as $m(c,j)=(m_s,m_x,m_e,m_l,m_p)$. Every active match lies between 0 and 1. HR reviews the requirements, accepted equivalents and active categories before publication.

| Match | Formula | Meaning and boundary |
|-|-|-|
| Skills | $m_s=\frac{\|R_s(j)\cap E_s(c)\|}{\|R_s(j)\|}$ | Fraction of assessed skills supported by evidence. $R_s$ is the reviewed assessed set; $E_s$ is the supported candidate set. Active denominator must be positive.  [F01] |
| Experience | $m_x=\min\left(\frac{y_c(j)}{y_j},1\right)$ | Relevant years divided by target years. Merge overlapping relevant periods first; require $y_j>0$. A mandatory minimum is checked separately.  [F02] |
| Education | $m_e=\mathbb{1}[D(c)\succeq D_{\min}(j)\ \land\ M(c)\in M_{\rm acc}(j)]$ | Check degree level and accepted subject. Omit the subject test when unrestricted. Use a reviewed degree ordering; university prestige is excluded.  [F03] |
| Languages | $m_l=\frac{1}{\|L_j\|}\sum_{\ell\in L_j}\mathbb{1}[\operatorname{level}_c(\ell)\succeq\operatorname{level}_j(\ell)]$ | Fraction of required languages meeting the level. Define how labels such as fluent map to the agreed ordering before calculating.  [F04] |
| Projects | $m_p=\frac{\|R_p(j)\cap E_p(c)\|}{\|R_p(j)\|}$ | Fraction of explicit project conditions with evidence. Count one satisfied requirement once, even if several excerpts support it.  [F05] |

$\mathbb{1}[\cdot]$ is 1 when a stated condition is true and 0 otherwise. The sets and equivalence tables are saved with a matching-rule version. An optional qualification enters the assessed set only when HR explicitly selects it. A company technology list is not automatically a candidate requirement.

For the active set $A_j$, HR weights satisfy:

$$w_i\geq0,\qquad \sum_{i\in A_j}w_i=1,\qquad i\notin A_j\Rightarrow(m_i,w_i)=(0,0).$$

Formula F06.

An inactive category means not assessed. An unresolved fact needed for an active criterion returns to review; it is not converted into failure or zero. No active categories means the job configuration is invalid.

Let $M_j$ contain the mandatory requirements. Each confirmed check $q_r$ is 1 if met and 0 if not met. Define:

$$g(c,j)=\prod_{r\in M_j}q_r(c,j),\qquad S(c,j,w)=\sum_{i\in A_j}w_i m_i(c,j).$$

Formula F07.

The empty product is 1 when the job has no mandatory condition. An unresolved check blocks scoring. The Objective decision is:

$$d_O=\mathbb{1}[g=1\ \land\ S\geq T_{\rm rule}],\qquad \operatorname{displayedFit}=100S.$$

Formula F08.

The calculation is deterministic after requirements, weights, matching rules and cutoff are fixed. The rubric and cutoff remain human design choices; deterministic does not mean universally correct.

### How we choose the rule cutoff

Use at least 20 reviewed candidate-job pilot cases from development profiles. Two reviewers independently apply the same professional rubric without seeing $S$. Resolve disagreements and record the agreed reference label $r_k$. Keep these pilot profiles out of the final test set; report agreement and the small sample size.

Try thresholds between adjacent distinct pilot scores, and include 0 and 1 as boundary candidates. If all scores are identical, these boundaries still provide candidates. Calculate:

$$\operatorname{BA}(t)=\frac{1}{2}\left(\frac{TP(t)}{TP(t)+FN(t)}+\frac{TN(t)}{TN(t)+FP(t)}\right).$$

Formula F09.

For each tested cutoff, predict $\mathbb{1}[g=1\ \land\ S\geq t]$ before counting TP, TN, FP and FN against the reviewer labels. Here Accept is the positive class. Balanced accuracy gives equal importance to recognising Accept and Reject. Use a pilot containing both classes; if a denominator is zero, report the undefined metric and expand/review the pilot rather than inventing a score. Maximise balanced accuracy, use macro-F1 to break ties, then choose the middle of a remaining tied threshold interval. Save the tested thresholds and scores. The standard metric definitions are in [scikit-learn's evaluation documentation](https://scikit-learn.org/stable/modules/model_evaluation.html) [R08].

For a class c, F1 combines precision and recall; macro-F1 gives the two classes equal weight:

$$F1_c=\frac{2TP_c}{2TP_c+FP_c+FN_c},\qquad \operatorname{macroF1}=\frac{F1_{\rm Accept}+F1_{\rm Reject}}{2}.$$

Formula F10.

Counts are calculated by treating each class as positive in turn. If the pilot does not support both classes, expand it before selecting a cutoff [R08].

Freeze $T_{\rm rule}$ before generating final labels. Also show how results change within a five-percentage-point window around it. A cutoff of 0.70 is only the worked example below. This selection procedure is our project protocol, not a threshold prescribed by a recruitment paper.

## Data sources and what they supply

| Source | Verified content | Our use | What it does not supply |
|-|-|-|-|
| [Djinni English job descriptions](https://huggingface.co/datasets/lang-uk/recruitment-dataset-job-descriptions-english) [R05] | Job title, description, experience/language fields and identifiers; the data card declares MIT. | Select IT roles and review their explicit requirements. | The final decision for a named candidate applying to that job. |
| [Djinni English candidate profiles](https://huggingface.co/datasets/lang-uk/recruitment-dataset-candidate-profiles-english) [R06] | Profile text, highlights, job preferences, experience/language fields and identifiers; the data card declares MIT. | Prepare professional profiles and plausible candidate-job pairs. | Original application PDFs or linked individual hiring outcomes. |
| [Snehaan Bhawal's Resume Dataset](https://www.kaggle.com/datasets/snehaanbhawal/resume-dataset) [R07] | Resume PDFs and CSV fields ID, text, HTML and occupational category; the publisher declares CC0 and identifies LiveCareer examples as the source. | Check PDF extraction, field accuracy and review screens. Inspect which PDFs actually need OCR. | Verified hiring outcomes or a measured demographic ground truth. |

The Djinni collection is described by Drushchak and Romanyshyn in [Introducing the Djinni Recruitment Dataset, UNLP 2024](https://aclanthology.org/2024.unlp-1.2/) [R04]. Our English subsets are the linked data cards; counts for the whole bilingual corpus must not be presented as the selected training sample.

Start by manually checking 20 IT CVs and 20 IT jobs. For every dataset save its URL, declared licence, download date/revision, source IDs, selection criteria and retained-record count. Data cards establish published contents and licence declarations; they do not guarantee that every CV is complete or correct.

The preparation chain is **source record → duplicate removal → extracted fields → reviewed professional profile/job → plausible pair → numeric features and labels**. Keep evidence, original versus normalised values, missing/conflicting status and corrections. Skill aliases, degree equivalence, language levels and job-family compatibility are reviewed tables. Unknown fields remain unknown until resolved for the selected assessment.

Candidate-job pairs are constructed for the experiment. Match compatible IT job families first, then sample low, middle and high professional match levels so the dataset is not dominated by obvious mismatches. This is not a reconstruction of historical applications.

Controlled school, referral, gender and ethnicity values are created or explicitly provided for study. They are separate from observed professional facts. Never derive ethnicity or gender from a name or photograph. Live application uploads remain outside the offline training dataset.

## Prepare the exact inputs and labels

One row represents a reviewed base profile, a selected job version, a weight configuration and an experimental variant. Set $v=m\odot w$, where $\odot$ means element-by-element multiplication. The shared professional block has 16 values:

$$u=[m_1,\ldots,m_5,\ w_1,\ldots,w_5,\ v_1,\ldots,v_5,\ g]\in\mathbb{R}^{16}.$$

Formula F11.

The research block $z$ has 16 positions: school category 3, referral 3, gender 3 and ethnicity 7. The fixed order is school = [prestigious, ordinary, unknown]; referral = [yes, no, unknown]; gender = [woman, man, unknown]; ethnicity = [east_asian, south_asian, black, white, middle_eastern_or_north_african, mixed_or_other, unknown]. Each family has one selected position, including an unknown category. The controlled categories are limited study settings; they are not a complete classification of human identity. Save the exact category order with the schema. Unknown has a zero direct synthetic effect and must appear in training examples.

$$x_N=[u,\mathbf{0}_{16}],\qquad x_B=[u,z],\qquad x_N,x_B\in\mathbb{R}^{32}.$$

Formula F12.

The Neutral API receives only $u$ and creates the zero mask inside Python. The Biased API receives $u$ plus permitted category values; Python encodes them using the saved schema. Neutral zeros mean the information is withheld, not that every category is unknown. Do not put $Y_N$, $Y_B$, the biased score or the bias effect into the model inputs.

There are no reliable linked hiring outcomes in the selected sources, so both labels are calculated **before** training:

$$Y_N=\mathbb{1}[g=1\ \land\ S\geq T_{\rm rule}].$$

Formula F13.

For four research families $E$, specify and version each controlled effect $\Delta_e$. Then:

$$\Delta(z)=\sum_{e\in E}\Delta_e(z_e),\qquad S_B=\min(1,\max(0,S+\Delta(z))).$$

Formula F14.

$$Y_B=\mathbb{1}[g=1\ \land\ S_B\geq T_{\rm rule}].$$

Formula F15.

The main Biased model contains the configured combined effects. A focused comparison changes one attribute while holding all other values fixed. Effect directions are written in the experimental protocol; there is no implied universal ranking of groups.

To choose effect sizes, measure $d_k=|S_k-T_{\rm rule}|$ on training/calibration cases with $g=1$. Use its 25th, 50th and 75th percentiles as small, medium and large study scales. The initial combined run uses the medium scale, then checks score clipping and label-flip rates; reduce combined magnitudes before freezing if effects overwhelm the professional score. Unknown always contributes zero. This margin-based choice is our experimental convention, not a measurement of real discrimination or a result borrowed from FairCVtest.

### One worked row

| Category | Match | HR weight | Contribution |
|-|-|-|-|
| Skills | 0.75 | 0.40 | 0.300 |
| Experience | 0.50 | 0.25 | 0.125 |
| Education | 1.00 | 0.15 | 0.150 |
| Languages | 1.00 | 0.10 | 0.100 |
| Projects | 0.50 | 0.10 | 0.050 |
| Total |  | 1.00 | 0.725 |

With $g=1$ and the example cutoff 0.70, $Y_N=1$. With a purely illustrative effect of $-0.08$, $S_B=0.645$ and $Y_B=0$. The professional inputs have not changed.

| Part of the example | Values or meaning |
|-|-|
| Five professional matches | Skills 0.75; experience 0.50; education 1.00; languages 1.00; projects 0.50 |
| Five HR weights | 0.40; 0.25; 0.15; 0.10; 0.10 in the same order |
| Five weighted matches | 0.300; 0.125; 0.150; 0.100; 0.050 |
| Mandatory value | 1: every mandatory check passes |
| Shared professional block | The five matches, five weights, five weighted matches and mandatory value: 16 numbers |
| Neutral input | Those 16 numbers followed by 16 zero values: 32 numbers |
| Biased input | The same professional block followed by the 16-position category encoding |
| Example research values | Ordinary school; no referral; gender unknown; ethnicity unknown; use the saved category order |
| Neutral label | 1, because the weighted score is 0.725, the example rule cutoff is 0.70 and mandatory checks pass |
| Biased label | 0 only under the explicitly hypothetical combined effect −0.08, giving 0.645 |
| Where labels go | Each label is passed separately to the loss calculation; neither label is part of the input |

These are two supervised training examples. The training label is given to the loss function alongside the network output; it is never concatenated into $x$.

## The actual network

<whiteboard token="UWNLwHDKShKlfCbouV8jdnyvpUb"></whiteboard>

Both models use the same chosen small multilayer network, with separately trained parameters. Its exact sequence is **32 input values → 16 learned hidden values → ReLU → 10% dropout → 8 learned hidden values → ReLU → one raw score**. For a column input $x$:

$$h_1=\operatorname{ReLU}(W_1x+b_1),\qquad W_1\in\mathbb{R}^{16\times32},\quad b_1\in\mathbb{R}^{16}.$$

Formula F16.

$$\widetilde{h}_1=D_{0.1}(h_1),\qquad h_2=\operatorname{ReLU}(W_2\widetilde{h}_1+b_2),\quad W_2\in\mathbb{R}^{8\times16}.$$

Formula F17.

$$a=W_3h_2+b_3,\qquad W_3\in\mathbb{R}^{1\times8},\qquad p=\sigma(a)=\frac{1}{1+e^{-a}}.$$

Formula F18.

ReLU applies $\max(0,t)$ to each value. $D_{0.1}$ is PyTorch's training dropout: randomly skip 10% of hidden values and scale retained values by $1/0.9$. In evaluation/inference it is the identity, so all values are used. The two remaining bias vectors have sizes 8 and 1. The raw output $a$ is called a logit; sigmoid turns it into the displayed acceptance score $p$.

$$N_{\rm parameters}=(32\cdot16+16)+(16\cdot8+8)+(8\cdot1+1)=673.$$

Formula F19.

HR weights $w$ are fixed input values for a row. $W_1,W_2,W_3$ and the layer biases are learned parameters. Each model owns its own 673 parameters. The hidden sizes are our starting design for the planned small dataset; the FairCVtest paper does not prescribe them. A logistic-regression baseline is retained only as an evaluation comparator to check whether the additional network layers help.

At prediction time:

$$\widehat{Y}_N=\mathbb{1}[p_N(x_N)\geq T_N],\qquad \widehat{Y}_B=\mathbb{1}[p_B(x_B)\geq T_B].$$

Formula F20.

$T_N$ and $T_B$ are saved model-specific cutoffs chosen on validation data. They are different from $T_{\rm rule}$, which created the labels. Even after calibration against synthetic labels, $p$ is an acceptance score and does not represent a real hiring probability.

## How X and Y enter training

<whiteboard token="Zb3YwD9qdhv655bxfdEjhIZ3pab"></whiteboard>

Split by original profile ID before creating job-weight/research variants. Use 60% training, 20% validation and 20% final test. Keep every version of a person in one split. Threshold-calibration pilot profiles belong to development data. Fit any data-derived mapping on training records only, then freeze it before applying it to validation and test records.

The working target is 500 base profiles: 300/100/100. With one compatible job, three distinct weight settings and two study variants each, this gives up to 3,000 rows, not 3,000 independent people. Use an HR-reviewed baseline, equal active weights, and a priority swap between the two largest unequal weights. Omit a duplicate or meaningless alternative. The target count and split are project choices, subject to usable reviewed data.

For one model, store $X\in\mathbb{R}^{N\times32}$ and $Y\in\{0,1\}^{N\times1}$ with the same row order. Float32 tensors feed batches of at most 32 rows: Each batch contains B rows of 32 inputs and B rows of one label. Use Y_N with X_N and Y_B with X_B.

The binary cross-entropy objective for a batch is:

$$\mathcal{L}(\theta)=-\frac{1}{B}\sum_{k=1}^{B}\left[y_k\log p_k+(1-y_k)\log(1-p_k)\right].$$

Formula F21.

BCEWithLogitsLoss computes this from raw logits using a stable combined operation. Pass the raw logit directly to that loss; apply sigmoid for inference. This behaviour is defined in [PyTorch's official loss documentation](https://docs.pytorch.org/docs/2.14/generated/torch.nn.BCEWithLogitsLoss.html) [R12].

| Step | What happens | Values and shape |
|-|-|-|
| Prepare one model's data | Keep inputs and labels in the same row order | N rows of 32 input values; N binary labels, stored as floating-point 0 or 1 |
| Make batches | Shuffle training rows and take up to 32 examples | B by 32 inputs and B by 1 labels; the final batch may be smaller |
| Create a fresh model | Start independent learned parameters for Neutral and Biased | 32 inputs → 16 hidden values → ReLU → 10% dropout → 8 hidden values → ReLU → 1 raw output |
| Predict a batch | Pass its input rows through the model | One raw score, or logit, per row |
| Calculate loss | Compare raw scores with the correct Y_N or Y_B labels using BCEWithLogitsLoss | Do not apply a separate sigmoid before this loss |
| Calculate changes | Clear previous batch gradients, then backpropagate the current loss | Gradients describe how learned weights and biases affect loss |
| Update learned values | Adam changes network parameters | Starting learning rate 0.001; weight decay 0.0001; HR weights remain input values |
| Finish an epoch | Process all training batches once | Maximum 100 epochs |
| Check validation | Turn dropout off and do not update weights | Calculate validation loss and scores after each epoch |
| Save/stop | Keep the lowest-validation-loss weights; stop after 10 epochs without improvement | Do not automatically keep the last epoch |
| Choose a cutoff | Use validation labels and the recorded selection rule | A separate cutoff for each model; also compare at 0.5 |
| Repeat and release | Record five seeds, freeze choices, evaluate final test and save the package | Test results do not choose settings or the best seed |

Repeat the procedure from a fresh model and optimizer for the other label set. Save the best validation checkpoint and complete settings for each run. PyTorch documents the batch handling in its [network tutorial](https://docs.pytorch.org/tutorials/beginner/nn_tutorial.html) [R14] and the gradient/update sequence in its [optimisation tutorial](https://docs.pytorch.org/tutorials/beginner/basics/optimization_tutorial) [R13].

### Parameter selection and model release

| Choice | Starting value | Selection and reason |
|-|-|-|
| Layer sizes | 32 → 16 → 8 → 1 | Fixed core model for this implementation; compare against the simpler baseline. Make any later architecture change an explicit new experiment. |
| Learning rate | 0.001 | Start here; proposed small validation search: 0.0003, 0.001, 0.003. These candidates are our development settings. |
| Weight decay | 0.0001 | Discourage very large learned weights. Proposed comparison: 0 versus 0.0001 after the learning-rate check. |
| Dropout | 0.1 | Reduce reliance on individual hidden units. Compare 0 versus 0.1 on validation; keep it off during prediction. |
| Batch size | 32 | A small batch for the planned dataset; retain for the initial search. The last batch can be smaller. |
| Training length | Maximum 100 epochs; patience 10 | Save minimum-validation-loss weights. Stop after 10 epochs without improvement; do not select the last epoch automatically. |
| Random initialisation | Five recorded seeds for the selected settings | Report mean and standard deviation. Compare models on the same split and matched seed list. |
| Model cutoffs | Selected separately on validation | Maximise balanced accuracy against each model's own labels; macro-F1 and the middle of a tied interval break ties. Also report both at 0.5. |

Use a small recorded search, not every possible combination. Select training settings by validation loss, record balanced accuracy and macro-F1, then choose the cutoff. Repeated use of one validation set can overfit; report the number of tried settings and keep the search limited. Final test data is untouched until settings, cutoffs and protocol are fixed. If the reviewed sample is too small or one label class is absent, improve the dataset before claiming successful model validation.

Save weights, exact category order, matching/preprocessing versions, model cutoff, rule cutoff, bias-effect table, split IDs, seeds, hyperparameters, dataset manifest, dependency versions and final metrics. Admin activates only a compatible checked package; current cases/results keep their recorded versions. Live scoring does not train the model.

## Evaluation: what evidence will support the comparison

Report each model's accuracy, balanced accuracy, macro-F1 and confusion matrix against its own labels. Also compare both predictions with $Y_N$ as the shared rule-qualified reference. The latter measures agreement with our professional rule; it is not real-world hiring accuracy.

For a controlled pair differing only in one research attribute, calculate:

$$\Delta p_k=p(x_k^{\rm changed})-p(x_k^{\rm reference}),\qquad F=\frac{1}{n}\sum_{k=1}^{n}\mathbb{1}[\widehat{Y}_k^{\rm changed}\neq\widehat{Y}_k^{\rm reference}].$$

Formula F22.

Show score differences, decision-flip rate $F$, unchanged cases, job/weight versions and sample count. Objective and Neutral should stay unchanged when their actual professional inputs are identical. Test on unseen job configurations and weight settings as a separate check.

For group $a$, show selection rate and acceptance of rule-qualified cases:

$$\operatorname{SR}_a=\frac{\sum_{k\in G_a}\widehat{Y}_k}{|G_a|},\qquad \operatorname{TPR}_a=\frac{\sum_{k\in G_a}\mathbb{1}[Y_{N,k}=1\ \land\ \widehat{Y}_k=1]}{\sum_{k\in G_a}\mathbb{1}[Y_{N,k}=1]}.$$

Formula F23.

Always report the denominators. A group with no qualified cases has undefined TPR. Use Fairlearn MetricFrame for group metrics and counts, following its [official assessment example](https://fairlearn.org/v0.13/user_guide/assessment/perform_fairness_assessment.html) [R15]. A metric alone is not proof of overall fairness. For Top-K analysis, declare K and show selected composition relative to the original pool; K is a comparison capacity, not the model cutoff.

Human reviews remain a separate evaluation dataset. The proposed pilot is 20 cases × 2 reviewers × 2 conditions = 80 ratings. If a reviewer sees both conditions, balance their order and hide earlier/automated answers until both submissions; independent reviewers reduce recall of the earlier case. Report disagreement as well as agreement. The two core models change both labels and attribute access; optional two extra models form a 2×2 experiment to separate these factors. This is a planned extension, not part of the minimum release.

## Existing work, reuse and our extensions

| Verified reference | What it actually establishes | What we reuse | Our proposed extension and how to check it |
|-|-|-|-|
| Peña et al., [Bias in Multimodal AI: Testbed for Fair Automatic Recruitment, CVPR Workshops 2020](https://openaccess.thecvf.com/content_CVPRW_2020/html/w1/Pena_Bias_in_Multimodal_AI_Testbed_for_Fair_Automatic_Recruitment_CVPRW_2020_paper.html) [R01]; [public FairCVtest code](https://github.com/BiDAlab/FairCVtest) [R02] | Controlled synthetic recruitment targets and demographic bias experiments using multimodal profiles. | The idea of a known professional target and deliberately biased targets, then measuring learned effects. | Job-specific requirements/weights, deterministic mandatory checks and five parallel methods. Verify Java/Python rule agreement, unchanged-input comparisons and separate human results. |
| Peña et al., [Human-Centric Multimodal Machine Learning, SN Computer Science 2023](https://link.springer.com/article/10.1007/s42979-023-01733-0) [R03] | Recruitment testbed analysis of biased targets and sensitive information in multimodal inputs. | Distinguish target bias from visibility of background information. | Optional 2×2 label/visibility experiment. Compare all four settings on one split before attributing an effect to only one factor. |
| [Fairlearn assessment documentation](https://fairlearn.org/v0.13/user_guide/assessment/perform_fairness_assessment.html) [R15] | Group-level selection/error metrics and counts. | Use its tested metric implementation. | Add fixed-job, same-profile pairs and rule-qualified reference labels; report counts and study limitations. |
| [OpenCATS](https://github.com/opencats/OpenCATS) [R16] and [Odoo's recruitment flow](https://www.odoo.com/documentation/18.0/applications/hr/recruitment/recruitment-flow.html) [R17] | Applicant tracking, job/application records and recruitment stages. | Clear job/case pages and visible workflow states. | A research comparison with three automatic methods and two review conditions. Check the full case journey rather than claim these systems contain our model experiment. |

FairCVtest's multimodal model and synthetic dataset are different from our 32-input tabular classifier and reviewed job/profile sources. We borrow the experimental idea, not its trained weights or an asserted optimal architecture. Our proposed extensions aim to make job requirements, decision construction and case history inspectable. Their benefit will be demonstrated by tests and experiments; no accuracy or fairness improvement is claimed before results exist.

The [2023 FairCVtest experiments](https://link.springer.com/article/10.1007/s42979-023-01733-0) [R03] predict continuous target scores and report Adam, 16 epochs, batch size 128 and mean absolute error. Our target is a binary decision, so we use BCEWithLogitsLoss and select our smaller-batch training settings on validation data. Our professional block deliberately includes the five match–weight products: this supplies the rule contributions directly rather than asking a small network to discover multiplication from limited examples. The experiment studies how controlled labels and attribute access affect learned predictions; it does not establish that a learned model is needed to calculate a known deterministic rule.

# Work plan and responsibilities

Manage dates, dependencies and status in the [live Lark Gantt chart](https://ijppwkmdd5yg.jp.larksuite.com/base/Th8abtDnEafjH0sNhLcjr8dFpzh?table=tbl1CdOED2YDYCII&view=vew2hRFW2F). The Base contains 116 tasks and 12 milestones. Member names remain blank until allocation is agreed.

![Complete work plan: all 116 tasks and 12 milestones](https://feishu.cn/file/K1wubFEvXotZo9xDhl4jU235pGe)

The overview covers the full date range. Annexe contains every task by work slot; the live Base holds individual dependencies, status and completion criteria.

A view filter chooses which task rows appear, for example by member, status or date. All views share the same task records. The current Schedule Gantt Chart has no filter: all 116 tasks are included. Grouping by work area arranges the rows into sections.

The database and integration tasks include zero SQL foreign keys, service-owned reference checks, explicit deletion and insertion-versus-deletion tests. Primary/unique/check constraints remain in the database. These are planned completion criteria; they do not mark implementation as finished.

| Work slot | Main work | First handoff |
|-|-|-|
| A — \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_ | Data preparation, model training and research analysis | Agree reference cases with B; freeze ML inputs with D |
| B — \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_ | Backend, database, matching and Objective Rule | Agree typed API responses with C and ML request/response checks with D |
| C — \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_ | Candidate, HR, Research, Review and Admin pages | Build against agreed examples; prove route guards and state-dependent buttons |
| D — \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_ | PDF/OCR, structured extraction, integration, testing and deployment | Connect upload, redaction, model service and cleanup; test recovery |
| All four | Protocol choices, human pilot and final demonstration | Review one working journey and one reproducible experiment each week |

Tasks assigned to A–D are development responsibilities, not application access roles. Compare estimated effort as well as task counts and share work when a stage becomes a bottleneck.

| Target | Planned date |
|-|-|
| System and training plan agreed | 2 October 2026 |
| Reviewed CV/job pilot | 18 October 2026 |
| Upload-to-confirmation journey | 25 October 2026 |
| Training data ready | 5 November 2026 |
| Both core models trained | 15 November 2026 |
| Model cutoffs selected | 22 November 2026 |
| Human pilot | 27 November 2026 |
| Final experiments | 4 December 2026 |
| Integrated system | 6 December 2026 |
| Core release | 18 December 2026 |
| Submission | 8 January 2027 |
| Presentation | 15 January 2027 |

Ten tasks currently start before a listed prerequisite finishes. They remain marked **Schedule check** in the Base. Agree an explicit early handoff or move the start date; do not silently remove the dependency.

## Items to settle and verify

| Item | Concrete next action |
|-|-|
| Team allocation | Fill A–D names; check effort and the ten dependency overlaps |
| RuoYi/build setup | Select the exact branch, Java/Spring compatibility, physical module root and dependency versions; reuse its actual authentication endpoints |
| Page/API completion | Implement the proposed reads, selectors, retry and retirement contracts; define typed role-safe responses before connecting buttons |
| Rule calibration | Review pilot cases, settle equivalence tables and mandatory checks, then measure and freeze the rule cutoff |
| Bias protocol | Freeze category order, effect directions and measured development scales; keep unknown at zero direct effect |
| Model validation | Run the limited parameter search and five seeds; select cutoffs; only then report final test results |
| Human workload | Confirm whether 80 ratings are feasible, reviewer access and order balancing; retain one primary result per condition |
| Reliability/privacy | Prove restart recovery, duplicate-operation handling, version freezing and complete deletion including pending reviews |

The formulas, folders and proposed integration endpoints are specified. Actual calibrated cutoffs, bias magnitudes, trained performance and exact build versions still require the listed work. An example number is not a measured result.

## Work added after this design review

| Task | Owner | Dates | Completion scope |
|-|-|-|-|
| A008 — Define typed API responses for each role | Member D | 2026-10-03 to 2026-10-04 | Request/response fields, paging, errors and access rules are in OpenAPI; reviewers receive no automated scores before both condition submissions. |
| A009 — Add database constraints for reviews and active models | Member B | 2026-10-04 to 2026-10-05 | A new migration enforces case/variant review uniqueness, one primary review per case/condition, and one active core model per type; simultaneous-write checks pass. |
| H009 — Complete HR draft and version APIs | Member B | 2026-10-08 to 2026-10-10 | Draft reload, immutable version detail and new draft from a published version work with ownership, revision conflict and idempotency checks. |
| C013 — Complete Candidate job, history and deletion pages | Member C | 2026-10-19 to 2026-10-21 | Direct routes reload safely; only published jobs and owned retained cases appear; deletion blocks old browser content and shows cleanup status. |
| X009 — Complete research selectors and experiment read fields | Member B | 2026-11-02 to 2026-11-04 | Authorised job/model/protocol/profile/reviewer selectors and full frozen experiment reads have typed fields; grants and retention are rechecked. |
| I013 — Recover background tasks after a restart | Member B | 2026-10-15 to 2026-10-18 | Task claim records worker, lease token, expiry and heartbeat; expired claims resume safely; stale workers cannot commit or repeat completed stages. |
| I014 — Check task recovery and deletion during processing | Member D | 2026-10-19 to 2026-10-20 | Restart, timeout, double claim and deletion-during-extraction/scoring checks pass; no fabricated Reject or overwritten result is saved. |
| I016 — Complete model registration, approval and retirement APIs | Member B | 2026-11-12 to 2026-11-13 | Artifact manifest, checksum, schema, protocol and validation evidence are checked; approve/activate/retire preserve historical results and availability. |
| I015 — Complete Admin model, protocol and audit pages | Member C | 2026-11-16 to 2026-11-18 | Model details and registration/approval/retirement actions use supported APIs; protocol/audit reads and safe retry show metadata without Candidate content. |

All 116 tasks have a start date, due date, owner slot and completion criterion. Every dependency points to an existing task and the dependency graph has no cycle. The live Gantt has no filter hiding tasks. Ten recorded overlaps still require an early handoff or a date change; dates are a plan rather than evidence that work is finished.

# Speaking notes

1. We compare five results for one professional case and one fixed job version. The two Human names are review conditions; Reviewer is an assigned responsibility of HR or Researcher.
2. The business chain is job publication, upload, fact review, confirmation, three automated results, later human reviews and deletion. Pending or technical failure is never Reject.
3. The architecture has five parts: the RuoYi Vue 3 frontend, Spring Boot backend, storage, online ML and independent offline training. Vue is the View; Spring MVC controllers handle HTTP; business/domain/data-access code forms the backend Model part.
4. The application layer coordinates the use case. Domain code calculates matching and the Objective Rule. MyBatis mappers handle SQL. Business interfaces have concrete implementations; MyBatis creates mapper implementations. Adapters isolate OCR, DeepSeek and model HTTP calls; PDFBox is used directly.
5. Objective uses five reviewed match values, saved HR weights, mandatory checks and a calibrated rule cutoff. Neutral labels follow that rule; Biased labels use explicitly controlled effects.
6. The real model has 32 inputs and layers 16, 8 and 1. X contains inputs, Y contains prepared labels, and BCEWithLogitsLoss compares each batch's raw predictions with Y. Adam updates the learned parameters.
7. Split by original profile before making variants. Choose settings and cutoffs on development/validation data; keep final test profiles untouched.
8. FairCVtest supports the controlled-target approach. Our job-specific workflow and five-method comparison are proposed extensions whose benefit still needs evidence.
9. The Lark Base tracks 116 tasks, 12 milestones and ten dependency overlaps. We must assign names and agree the immediate handoffs.
10. MySQL stores 36 business tables with zero SQL foreign keys. The E/R diagram shows 65 logical relationships with entity connectors. Spring Boot business services check references and explicitly clean dependent rows. Redis holds temporary runtime data. Training produces versioned files; internal FastAPI inference returns scores to Spring Boot, which saves them.

<figure view-type="Preview"><source name="Meeting_3_Speaking_Notes_EN.md" mime="text/markdown; charset=utf-8" size="2192" token="XDD5bLQNQouVEDxuuebjZVudpHc"/></figure>

# Annexe — references and full task schedule

## How references are marked

R numbers identify external sources beside the relevant statement. F numbers identify formulas. The formulas table distinguishes established operations/metrics from rules defined for this project. The chosen match rules, gates, label effects, layer sizes, split, tie rules and retention defaults are project design choices; a linked paper does not prove they are optimal.

## Formula origins

| Formula | Meaning | Origin | Reference |
|-|-|-|-|
| F01 | Skills coverage | Project definition: reviewed sets, evidence and equivalence tables. Not copied from a paper. | Project matching protocol |
| F02 | Capped relevant experience | Project definition: reviewed sets, evidence and equivalence tables. Not copied from a paper. | Project matching protocol |
| F03 | Education level and subject check | Project definition: reviewed sets, evidence and equivalence tables. Not copied from a paper. | Project matching protocol |
| F04 | Required language coverage | Project definition: reviewed sets, evidence and equivalence tables. Not copied from a paper. | Project matching protocol |
| F05 | Project-condition coverage | Project definition: reviewed sets, evidence and equivalence tables. Not copied from a paper. | Project matching protocol |
| F06 | Active weights | Project definition, frozen with its rule/schema/protocol version | Project design |
| F07 | Mandatory gate and weighted fit | Project definition, frozen with its rule/schema/protocol version | Project design |
| F08 | Objective decision and display scale | Project definition, frozen with its rule/schema/protocol version | Project design |
| F09 | Balanced accuracy | Standard mean of positive- and negative-class recall | R08 |
| F10 | Class F1 and macro-F1 | Standard classification metric | R08 |
| F11 | Professional input block | Project definition, frozen with its rule/schema/protocol version | Project design |
| F12 | Neutral mask and Biased input | Project definition, frozen with its rule/schema/protocol version | Project design |
| F13 | Neutral training label | Project definition, frozen with its rule/schema/protocol version | Project label protocol; R01, R03 provide the controlled-target idea |
| F14 | Controlled effects and clipped score | Project definition, frozen with its rule/schema/protocol version | Project label protocol; R01, R03 provide the controlled-target idea |
| F15 | Biased training label | Project definition, frozen with its rule/schema/protocol version | Project label protocol; R01, R03 provide the controlled-target idea |
| F16 | Network layer computation | Standard affine/ReLU/sigmoid operations; our dimensions | R10, R11, R12 |
| F17 | Network layer computation | Standard affine/ReLU/sigmoid operations; our dimensions | R10, R11, R12 |
| F18 | Network layer computation | Standard affine/ReLU/sigmoid operations; our dimensions | R10, R11, R12 |
| F19 | 673 learned parameters | Arithmetic from our chosen layer dimensions | Project architecture; R10 |
| F20 | Model prediction cutoffs | Project definition, frozen with its rule/schema/protocol version | Project design |
| F21 | Binary cross-entropy | Standard loss; PyTorch combines it with sigmoid numerically | R12 |
| F22 | Same-profile score change and flip rate | Project pair-comparison definitions | Project evaluation; R01, R03 provide the experiment idea |
| F23 | Selection rate and true-positive rate | Standard group metrics | R09, R15 |

## Research, dataset, database and tool references

The sources below are publisher pages, official project repositories, data cards or official technical documentation. Access was checked during preparation, through direct pages or official indexed content where pages could not be fetched. Save each dataset's actual revision, license declaration and downloaded checksum when data work starts. Documentation versions cited here explain behaviour; they do not silently choose the application's build versions.

| Reference | Source | What it supports |
|-|-|-|
| R01 | [Peña et al. (2020), Bias in Multimodal AI: Testbed for Fair Automatic Recruitment](https://openaccess.thecvf.com/content_CVPRW_2020/html/w1/Pena_Bias_in_Multimodal_AI_Testbed_for_Fair_Automatic_Recruitment_CVPRW_2020_paper.html) [R01] | Controlled professional and deliberately biased target experiments; our exact matching and label rules are project definitions. |
| R02 | [BiDAlab, FairCVtest source repository](https://github.com/BiDAlab/FairCVtest) [R02] | Public implementation of the recruitment testbed; no reuse claim for our trained weights or 32-input network. |
| R03 | [Peña et al. (2023), Human-Centric Multimodal Machine Learning: Recent Advances and Testbed on AI-Based Recruitment](https://link.springer.com/article/10.1007/s42979-023-01733-0) [R03] | Continuous target experiments with Adam, 16 epochs, batch 128 and MAE. These are comparison settings, not our defaults. |
| R04 | [Drushchak and Romanyshyn (2024), Introducing the Djinni Recruitment Dataset](https://aclanthology.org/2024.unlp-1.2/) [R04] | Recruitment text corpus and provenance; not linked individual hiring outcomes. |
| R05 | [lang-uk, Djinni English job-description data card](https://huggingface.co/datasets/lang-uk/recruitment-dataset-job-descriptions-english) [R05] | Source fields and declared MIT license for the selected job source. |
| R06 | [lang-uk, Djinni English candidate-profile data card](https://huggingface.co/datasets/lang-uk/recruitment-dataset-candidate-profiles-english) [R06] | Professional profile text and declared MIT license; no original application PDF claim. |
| R07 | [Snehaan Bhawal, Resume Dataset](https://www.kaggle.com/datasets/snehaanbhawal/resume-dataset) [R07] | Publisher-listed resume files/CSV and license/source declaration. Selection and OCR needs require our own inspection. |
| R08 | [scikit-learn, model evaluation](https://scikit-learn.org/stable/modules/model_evaluation.html) [R08] | Balanced accuracy, F1 and classification metrics; our threshold-selection/tie procedure is a project protocol. |
| R09 | [Fairlearn, common fairness metrics and assessment](https://fairlearn.org/main/user_guide/assessment/common_fairness_metrics.html) [R09] | Selection rate and true-positive-rate definitions, group comparisons and their limits. |
| R10 | [PyTorch, Linear layer](https://docs.pytorch.org/docs/main/generated/torch.nn.Linear.html) [R10] | Affine-layer operation and dimensions; 32–16–8–1 sizes are our design. |
| R11 | [PyTorch, Dropout](https://docs.pytorch.org/docs/main/generated/torch.nn.Dropout.html) [R11] | Random zeroing and training scaling by one divided by one minus dropout rate; identity during evaluation. |
| R12 | [PyTorch, BCEWithLogitsLoss](https://docs.pytorch.org/docs/2.14/generated/torch.nn.BCEWithLogitsLoss.html) [R12] | Binary cross-entropy computed stably from raw logits. |
| R13 | [PyTorch, optimization tutorial](https://docs.pytorch.org/tutorials/beginner/basics/optimization_tutorial) [R13] | Loss, gradients and optimizer update procedure; our selected hyperparameters are project settings. |
| R14 | [PyTorch, neural-network tutorial](https://docs.pytorch.org/tutorials/beginner/nn_tutorial.html) [R14] | Batch-based training and validation concepts. |
| R15 | [Fairlearn, perform fairness assessment](https://fairlearn.org/v0.13/user_guide/assessment/perform_fairness_assessment.html) [R15] | MetricFrame group metrics and sample counts. |
| R16 | [OpenCATS project](https://github.com/opencats/OpenCATS) [R16] | Applicant-tracking reference project, not evidence for our model comparison. |
| R17 | [Odoo 18, recruitment flow](https://www.odoo.com/documentation/18.0/applications/hr/recruitment/recruitment-flow.html) [R17] | Reference workflow for jobs, applications and stages. |
| R18 | [MySQL 8.0, InnoDB locking reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html) [R18] | Row locking within transactions. Our backend applies the reference and deletion policy; the schema creates no SQL foreign keys. |
| R19 | [MySQL 8.0, unique indexes](https://dev.mysql.com/doc/refman/8.0/en/create-index.html) [R19] | Unique indexes allow multiple nullable values; motivates separate target-specific review constraints. |
| R20 | [MySQL 8.0, CHECK constraints](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html) [R20] | CHECK enforcement from 8.0.16, NULL semantics and restrictions to verify before running migrations. |
| R21 | [MyBatis 3, Java API](https://mybatis.org/mybatis-3/java-api) [R21] | Mapper interfaces and mapped SQL; MyBatis supplies persistence implementations. |
| R22 | [Spring Framework, transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html) [R22] | Managed database transaction boundaries; filesystem and network calls are separate application operations. |
| R23 | [Redis, persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/) [R23] | Redis persistence options; our architectural choice keeps MySQL as the durable business source. |
| R24 | [FastAPI, request body](https://fastapi.tiangolo.com/tutorial/body/) [R24] | Pydantic-based typed request parsing and validation. |
| R25 | [Official RuoYi-Vue3 frontend repository](https://github.com/yangzongzhuan/RuoYi-Vue3) [R25] | Vue 3, JavaScript, Vite, Element Plus, Pinia and Vue Router frontend baseline; pair it with a compatible RuoYi Spring Boot backend. |
| R26 | [Apache PDFBox](https://pdfbox.apache.org/) [R26] | Embedded PDF content/text extraction. |
| R27 | [Tesseract project and language data](https://github.com/tesseract-ocr/tesseract) [R27] | OCR engine; English and French language data are separately configured. |
| R28 | [DeepSeek, JSON output](https://api-docs.deepseek.com/guides/json_mode) [R28] | Structured output mode; our backend still checks schema and fact evidence. |
| R29 | [Redgate Flyway, versioned migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html) [R29] | Apply versioned migrations once and add later fixes as new versions. |
| R30 | [Vue Router, getting started](https://router.vuejs.org/guide/) [R30] | Browser URL to Vue component routing. |
| R31 | [Nginx, HTTP proxy module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html) [R31] | Request forwarding; keeping /api/v1 is our chosen routing rule. |
| R32 | [Docker Compose, application model](https://docs.docker.com/compose/intro/compose-application-model/) [R32] | Separate services, networks, configurations and persistent storage. |
| R33 | [Spring Framework, Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html) [R33] | Spring web/MVC responsibilities. Our feature folders, business interfaces and independent model boundaries are project choices. |

## Complete task schedule by work slot

Each task appears once in the following five charts, including reporting and presentation work. Amber bars identify the ten recorded dependency overlaps. The live Base contains the current status, detailed dependencies and Done when criterion. A work slot is a team responsibility, not an application role.

### Member A

![Every planned task for Member A](https://feishu.cn/file/NTLLb1kwmoSTzNxz2lHjoW8hpeh)

### Member B

![Every planned task for Member B](https://feishu.cn/file/VdzJbZzrtoxBhMxopfsjKDOYpZd)

### Member C

![Every planned task for Member C](https://feishu.cn/file/ZHW1b2IzJoMy6WxENHKjbxRdpRd)

### Member D

![Every planned task for Member D](https://feishu.cn/file/TmoBbCbrTovusXxYv5ejRiaAphm)

### All four

![Every planned task for All four](https://feishu.cn/file/NV2rbvX6GoNrTzxWPEajfl8YpXe)

## Dependency dates to resolve

| Task | Prerequisite | Planned start | Prerequisite due |
|-|-|-|-|
| E004 | D009 | 2026-10-08 | 2026-10-11 |
| H002 | E007 | 2026-10-08 | 2026-10-14 |
| H003 | D010 | 2026-10-09 | 2026-10-11 |
| C002 | E008 | 2026-10-15 | 2026-10-16 |
| M012 | M011 | 2026-11-14 | 2026-11-15 |
| S002 | S001 | 2026-11-03 | 2026-11-04 |
| S003 | S001 | 2026-11-03 | 2026-11-04 |
| X008 | X007 | 2026-12-02 | 2026-12-03 |
| F001 | A004 | 2026-10-01 | 2026-10-02 |
| F004 | X007 | 2026-12-01 | 2026-12-03 |
