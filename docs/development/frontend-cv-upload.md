# Frontend CV Upload and Extraction

C03 is the Candidate upload page at `/candidate/cases/:caseId/upload`. P01-P04,
C01 and C02 are reviewed and approved. C03 was reviewed and approved before C04.
This is a frontend demonstration only.

## Scope and reference corrections

The prototype's C03 page (PDF page 8) provides file selection, extraction status,
a limited preview and cancellation. Its corrections take precedence over the
initial image: PDF only, at most 10 MB, at most 20 pages, no encryption, no image
uploads and no invented progress percentages. Complete fact review belongs to
C04. Save/edit facts, scoring, OCR and AI extraction are not implemented here.

C02 Start Simulation now creates an in-memory case using the displayed published
jobVersionId, rechecks that version, and opens C03. Duplicate clicks are blocked.
If creation fails or the version is unavailable, the user remains on Job Detail
with English feedback and Back to Jobs. Leaving before creation completes
discards that unfinished demo case.

Cases are scoped to the demo username, expire 24 hours after creation and are
cleared on refresh or sign out. Re-upload does not extend expiry. This is local
navigation behavior, not authentication or backend access protection.

## Upload and processing behavior

Choose File, drag/drop, Remove File and replacement are supported. Only one PDF
is accepted at a time. The browser checks extension, provided MIME type, nonempty
size and a maximum of 10 * 1024 * 1024 bytes, then parses the PDF to check the page
tree and encryption flag. PDFs with no pages, more than 20 pages, encryption or
invalid contents show English errors and cannot begin processing.

`pdf-lib` is the single new direct dependency. It is loaded dynamically when a
file is checked, allowing actual local page-count/encryption checks rather than
filename assumptions. It is not used to extract CV facts. No PDF is sent to a
server, saved to browser storage or copied into the repository. Only validated
name, size and page count are retained in the case; the File and parsed document
remain temporary validation inputs. Future backend validation remains necessary.

The demo follows AWAITING_UPLOAD -> UPLOADED -> EXTRACTING -> REVIEW_REQUIRED.
Processing uses stage labels and an indeterminate spinner, never a percentage.
Duplicate uploads are unavailable while processing. Leaving and returning in
the same signed-in tab resumes the in-memory case; refresh clears the session.

FAILED_RETRYABLE provides one controlled Retry Upload or Retry Extraction action.
The retryable scenario succeeds on that retry. FAILED_FINAL provides replacement
instead of retry. Invalid local files require selection of a valid replacement
and never start an extraction task. The final scenario keeps failing until the
development configuration is restored to normal.

At REVIEW_REQUIRED, three fixed public sample facts are shown, explicitly labeled
as unrelated to the selected PDF. There are no personal identifiers, evidence
or full editing controls. Review Extracted Facts now opens the implemented
[C04 fact review](frontend-cv-review.md) when the case is REVIEW_REQUIRED.
My simulations opens [C07](frontend-my-simulations.md).

Cancel and Delete opens a confirmation dialog. Keep Case closes it without
deleting. Delete Demo Case removes metadata and preview, cancels late state
updates, and returns to My simulations. Unknown,
expired or unavailable cases show a safe Back to Jobs state.

## Files

| File under `frontend/` | Responsibility |
| --- | --- |
| `src/views/candidate/cases/upload.vue` | Case context, stage views, preview and delete confirmation. |
| `src/components/simrecrut/CvUploadCard.vue` | File picker, drag/drop, validation and selection feedback. |
| `src/components/simrecrut/CaseProgressStepper.vue` | Reusable four-step workflow display. |
| `src/store/modules/cases.js` | Memory-only case creation, processing, retry and cleanup. |
| `src/utils/cv-validation.js` | Local PDF checks and file-size formatting. |
| `tests/cv-upload.test.js` | PDF boundaries, version/ownership, duplicate actions and cleanup checks. |
| `src/views/candidate/jobs/detail.vue` | Enabled Start Simulation and creation feedback. |
| `src/router/index.js` | Protected CvUpload route. |
| `src/store/modules/auth.js` | Clear demo cases on sign out. |
| `src/layouts/CandidateLayout.vue` | Jobs active state follows the current work area. |
| `src/config/demo.js`, `.env.example` | Optional creation and processing scenarios. |
| `package.json`, `package-lock.json` | PDF parsing dependency. |

## Local preview

Follow [local setup](local-setup.md): sign in, choose Browse published jobs,
View Details, then Start Simulation. Use a sample PDF. The Upload and Extract
button runs the frontend demonstration only.

Optional `frontend/.env.local` settings, followed by a Vite restart:

```text
VITE_DEMO_CASE_CREATION_SCENARIO=normal
VITE_DEMO_EXTRACTION_SCENARIO=retryable
```

Case creation supports normal, unavailable and error. Extraction supports normal,
retryable, final and upload-error. Restore normal or remove the settings to
restore the successful flow. No scenario controls are shown in the product.

## Verification on 2026-10-10

- All 21 Node tests passed, including five new PDF/case tests. Checks cover
  compressed page trees, 20/21-page boundaries, empty/corrupt/encrypted files,
  size/format errors, published-version consistency, ownership and expiry.
- Duplicate uploads, controlled retry, final failure, unchanged expiry and
  deletion/sign-out during pending work passed store tests.
- Production build passed without warnings; dependency installation reported
  zero vulnerabilities.
- Edge browser checks passed for creation, file selection, drag/drop, removal,
  all file-error types, stage states, resume, fixed preview, confirmation,
  deletion, refresh and sign out. Normal flow also passed in production preview.
- Retryable extraction, upload failure, final failure and case-creation failure
  or unavailable-version scenarios passed isolated browser checks.
- No horizontal overflow at 1440, 1024, 768, 390 or 320 pixels. Desktop/mobile
  initial, processing, ready and failure screenshots were inspected.
- No console errors or external requests were observed in the normal browser
  flows. No backend, storage service, OCR, DeepSeek, token or database code was added.
- Frontend source and production output passed the Han scan. Repository checks
  excluding preserved IDE metadata and `git diff --check` passed. The existing
  `.idea/vcs.xml` language failure remains; that file was not edited.

The reusable upload component and pre-upload validation structure were compared
with [official RuoYi-Vue3 FileUpload](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/components/FileUpload/index.vue)
on 2026-10-10, without copying its server upload, token or Chinese content.
Local PDF checks use the documented
[PDFDocument API](https://pdf-lib.js.org/docs/api/classes/pdfdocument).

C03 is approved. C04 is approved; C05 is approved; C06 is approved; C07 is approved; H01 now awaits separate review.
