# Frontend User Management

A01 is `/admin/users`, the next page after approved V02. This guide covers A01.
[A02 Roles and Permissions](frontend-admin-roles.md) now supplies the next entry.
PDF page 26 and Third Meeting Preparation specify user lists, account status,
account authorisation entry points, Search, Add, Edit and Enable/Disable.
The visual reference supplies role/status/group filters, pagination and details.
The existing WANG Admin wrapper depends on missing user/provider/demo components
and real RuoYi account APIs; it is preserved unchanged in the reference snapshot.
A01 instead follows the existing Vue/Pinia/layout structure with local mock data.

## Behaviour and boundaries

AdminLayout provides Administration navigation and the approved brand header.
User Management is active on A01. Roles & Permissions now enters A02; Audit
Logs and further administration pages remain unavailable.

The account list supports combined name/username/email/role search, role/status/
group filters, Reset Filters, Refresh and 5/10/20-item pagination. Refresh keeps
filters and page size. Desktop tables scroll within their panel; mobile uses
cards. Click a display name for a metadata-only User Details drawer.

Add/Edit dialogs validate username, display name, email, supported role/group
and case-insensitive duplicates. New usernames use 3-20 letters, digits or
underscores; names use 1-60 characters; email uses at most 100 characters.
Existing usernames are immutable. The short legacy username `hr` is preserved
during edits; new usernames still require at least three characters. Added entries start Active with no last login.
Cancel makes no changes. Save uses loading state and retains input after failure.

Enable/Disable requires confirmation and preserves account metadata. The current
Admin cannot change its own status or role; the last active Admin cannot be
disabled or demoted. Action-time revision checks reject stale or concurrent
updates. Duplicate concurrent creates produce only one entry. Logout resets the
mock directory and pending writes cannot restore earlier session data.

A separate Admin demo login is `admin` or `admin@simrecrut.test`, password
`SimRecrut123!`. Public Candidate registration cannot claim these identifiers.
Candidate, HR and Researcher navigation to A01 reaches Access Denied. Signed-out
entry goes to Login. These frontend guards provide no real security.

The directory deliberately does not modify existing mock login credentials,
registration state or account roles used by other demos. An added directory
entry is not a new login account. No password field or reset workflow is added.
Role/group labels are account metadata, not case grants or backend permissions.
No CV, professional facts, research attributes, assessment results or human
reasons enter the explicit account projection. No Reviewer account role is
introduced. Dates and last logins are fixed illustrative mock values, not live
service activity. No backend, API, token, database or dependency is added.

## Files

| File under frontend | Responsibility |
| --- | --- |
| `src/views/admin/users/index.vue` | List, filters, details drawer and account/status dialogs. |
| `src/layouts/AdminLayout.vue` | Administration shell and unavailable next-page entries. |
| `src/mock/admin-users.js` | English fixtures, minimal projection, filtering and validation. |
| `src/store/modules/admin-users.js` | Memory directory, delayed actions and stale/self/last-Admin guards. |
| `src/mock/auth.js` | Reserved Admin demonstration login. |
| `src/store/modules/auth.js` | Reset the directory on sign out. |
| `src/router/index.js`, `src/views/DemoDashboardView.vue` | Admin guard and Manage users entry. |
| `src/config/demo.js`, `.env.example` | Normal/empty/load-error/save-error/status-error scenarios. |
| `tests/admin-users.test.js` | Eight meaningful account boundary and interaction tests. |

## Local review

From `frontend`, run `npm install` if needed and `npm run dev`. Sign in as
`admin` using `SimRecrut123!`, then choose Manage users on the temporary dashboard.
Use Add User, select an account name for details, Edit, and Disable/Enable to
review interactions. Existing login accounts remain unchanged by these actions.

Set `VITE_DEMO_ADMIN_USERS_SCENARIO` in `.env.local` and restart Vite for empty,
load-error, save-error or status-error. Restore normal afterwards. An empty
scenario hides the fixtures and disables Add; ordinary empty directory state
still allows Add. Failure scenarios allow retry and do not modify source data.

## Verification at A01 completion

All 106 Node tests and the production build pass. Browser checks cover Admin
entry, Candidate/HR/Researcher rejection, combined filters, reset, pagination,
retained Refresh, required and duplicate validation, Add/Edit cancellation and
saving, immutable usernames, detail/status confirmation, current Admin controls,
logout reset, empty/loading/error/retry and production preview/reload.
Widths are 1440, 1024, 768, 390 and 320 pixels, including account dialogs.
A 60-character display name and details drawer also pass at 320 pixels.
No body overflow, console errors or external requests were observed.
Authored repository and frontend source/build text contain no Han characters.
Existing `.idea/vcs.xml` Han metadata remains untouched. Stop after A01 and wait
for user confirmation. A02 integration checks are in its separate guide.
