# Frontend Model Versions and Release

A03 is `/admin/models`, following approved A02. Only this page unit is added.
PDF page 28 corrects the lifecycle to TESTED -> APPROVED -> ACTIVE -> RETIRED.
Third Meeting Preparation describes offline package registration, separate
approval/activation, readiness checks and retirement with a compatible replacement.
This frontend implements labelled local demonstrations of those operations;
real HTTP contracts, service checks and releases remain future backend work.

## Interface

Reuse AdminLayout, the existing Admin demo login and Element Plus. The new
Model Versions sidebar entry is active on this route. Later administration
pages are not implemented. A02 gains only the necessary protected Admin model
management permission metadata; no existing navigation guards are rewritten.

Neutral ML and Biased ML cards show registered version counts and one active
slot each. Search matches version ID/type/version/description, and type/status
filters combine. Reset clears filters; Refresh preserves them. Pagination uses
5/10/20 rows. Narrow screens use cards instead of the desktop table.

View opens a read-only details drawer with version, type, status, description,
32 -> 16 -> 8 -> 1 architecture, 673 parameters, 32-input schema, preprocessing,
protocol, cutoff, input mode, synthetic manifest/checksum/training references,
timestamps, recorded synthetic checks and readiness. No measured accuracy,
balanced accuracy, macro-F1 or fairness values are invented. Evaluation is
explicitly unavailable because no measured values/denominators are retained.
Artifact paths, weights, CV content and human reasons are omitted at projection.

## Local lifecycle operations

Register Package selects one of two predefined synthetic offline metadata
records. It validates recorded compatible schema/protocol/cutoff and evidence
references, then adds a TESTED version. The browser never uploads, trains or
executes a model. Duplicate/concurrent registration creates at most one entry.

Approve is available only for TESTED records and requires complete recorded
package/evaluation references. It produces APPROVED, not ACTIVE. Activate is
available only for APPROVED records and rechecks package compatibility and
synthetic inference readiness. It switches the same-type active slot and marks
the previous active version RETIRED in one synchronous state change.

Retire is available only for an ACTIVE version with an APPROVED replacement.
The confirmation dialog requires a compatible same-type replacement and swaps
it into the slot while retiring the old version. No core slot is left empty.
Retired records remain viewable and cannot be reactivated directly. No model
record is deleted; existing fixed version references remain reproducible.

All mutations require confirmation, show loading, and check revision, current
slot, registry consistency and fixture identity after the mock delay. Stale,
concurrent, incomplete, incompatible or not-ready operations fail without
partially changing a slot. Failed operations preserve dialog selections for
retry. Cancel makes no changes; logout restores fixtures and pending writes
cannot restore prior session data.

## Demonstration boundaries

Seven initial versions show all four lifecycle states. All package checks,
readiness, references and checksums are visibly synthetic. No real integrity,
evaluation, service availability, activation or retirement is performed.
No online training or DeepSeek scoring integration is introduced.

The A03 registry is separate from approved Research and Candidate mock stores.
Its initial active version IDs align with examples, but A03 releases do not
change R05 model examples, existing experiments, cases or result records.
Sharing live registries and real backend checks is outside this page scope.
No backend, API, database, token, permission verification or dependency is added.

## Files

| File under frontend | Responsibility |
| --- | --- |
| `src/views/admin/models/index.vue` | Slot cards, filtering, pagination, details and confirmation dialogs. |
| `src/mock/admin-models.js` | Synthetic metadata, projections, package/registry checks and filtering. |
| `src/store/modules/admin-models.js` | Delayed registration/approval/release with atomic slot updates. |
| `src/router/index.js`, `src/layouts/AdminLayout.vue` | Admin demo guard and Model Versions navigation. |
| `src/mock/admin-roles.js` | Protected model administration permission metadata on A02. |
| `src/store/modules/auth.js` | Reset registry on sign out. |
| `src/config/demo.js`, `.env.example` | Normal/empty/load-error/register-error/release-error fixtures. |
| `tests/admin-models.test.js` | Eight meaningful lifecycle, projection, access and rollback tests. |

## Local review and verification

Run `npm run dev` from `frontend`. Sign in with `admin` / `SimRecrut123!`,
choose Manage users, then Model Versions. Register N-2026.04, approve a TESTED
version, activate an APPROVED version and inspect the retained previous record.
Retire an ACTIVE version by selecting an APPROVED same-type replacement.

Set `VITE_DEMO_ADMIN_MODELS_SCENARIO=empty`, `load-error`, `register-error` or
`release-error` in `.env.local` and restart Vite to check recovery. Restore normal.

All 122 Node tests and the production build pass. Browser checks cover Admin
navigation and non-Admin rejection, combined filters/Reset/retained Refresh,
pagination, safe details and unavailable metrics, registration validation,
cancel/save, separate approval/activation, retirement replacement validation,
retained history, empty/load/register/release failures and retry, logout reset,
production preview and reload. Widths are 1440, 1024, 768, 390 and 320 pixels,
including details and registration dialogs. No body overflow, console errors or
external requests were observed. English/UTF-8/LF, documentation and whitespace
checks pass excluding preserved `.idea/vcs.xml` Han metadata. Frontend source
and build text have no Han characters. Stop after A03 and wait for confirmation.
