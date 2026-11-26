# Frontend CV Fact Review and Evidence

C04 is `/candidate/cases/:caseId/review`. P01-P04 and C01-C03 are approved.
C04 is approved. C05 is approved; C06 is approved; C07 is approved; H01 now awaits separate review.

## Layout and scope

The C04 prototype (PDF page 9) and corrections govern the page. It provides
professional fact groups, short evidence excerpts and save/confirm actions. The
corrected implementation excludes raw PDF previews, identity editing and arbitrary
re-upload. It reuses the Candidate layout and four-step workflow display rather
than adding the prototype's unrelated workspace menus.

C03 Review Extracted Facts opens C04 only after REVIEW_REQUIRED. Cases that have
not completed extraction offer Back to Upload. Missing, expired or inaccessible
cases offer Back to Jobs. Confirmed cases show a locked confirmation state.

Five editable groups are displayed:

| Group | Local demo fields |
| --- | --- |
| Skills | Skill name. |
| Experience | Role, reviewed relevant months and short professional summary. |
| Education | Degree level and field of study. |
| Languages | Language and explicitly chosen CEFR/native level. |
| Projects | Title and short professional summary. |

These are typed local UI fields, not the final API schema or matching inputs.
Frozen aliases, degree/subject equivalences, dated experience intervals and
project condition codes remain future integration work. No scoring or matching
formula is implemented. Demo required groups are Skills, Experience, Education
and Languages; Projects is optional. These are explicitly labeled demo criteria,
not inferred from a real backend job protocol.

Each group supports add/remove. An empty required group must be filled or have
its absence explicitly confirmed. Empty unknown and confirmed absence remain
distinct. A populated item must have valid fields before confirmation, even in
an optional group. Relevant months must be an integer from 0 to 600. Text has
length limits and select values must be recognized local options.

## Evidence and corrections

Fixed mock facts and source excerpts are unrelated to the selected PDF. View
Evidence shows only a short mock excerpt and page reference. Back to Fact returns
focus to the selected input, supporting the stacked mobile layout. No PDF,
full CV text, file download, personal name, email, phone or address editor exists.

Field metadata follows value, status, evidenceExcerpt and evidenceLocation.
Corrections and added fields become USER_SUPPLIED. Original excerpts remain
unchanged and are clearly described as original observations after correction.
UNKNOWN and CONFLICT on required groups block confirmation until the Candidate
resolves the value. Ambiguous language text never generates an inferred CEFR
level. Added facts have no invented source excerpt.

## Save and confirm

Save Changes keeps incomplete drafts in the memory-only store. Malformed entered
values still block saving. A failure preserves the prior saved draft and leaves
the user's current form edits visible. Returning to the review route resumes
saved values; unsaved local edits are discarded when leaving the page.

Confirm Facts checks required facts and the review acknowledgement, saves the
valid professional snapshot, changes the case to CONFIRMED and locks editing.
It does not require a separate Save Changes click. The action is idempotent in
the demo store. Failure leaves the case REVIEW_REQUIRED and preserves temporary
data. Successful confirmation clears file metadata, preview and editable draft,
sets a confirmation time and changes the local expiry to confirmation plus
30 days. The frontend has never stored raw PDF contents, so no actual file or
backend cleanup occurs.

The confirmed state stays on C04 with Continue to Simulation opening C05.
Running, completed and failed assessments also retain this read-only confirmation view.
C04 does not run assessments; Continue to Simulation opens C05. Revisiting the upload route after
confirmation offers View Confirmation and does not reopen file selection.

Cancel and Delete opens a confirmation dialog; Keep Case preserves the draft.
Confirmed deletion removes the in-memory case and returns to My simulations.
Late save responses cannot restore deleted data. Refresh and
sign out clear all demo cases, as in C03. This is frontend navigation, not real
authentication or backend authorization.

## Optional research details

The section is hidden by default. Only cases loaded with the enabled demo
research capability expose it. The store checks that capability before saving.
The four fields are school category, referral, gender and ethnicity. They live
outside the professional draft and default to UNKNOWN; empty values save as
UNKNOWN. Nothing is inferred from the professional facts or PDF.

The demo uses explicit self-reported text of at most 80 characters because the
preparation document leaves the value codes unspecified. The UI explains that
the final research schema must supply permitted categories. Supplied values
require an independent acknowledgement and Save Optional Details. Unsaved
optional changes must be saved or restored before professional confirmation.
All UNKNOWN values do not block the workflow. No research value enters a score,
Objective or Neutral model input. This capability flag is not real authorization.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/candidate/cases/review.vue` | Loading, local edit state, saves, confirmation and deletion. |
| `src/components/simrecrut/FactGroupEditor.vue` | Reusable field group and add/remove controls. |
| `src/components/simrecrut/EvidencePanel.vue` | Short excerpt display and return-to-field action. |
| `src/mock/review-draft.js` | English mock professional facts and missing/empty scenarios. |
| `src/utils/review-validation.js` | Required facts, format checks, projection and optional UNKNOWN normalization. |
| `src/store/modules/cases.js` | Draft/resume, optional values, revision checks and confirmation lock. |
| `src/components/simrecrut/CaseProgressStepper.vue` | Current review step and completed upload/confirmation indicators. |
| `src/views/candidate/cases/upload.vue` | Enabled C04 entry and confirmed upload lock. |
| `src/router/index.js` | Protected CvReview route. |
| `src/config/demo.js`, `.env.example` | Review scenarios and demo research capability. |
| `tests/cv-review.test.js` | Validation, absence, evidence, failed saves, optional details and confirmation checks. |

No new dependency or backend code was added for C04.

## Local preview

Follow [local setup](local-setup.md): sign in -> Browse published jobs ->
View Details -> Start Simulation -> choose a valid sample PDF -> Upload and
Extract -> Review Extracted Facts. Edit a fact, inspect evidence, save, acknowledge
review and select Confirm Facts. Confirming locks the facts; Continue to Simulation opens C05.

Optional `frontend/.env.local` settings, followed by a Vite restart:

```text
VITE_DEMO_REVIEW_SCENARIO=missing
VITE_DEMO_RESEARCH_ATTRIBUTES_ENABLED=false
```

Review supports normal, missing, empty, load-error, save-error and confirm-error.
Missing and empty seed newly loaded case drafts; saved drafts resume unchanged.
Load error retry repeats the configured failure. Save and confirm errors preserve
the prior case state. Restore normal to test success. Enable research with true
only to inspect the demo capability branch; no permission controls appear in the UI.

## Verification on 2026-10-10

- All 28 Node tests passed, including seven new review tests.
- Required unknown/conflicting values, explicit absence, optional empty groups,
  invalid months, separate research values and whitelist projection passed.
- Failed saves, resume, optional acknowledgement, non-destructive confirmation
  failure, duplicate confirmation, edit locking and delete-during-save passed.
- Production build passed without warnings.
- Edge browser checks passed for the complete C03-to-C04 path, edit/add/remove,
  evidence and keyboard focus recovery, validation, save/resume, acknowledgement,
  confirmation lock, upload lock, unknown case, refresh and sign out.
- Missing, empty, load/save/confirm failure and enabled research branches passed
  isolated browser checks using the already-tested upstream completion state.
- Normal save flow passed in production preview. No console errors or external
  requests were observed in the normal flows.
- No horizontal overflow at 1440, 1024, 768, 390 or 320 pixels. Desktop/mobile,
  confirmation and error screenshots were inspected.
- Frontend source and production output passed the Han scan. Repository checks
  excluding preserved IDE metadata and `git diff --check` passed. The existing
  `.idea/vcs.xml` language issue remains; that file was not modified.

The explicit form-validation and save structure was compared with
[official RuoYi-Vue3 userInfo](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/system/user/profile/userInfo.vue)
on 2026-10-10. Its identity fields, server request and Chinese content were not copied.
The reference documents' backend confirmation and cleanup requirements remain
future work; their workflow is simulated in memory here.

C04 is approved. Stop after H01 and wait for user confirmation before H02.
