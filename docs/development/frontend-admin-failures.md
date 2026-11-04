# Frontend Failed Jobs and Errors

A06 implements `/admin/failures` in the existing Admin layout. The page shows eight explicitly synthetic task records, independent of Candidate/HR/Research cases. It does not read or alter their data.

## Behavior

Search combines exact stage/status filters and inclusive UTC dates. Date inputs use the explicit English format YYYY-MM-DD so browser locale cannot introduce non-English placeholders. Invalid dates and inverted ranges show English validation errors. Pagination offers five or ten records per page. View opens a read-only technical drawer with task ID, resource ID/type, failed stage, safe error code/message, retryability, cleanup status and occurrence time. Narrow layouts use cards.

Refresh reloads fixtures. Loading, empty, list-error and detail-error states are supported. Request counters and the existing Pinia auth session prevent late reads from rendering after close, navigation or logout. No mutable failure store is needed.

The requirements explicitly keep manual Admin retry unavailable until an operation is defined and implemented. Retry is therefore disabled, even when fixture metadata says retryable. The reason is visible beneath the list and in details. No retry endpoint, queue, repaired state or fake completed outcome is implemented. A future HTTP 202 can mean Queued only; completion needs separate verification and successful stages/fixed versions must be retained.

The fixture uses display labels, not a newly invented backend enum contract. Its error codes and timestamps are fictional. Cleanup Pending/Unavailable is metadata only, never proof of actual storage deletion. Safe messages come from a fixed frontend allowlist; raw error strings, stack traces, SQL, CV facts, research values, human reasons and secrets are discarded by the projection.

## Files and necessary connections

- `frontend/src/mock/admin-failures.js`: safe projection, fixtures, filters, dates and async loaders.
- `frontend/src/views/admin/failures/index.vue`: filters/list/cards, details and disabled retry explanation.
- `frontend/tests/admin-failures.test.js`: access, safe projection, task identity, filtering, immutability and failures.

The Admin router/layout add an entrance, and protected role metadata adds only `admin.failures.view`. The existing A05 View Failures button now opens A06. Audit now opens the completed A07 page. No backend code, network API requests or third-party dependencies are added. Demo navigation does not implement real authorisation.

## Local use

In `frontend`, run `npm run dev`. Sign in with `admin` / `SimRecrut123!`, open Manage users, then Failed Jobs & Errors; alternatively use View Failures in Service Health. The signed-in URL is `http://127.0.0.1:5173/admin/failures`.

`VITE_DEMO_ADMIN_FAILURES_SCENARIO` supports normal, empty, load-error and detail-error. Restart Vite after changing it. Refresh retries the current fixture scenario; persistent errors remain until normal is restored.

Run `npm test`, `npm run build` and the repository checker. Browser verification covers entry routes, role guards, filters/date validation/reset, pagination, safe detail, disabled Retry/Audit, fixture errors/retry, logout, production preview and responsive widths. Pre-existing IDE metadata is preserved; authored frontend files must remain English-only.

Verification at completion: 137 full-suite tests passed. After the locale-independent date-input adjustment, all five failure tests and the build passed again. Browser checks passed at widths 1440, 1024, 768, 390 and 320 with no console errors or external requests. Authored files and build output passed English-language checks; existing IDE metadata was preserved.
