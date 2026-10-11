# Frontend Roles and Permissions

A02 is `/admin/roles`, following approved A01. Only this page unit is added.
PDF page 27 and Third Meeting Preparation define roles, menu/action permission
trees, Edit permissions, Assign role, Save and navigation back to Users.
The correction says menu permissions are not case data grants and Reviewer is
not an independent privileged role. The image's Platform role is replaced by
Candidate to match the existing account model. No custom role creation or role
activation/deactivation is introduced; the four supported roles remain fixed.

## Interface and state

Reuse AdminLayout, Element Plus and the existing Admin demo session. A01's
Roles & Permissions entry is enabled; each sidebar link reflects the active
page. Audit Logs and later administration pages remain unavailable.

Cards and a role list show Admin, HR, Researcher and Candidate, descriptions,
Active metadata, and live directory user counts. Search matches role name or
description; Refresh retains search. Empty/error/retry states are explicit.
The matrix shows saved module View permissions for all four roles.

Edit Permissions opens a drawer with a fixed role name, editable 1-500 character
description, menu/action checkbox tree and a module visibility preview. Only
nodes relevant to the selected role appear. Group checkboxes select children.
Actions require the same module's View permission. Core Admin permissions are
protected/read-only; description remains editable. Save updates the memory
configuration and matrix; Cancel discards the draft. Save failures preserve
selection for retry. A revision check prevents stale or simultaneous edits
from overwriting a newer role configuration. Pending writes cannot restore
state after logout.

Assign Role opens a dialog for an account and the chosen fixed role. Save
replaces the account's mock directory role via A01's validated store action.
Cancellation makes no changes. User counts and A01 immediately reflect a saved
assignment. The current Admin cannot be demoted, the last active Admin is
protected, and stale account revisions are rejected. Other account metadata
and any existing grants remain untouched. A01's short legacy username `hr`
is accepted unchanged during editing; new usernames still require 3-20
characters and existing usernames remain immutable.

## Demonstration boundaries

Saved permissions affect only the matrix and visibility preview on A02.
They do not rewrite previously approved application routes or real permissions.
Role assignment changes the directory, not the mock login account's role or
credentials. Menus and action flags do not authorise specific cases, subjects
or tasks. Ownership, current grants, privacy, retention and reviewer assignment
are separate. Reviewer permissions appear only for HR/Researcher responsibilities.
Admin receives administration menus, not automatic access to Candidate content.

The explicit role projection contains name, description, status, revision,
permission codes and directory count. No account identities, CV data,
professional facts, research attributes or human reasons enter the role list
or matrix. No backend, API, token, database or dependency is added. Permission
codes and defaults are illustrative local fixtures, not a backend contract.

## Files

| File under frontend | Responsibility |
| --- | --- |
| `src/views/admin/roles/index.vue` | Role cards/list, search, matrix, permission drawer and assignment dialog. |
| `src/mock/admin-roles.js` | Fixed roles, permission tree, projection, preview and validation. |
| `src/store/modules/admin-roles.js` | Memory configurations, load/save guards and A01 assignment connection. |
| `src/layouts/AdminLayout.vue`, `src/router/index.js` | Navigation, active links and Admin demo route guard. |
| `src/views/admin/users/index.vue` | Enable A02 entry and update the availability note. |
| `src/mock/admin-users.js` | Preserve short existing usernames during editing. |
| `src/store/modules/auth.js` | Reset role configuration on sign out. |
| `src/config/demo.js`, `.env.example` | Normal/empty/load-error/save-error/assign-error states. |
| `tests/admin-roles.test.js`, `tests/admin-users.test.js` | Eight role tests and short existing-username regression coverage. |

## Local review and verification at A02 completion

Run `npm run dev` in `frontend`. Sign in as `admin` or `admin@simrecrut.test`
with `SimRecrut123!`, choose Manage users, then Roles & Permissions. Edit HR
permissions, cancel or save, inspect the matrix, then assign Sam Parker to HR
and return to Users to see the directory update. Reload/sign out resets data.

Use `VITE_DEMO_ADMIN_ROLES_SCENARIO=empty`, `load-error`, `save-error` or
`assign-error` in `.env.local` and restart Vite to check recovery. Restore normal.

All 114 Node tests and the production build pass. Browser checks cover navigation,
fixed roles, search/reset/Refresh, tree dependencies, cancel/save and matrix
updates, protected Admin permissions, assignment validation/cancel/save, A01
count/metadata linkage and legacy `hr` editing, non-Admin rejection, configured
empty/load/save/assign errors and retry, production preview and reload.
Widths: 1440, 1024, 768, 390 and 320 pixels, including drawers and dialogs.
No body overflow, console errors or external requests were observed. Authored
files and frontend source/build text contain no Han characters, excluding
pre-existing `.idea/vcs.xml` metadata that remains unchanged. Stop after A02
and wait for confirmation before starting A03.

A03 adds protected Admin model management permission metadata and the Model
Versions navigation entry. Its lifecycle demonstration is documented in
[Model Versions and Release](frontend-admin-models.md).
