# Frontend Page Not Found

P04 is implemented at `/404` and for unmatched browser routes. The page follows
the supplied prototype's page 5: SimRecrut header, original local signpost SVG,
404 status, Page Not Found message and recovery actions. It uses StatusLayout.

## Behaviour

`/404` is public. Unknown URLs render the same page without redirecting, keeping
the requested URL in the browser address bar. Paths, query values and resource
identifiers are not repeated in the page. There is no search form, search result,
business-area menu or notification feature because the prototype correction
excludes uncontracted search and inaccessible workspaces.

For signed-out users, the header and primary action show Sign in. For signed-in
demo users, the header shows the existing user and Sign out; the primary action
opens the temporary Dashboard. Only Demo dashboard is listed as an available
workspace. Refresh clears the in-memory session and changes the same 404 page
to its signed-out state, instead of redirecting public missing URLs to Login.

Go Back resolves the previous internal route and returns only by an allowed
route name. Signed-out targets are Login and enabled Register; signed-in targets
are Dashboard and Access Denied. Query values are dropped. External addresses,
unknown routes, repeated 404s, unavailable registration and inaccessible routes
fall back to Login or Dashboard according to the current demo session. The action
never follows arbitrary browser history or a query-provided redirect URL.

Sign in, then use Preview page not found on the temporary Dashboard to inspect
the signed-in state in the same tab. Direct entry into `/404` or any missing URL
also works for signed-out users. Existing startup instructions are in
[Local setup](local-setup.md).

## Files

- `frontend/src/views/error/NotFoundView.vue`: public 404 page and recovery logic.
- `frontend/src/assets/not-found-illustration.svg`: original signpost illustration.
- `frontend/src/router/index.js`: explicit `/404` and catch-all view routes.
- `frontend/src/layouts/StatusLayout.vue`: signed-out header support; signed-in layout preserved.
- `frontend/src/views/DemoDashboardView.vue`: one P04 preview link.

Login, Register and Access Denied view files remain unchanged. This increment
contains no backend, token handling, real authorization or external API requests.

## Verification

On 2026-10-10, the six existing logic tests passed and the production build
completed without warnings. Headless Microsoft Edge browser checks passed:

- Explicit `/404`, unmatched paths and session-aware titles and recovery actions.
- Public Sign in and signed-in Dashboard navigation without unavailable menus.
- No reflected paths, query values, resource identifiers or search UI.
- Go Back to permitted Login, Register, Dashboard or Access Denied routes.
- Safe fallback for external addresses, missing/repeated error pages, denied
  destinations and disabled registration; query values are dropped.
- Keyboard activation, sign out and refresh from signed-in to public 404.
- No horizontal overflow at 1440, 1024, 768, 390 and 320 pixels in either state.
- Login, Register and Access Denied navigation regression checks.
- Production preview unknown routes, public/signed-in recovery, refresh and P03.

Desktop and mobile screenshots were reviewed. No console errors or external API
requests were observed. The optional disabled-registration test ran in an isolated
Vite process, which was stopped after verification. Frontend source and production
output passed the Han-character scan. Excluding preserved IDE metadata, the
repository checker passed 247 files. The full repository checker retains the
known failure in existing `.idea/vcs.xml`; that user modification was preserved.

## Design reference

The prototype correction is authoritative for removing search and selecting
recovery actions by session state. The preparation document's future backend
and authentication instructions are not implemented in this frontend-only phase.

The illustration/message/recovery arrangement was compared with the
[official RuoYi-Vue3 404 source](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/error/404.vue)
on 2026-10-10. No upstream source, image or Chinese content was copied.

P04 was reviewed and confirmed before C01 development. The signed-in safe-back
allowlist now also permits the implemented Published Jobs list; recovery actions
and the approved layout are unchanged. C01 awaits separate review.
