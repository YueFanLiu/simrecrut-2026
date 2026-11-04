# Frontend Frozen Protocols

A04 implements `/admin/protocols` with the shared Admin layout. The corrected specification makes this page read-only: View Protocol and Refresh are its operational actions. There is no create, edit, deactivate or version-copy action.

## Scope and data

The single synthetic `P-2026.01` summary matches the existing demo model registry: `FS-32-v1`, `PP-2026.01` and ML cutoff `0.5`. Both Neutral ML and Biased ML reference it. No additional historical protocol versions are invented.

The list supports keyword, model-type and Frozen-status filters, reset, pagination and responsive cards. View Protocol opens a read-only drawer with matching precision, input mode, cutoff context and a JSON summary. Matching uses the documented seven-place HALF_UP procedure. The ML cutoff does not define the Objective rule threshold or offline labels. Complete label-generation rules, feature ordering, category encoding, preprocessing parameters and the freeze timestamp are unavailable and explicitly marked as such. Null does not mean zero.

This is metadata display only. No scoring, preprocessing, label generation, training, API requests, file access or model release occurs. Existing model slots, cases, reviews and Research data remain independent.

## Files and integration

- `frontend/src/mock/admin-protocols.js`: immutable fixture, explicit read projection, demo Admin checks and filters.
- `frontend/src/views/admin/protocols/index.vue`: list, loading/empty/errors, read-only detail and responsive layout.
- `frontend/tests/admin-protocols.test.js`: access, projection, immutability, filters and model-reference consistency.
- Router and AdminLayout add the page entrance. The existing role metadata adds only the protected `admin.protocols.view` permission. This is demo navigation, not real authorisation.

A page-local async request counter discards stale loads after closing, navigation or logout. Data does not need a mutable Pinia store; the existing auth Pinia store supplies the demo session. Reload clears the in-memory login.

## Local use

Run `npm install` once in `frontend`, then `npm run dev`. Sign in with `admin` / `SimRecrut123!`, open Manage users, then Frozen Protocols. Direct URL: `http://127.0.0.1:5173/admin/protocols` after signing in.

`VITE_DEMO_ADMIN_PROTOCOLS_SCENARIO` supports `normal`, `empty`, `load-error` and `detail-error`. Restart Vite after changing environment variables. Refresh retries the configured scenario; a persistent error scenario continues to show the simulated error until reset to normal.

Run `npm test`, `npm run build` and the repository language/link checker. Browser checks cover navigation, demo role guards, filters/reset, read-only details, retry states, logout, production preview and desktop/mobile overflow. Only the pre-existing IDE metadata contains repository Han characters; authored frontend code and build output are English-only.

Verification at completion: 127 tests passed, production build passed, and browser checks passed at widths 1440, 1024, 768, 390 and 320 pixels with no console errors or external requests.
