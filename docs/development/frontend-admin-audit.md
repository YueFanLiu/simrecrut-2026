# Frontend Audit Logs

A07 implements `/admin/audit` with the existing Administration layout. The prototype corrections require read-only, minimal non-content metadata and future server-side filtering. This implementation is frontend-only.

## Behavior

Eight synthetic events demonstrate action code, nullable actor reference, resource type/ID, UTC time, operational From/To state, outcome and request ID. These are independent examples, not records captured from existing demo pages. Codes, IDs and timestamps are fictional display fixtures rather than a new backend contract.

Search applies actionCode, resourceType, actor, from, to and outcome. Refresh and pagination use the last applied query, even while the user edits draft filters. Clear Filters resets and loads the initial query. Date inputs use English YYYY-MM-DD text with calendar and range validation. A local async mock query filters before slicing into pages of five or ten. This creates a replaceable read boundary; there is no HTTP request or real server filtering.

View opens a read-only drawer using the already projected list item; no unsupported detail endpoint is added. Null actors and resource IDs remain unavailable. From/To show operational status only, not content snapshots or review decisions. Summary text and allowed state values are selected from fixed safe maps. Raw JSON, free-form details, IP, user agents, sessions, credentials, CV facts, research values and human reasons are excluded. No edit, delete or export action is added.

Loading, empty and load-error states are supported. Request/session checks discard late loads after navigation/logout. Pinia auth remains the existing demo session; no mutable audit store or backend audit recording is implemented.

## Files and connections

- `frontend/src/mock/admin-audit.js`: explicit safe projection, fixtures, date validation and paged mock query.
- `frontend/src/views/admin/audit/index.vue`: draft/applied filters, pagination, read-only detail and responsive table/cards.
- `frontend/tests/admin-audit.test.js`: Admin checks, safe fields, combined query/pagination, dates, nullable metadata and immutable reads.

Router/AdminLayout add the page entrance and `admin.audit.view` is protected Admin demo metadata. A06 Audit now opens A07; its Retry stays unavailable. No other workflow is changed. Real access enforcement and server-side safe filtering remain future backend responsibilities.

## Local use and verification

From `frontend`, run `npm run dev`. Sign in with `admin` / `SimRecrut123!`, open Manage users then Audit Logs, or use Audit on A06. Signed-in URL: `http://127.0.0.1:5173/admin/audit`.

`VITE_DEMO_ADMIN_AUDIT_SCENARIO` supports normal, empty and load-error. Restart Vite after changing it. Refresh retries the current scenario; persistent errors remain until restored to normal.

Run `npm test` and `npm run build`. Browser checks cover the two entry points, role guards, draft/applied query semantics, combined filters/date validation, clear/reset, pagination, read-only safe details, removed actor, errors, responsive widths, logout/reload and production preview. Authored files remain English-only; pre-existing IDE metadata is preserved.

Verification at completion: all 143 tests and the production build passed. Browser checks passed at widths 1440, 1024, 768, 390 and 320, with no console errors or external requests. English-language, documentation-link and LF checks passed for authored files; frontend source, reference files and build output contain no Han characters.
