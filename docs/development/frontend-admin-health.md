# Frontend Service Health

A05 implements `/admin/system-health` using the existing Admin layout. The prototype corrections replace fabricated uptime, CPU/memory measurements and unsupported infrastructure with the documented Java API, MySQL, Redis, ML inference and temporary-workspace/cleanup components.

## Scope

All runtime statuses are Unknown. No health API is connected, no backend status enum is invented and no actual service/network/storage check runs. Last check is Unavailable; Refresh reloads local metadata without creating a runtime timestamp. Workspace writability and cleanup state remain null. The two loaded-model slots have unknown load status and unavailable version references, independently of the mutable A03 mock registry.

The page displays summary cards, component status/check/freshness details, loaded-model slots and workspace/cleanup information. Responsive cards replace the status table on phones. Models navigates to A03. View Failures now opens the completed A06 page. There are no raw logs, paths, credentials, CV facts, research attributes or human reasons.

No new service-health dependencies, backend code or real API requests are added. The existing Admin router, navigation and protected role metadata gain the necessary read-only page entry. Demo navigation is not real authorisation.

## Files

- `frontend/src/mock/admin-health.js`: minimal metadata factory, Admin demo checks, async fixture loader and timestamp freshness helper.
- `frontend/src/views/admin/system-health/index.vue`: Composition API view, request/session guards, timer cleanup and responsive presentation.
- `frontend/tests/admin-health.test.js`: role restrictions, unknown/missing values, copies, failures and freshness boundary checks.

The page discards pending loads after navigation or logout. The local freshness timer is removed on unmount. No mutable health store is needed; Pinia auth supplies the existing in-memory session.

## Local use

From `frontend`, run `npm run dev`. Sign in with `admin` / `SimRecrut123!`, choose Manage users, then Service Health. After login the direct URL is `http://127.0.0.1:5173/admin/system-health`.

`VITE_DEMO_ADMIN_HEALTH_SCENARIO` supports normal, empty, load-error and stale. Restart Vite after changing it. The stale fixture uses an explicitly labelled synthetic timestamp and still reports Unknown health. The 60-second freshness window is a frontend demonstration convention; the future real contract must define its own freshness policy and typed statuses. Persistent load-error keeps failing on Refresh until the scenario is restored to normal.

Run `npm test` and `npm run build`. Browser verification covers the Admin entry, other-role guards, Refresh, Models, Failures entry (enabled when A06 was added), empty/error/stale fixtures, logout/reload and production preview. Check widths 1440, 1024, 768, 390 and 320 for overflow, console errors and unexpected external requests. Repository language checks preserve existing IDE metadata while requiring English-only authored files.

Verification at completion: all 132 tests and the production build passed. Browser checks passed at all five viewport widths, with no console errors or external requests. The English-language, documentation-link and LF checks passed for authored files.
