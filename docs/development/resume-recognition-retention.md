# Resume recognition and retention

The shared Java 17 cleaner reads embedded PDF text first and uses local Tesseract 5 OCR for pages with fewer than 20 letters or digits. OCR requires the `eng` and `fra` models. Configure `TESSERACT_EXECUTABLE`, `TESSDATA_PATH`, and `TESSERACT_LANGUAGES` in the private root `.env`. The web service and offline watcher use the same cleaner. Original documents are not sent to an AI provider.

On Windows, install `UB-Mannheim.TesseractOCR` with Windows Package Manager. Install English and French models from the official [tessdata_fast repository](https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd). Keep downloaded models outside version control. OCR processes have a 30-second page timeout, two concurrent process slots, a 120-second document budget, and bounded raster sizes. Temporary OCR files are deleted after each attempt.

## Extraction scope

The conservative rules recognize the existing skill dictionary, software engineering and data analysis roles, explicitly stated ISO months, English/French month names and MM/YYYY dates, Bachelor/Master/PhD degrees, computer science and mathematics subjects, five languages with explicit CEFR levels, and projects with recognized skills. Section headings support English and French. Year-only dates, unrecognized subjects, and vague language levels remain unknown. User review is mandatory; relevant years are never inferred automatically. Evidence contains only recognized professional fragments and PDF page numbers, excluding arbitrary identity lines. Codes remain provisional until the formal codebook is integrated.

Run Maven tests with OCR models configured. `target/extraction-accuracy.json` reports fact matches for two synthetic bilingual fixtures. Additional tests cover unknown fields, negative skill statements, text PDFs, DOCX, actual raster-only PDF OCR, and retention. These fixtures are regression checks, not a representative real-world accuracy estimate. Production accuracy still requires an annotated, consented corpus covering layouts, roles, languages, and scan quality.

## Retention and deletion

New web imports expire exactly 24 hours after import. Failed extraction, duplicate imports, draft edits, and retries do not extend this deadline. Confirmation locks professional facts and starts a fixed 30-day deadline. Identical confirmation retries are idempotent. Existing temporary records are migrated from their original import or last pre-migration confirmation timestamp. Legacy research/library records with `temporary_original=0` and offline dataset files are excluded from automatic expiry.

An expired or `PURGING` resume cannot be listed, read, rendered, edited, processed, or published. A sweep runs every minute, including inactive user directories. Explicit deletion uses the authenticated `DELETE /simrecrut/resumes/{id}` endpoint and `simrecrut:resume:delete` permission. Candidate roles receive only their own resume permission; user identity always comes from the authenticated session.

Deletion cancels processing, removes all published revisions from `sim_cv_cleaned`, removes the temporary original and local resume row, and stores a content-free local receipt containing only ID, reason, and completion time. Publication intent is persisted before the cloud commit for crash recovery. SQLite secure deletion clears deleted cell content. Failures keep the record inaccessible and retry automatically. Successful publication also retries temporary-original cleanup automatically.

This implementation covers the existing resume adapter. Formal case/experiment dependencies are not yet connected to that adapter; their future cascade deletion needs integration when those business flows are implemented. Cloud-provider backups follow the provider's retention policy and cannot be erased by this endpoint.

## Local verification on 2026-10-12

Java 17 Maven verification passed 14 tests, including real local OCR. All 148 frontend regression tests passed and the production build succeeded. The 25 existing authenticated API audit checks passed. Additional live checks verified raster-only PDF extraction of all five groups and authenticated deletion of published Aiven JSON, local rows, and original files. Test-created originals and cloud rows were removed.
