# Frontend Access Denied

P03 is implemented at `/403` for an active frontend demo session. It follows
page 4 of the supplied prototype: SimRecrut branding, a lock illustration, the
403 status, Access Denied heading, possible reasons, workspace context and
recovery buttons. It uses a new shared StatusLayout with a compact header.

## Behaviour

Sign in and select Preview access denied on the temporary dashboard to inspect
the page. No business area or backend permission check is implemented. The
illustration is an original local SVG; the prototype's unavailable navigation
menus and sensitive resource identifiers are omitted. The header displays the
demo user and Sign out. Only Demo dashboard is shown as an available workspace.

Back to previous page and Go to dashboard both return to `/dashboard`. The
dashboard is currently the only accessible signed-in destination. The Back
action deliberately uses a known route rather than arbitrary browser history,
query parameters or a referrer, so direct entry and untrusted URLs cannot lead
to another workspace or site. Future work areas must provide their permitted
return destinations when they are actually implemented.

`/403` requires the existing in-memory demo session. Unauthenticated visits and
refreshes return to Login because refresh clears the session. Sign out from
the page clears the session and opens Login. Authentication loss is not shown
as an access-denied error. No token or backend authorization was added.

## Files

- `frontend/src/views/error/ForbiddenView.vue`: message, reasons and recovery actions.
- `frontend/src/layouts/StatusLayout.vue`: reusable header, content area and footer.
- `frontend/src/assets/access-denied-illustration.svg`: local access-denied illustration.
- `frontend/src/router/index.js`: lazy-loaded, demo-session-protected `/403` route.
- `frontend/src/views/DemoDashboardView.vue`: one preview link for reviewing P03.

Login and Register views are not changed. Their existing demo flow is checked
alongside P03. Startup remains documented in [Local setup](local-setup.md).

## Verification

On 2026-10-10, all six existing logic tests passed and the production build
completed without warnings. Headless Microsoft Edge checks passed:

- Unauthenticated direct entry and refresh return to Login.
- An active demo user sees the 403 page, correct title and user context.
- Both recovery actions return to Dashboard, including keyboard activation.
- External history entries and query-supplied destinations are not followed;
  resource query values are not rendered.
- Sign out clears the demo session and opens Login.
- Normal rendering at 1440, 1024, 768, 390 and 320 pixels has no horizontal
  overflow and keeps action buttons within the viewport.
- Login and Register still work, including a newly registered account visiting P03.
- Production preview supports Login, P03, recovery and logout.

Desktop and mobile screenshots were visually reviewed. No browser console errors
or external API requests were observed. Frontend source and production output
passed the Han-character scan. The repository checker passed 244 files when
excluding preserved IDE metadata. The unfiltered checker still reports existing
Han characters in `.idea/vcs.xml`; that user modification remains untouched.

## Design reference

The prototype's correction requires permitted navigation only and routes expired
sessions to Login. The preparation document also distinguishes unauthenticated
and forbidden responses. The current user request keeps this demonstration
frontend-only; no API, token, backend or role enforcement is introduced.

The official RuoYi-Vue3 source consulted on 2026-10-10 has an unauthorized page
at `/401`. SimRecrut deliberately uses the prototype's `/403` route and HTTP
terminology while borrowing the illustration/message/recovery arrangement.
It does not copy upstream source or reproduce its Chinese text.

- [RuoYi-Vue3 unauthorized page](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/error/401.vue)
- [RuoYi-Vue3 router](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/router/index.js)

P03 has been reviewed and approved. P04 is implemented separately and requires
confirmation before another page unit is developed.
