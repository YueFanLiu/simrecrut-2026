# Guest browsing and authentication return (frontend only)

Visitors can read published jobs and their requirements. Selecting sign-in or registration from a job retains that route, including an existing bounded CV-selection context. Login → registration → sign-in, registration success, recovery → sign-in, a protected deep link and an expired-session HTTP/business 401 all preserve the return target. No passwords, email codes, email addresses, CV contents or auth tokens are added to the URL or persisted for this handoff; the existing `redirect` route parameter carries the local page.

`utils/auth-navigation.js` validates normalized local paths, rejects external/protocol-relative paths, backslashes, control characters and auth-page loops, and resolves targets against the registered router. Unknown targets use the normal role-specific workspace. Signed-in visits to login/registration use the same return behavior. The regular route guard still enforces roles and the backend remains authoritative. Public guest browsing links return only to the jobs list/detail, never to private CV or case pages.

Registration still requires the existing email verification and explicit notice acknowledgement. It does not sign the user in, choose a different account role, or create a simulation. After signing in, the job is loaded again: an unpublished/missing job uses the existing unavailable state. The user selects **Start Simulation** explicitly, preventing an automatic or duplicate case creation from auth callbacks/back navigation. Login/registration errors retain the route context and existing form behavior.

The target is kept in the URL for reload/back behavior; it is not stored globally across tabs. Expired sessions must sign in again; this handoff does not recover unsaved form edits or guarantee a private resource still exists or is authorized. Public API availability, real email delivery and account creation remain backend responsibilities.

Checks: `auth-navigation.test.js` covers memory-router deep-link/registration/sign-in navigation, role rejection, default destinations, malformed/external targets, auth loops and guest boundaries. `account-api.test.js` verifies the connected HTTP/business-401 handoff without sending email or creating real accounts. Browser testing uses a disposable local API and invented registration data.

Private RouterViews are unmounted when the authenticated session is cleared,
and their component key includes the account name so local CV state cannot be
reused after switching accounts. Logout waits for that unmount before navigating
to sign-in; an unsaved-change guard cannot retain the previous user's private
page after logout. `session-route-boundary.test.js` exercises the real App
template with a guard that always cancels, including an account switch.
