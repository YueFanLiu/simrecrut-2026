# Frontend login

`/login` reuses the responsive shared authentication layout. Connected mode calls
`POST /api/v1/auth/login` with `identifier` and `password`, stores the returned
`data.accessToken` in the session cookie and reads `GET /api/v1/auth/me`.
Roles and permissions come from the server. Uppercase role codes are normalized
for existing frontend navigation; server access checks remain authoritative.

Invalid public credentials stay on the form with the server error. Protected
401 responses clear the token and return to login. Refresh restores identity
from `/me`. Sign out calls `/api/v1/auth/logout` and clears private frontend state
even if the service is unavailable. Candidate login opens the connected CV library;
other roles open the workspace dashboard. HR and Admin alone cannot enter the CV library.

Remember Me stores only the identifier after successful sign-in. Passwords are
cleared after success and never stored by Remember Me. Password visibility,
required-field validation, loading protection and keyboard submission remain available.
The backend has no captcha, registration-options or password-reset endpoints;
the connected login page does not request them.

The existing explicit demo mode keeps its simulated accounts and does not call
identity APIs. Run `npm run dev:connected` for backend integration; see
[Local setup](local-setup.md). Registration is described in
[Frontend register](frontend-register.md).

Contract regression tests cover the JWT envelope, role normalization, logout,
incomplete identity cleanup and public/protected 401 behavior. Browser checks use
intercepted API responses and generated synthetic PDFs; they do not establish that
SMTP, Redis, database access or real extraction work in the target environment.
