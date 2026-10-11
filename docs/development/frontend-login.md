# Frontend Login

The first implemented page is P01 at `/login`. The implementation follows the
provided prototype's two-column brand and form layout, with a compact stacked
layout on small screens. The illustration is an original local SVG inspired by
the prototype's recruitment theme; no upstream RuoYi code or imagery is copied.

## Scope

Vue 3 Composition API, Vite, Element Plus, Vue Router and Pinia form the frontend
foundation. Login accepts a username or email, validates required fields and email
format, toggles password visibility, displays an English failure alert and
disables submission during the simulated response. Remember Me saves the account
identifier only after a successful login and never saves a password.

P01 was reviewed and approved before P02 development. The Create a new account
button now opens [Register](frontend-register.md), unless the optional demo switch
disables registration. PasswordInput is shared by the two pages; Login's approved
layout and existing sign-in behaviour are preserved.
Captcha requires an actual service configuration and is omitted in this demo.
The prototype correction excludes unconfirmed password recovery.

The preparation document describes future RuoYi authentication and backend work.
The current frontend-only request takes precedence: no token, HTTP client, real
authentication, role validation, database or AI integration is implemented.
The route guard controls demonstration navigation only.

## Implemented files

```text
frontend/
|-- index.html
|-- package.json
|-- package-lock.json
|-- vite.config.js
|-- public/favicon.svg
|-- tests/auth.test.js
`-- src/
    |-- App.vue
    |-- main.js
    |-- assets/
    |   |-- styles.css
    |   `-- recruitment-illustration.svg
    |-- components/simrecrut/
    |   |-- BrandMark.vue
    |   `-- FieldIcon.vue
    |-- layouts/AuthLayout.vue
    |-- mock/auth.js
    |-- router/index.js
    |-- store/
    |   |-- index.js
    |   `-- modules/auth.js
    |-- utils/
    |   |-- login-validation.js
    |   `-- remembered-account.js
    `-- views/
        |-- auth/LoginView.vue
        `-- DemoDashboardView.vue
```

Existing feature directories and `.gitkeep` files are retained. They represent
future pages rather than implemented features.

`AuthLayout` and `BrandMark` can be reused by later public pages. `LoginView`
owns local form state and submission feedback. The Pinia auth store owns the
in-memory demo user. `mock/auth.js` provides one asynchronous login boundary
that can later be replaced with the approved backend contract. Validation and
remembered-identifier helpers keep browser storage and form rules easy to inspect.

After login, `/dashboard` displays only a success message and Sign out. It has no
work-area menu, statistics or business functionality. Unauthenticated dashboard
visits return to `/login`. Unknown routes now show the public
[Page Not Found](frontend-not-found.md) view introduced with P04.

## References and verification

Startup commands and demo credentials are maintained in [Local setup](local-setup.md).
The initial browser review uses desktop, tablet and mobile sizes and checks the
normal, invalid-input, failure and loading states. Run the repository language
check from the repository root to verify all authored files and filenames.

Verification on 2026-10-10 passed three Node tests and the production build with
no build warnings. Headless Microsoft Edge checks passed validation, failed login,
loading, keyboard password visibility, Enter submission, username/email login,
Remember Me, logout, refresh, route guards, disabled registration and widths of
1440, 1024, 768, 390 and 320 pixels. No horizontal overflow, console errors or
external API requests were observed. Production preview login and logout also
passed. Screenshots were reviewed at desktop, tablet and mobile sizes.

All frontend authored files and generated production text passed the Han-character
scan. The full repository checker reports existing Han characters in tracked
`.idea/vcs.xml`; that pre-existing user modification was preserved. A separate
read-only run excluding IDE metadata passed the remaining 233 files. This does
not mean the unfiltered repository check passes.

Element Plus's default CSS includes a localized font-family name. The small
PostCSS rule in `vite.config.js` replaces that font declaration with English font
names so the production CSS also meets the project language requirement.

The architecture uses the public Login route, lazy-loaded views, form validation
and store separation illustrated by the official RuoYi-Vue3 source. It does not
copy its cookies, encryption, permissions or authentication implementation.

- [RuoYi-Vue3 Login source](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/login.vue)
- [RuoYi-Vue3 router source](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/router/index.js)

Sources consulted on 2026-10-10. P01, P02 and P03 are approved. P04 is implemented
separately and must be confirmed before another page unit is developed.
