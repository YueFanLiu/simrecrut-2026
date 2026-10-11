# Frontend Register

P02 is available at `/register`. It reuses the approved Login brand layout and
the recruitment illustration. Login's Create a new account button now opens this
page. This document covers the second page unit. Access Denied and Page Not Found
are implemented separately; full work areas are deferred.

## Implemented behaviour

The form contains Username, Email address, Password, Confirm password and a demo
terms checkbox. All labels, rules and feedback are in English. The prototype
rules require 3-20 letters, numbers or underscores for the username and at least
eight characters including letters and numbers for the password. Email must
have a valid format and no more than 100 characters. Password confirmation must
match, including after editing the first password. These rules are provisional;
the approved backend policy will replace them when integration begins.

Both password fields have independent keyboard-accessible visibility buttons.
Submission by button or Enter shows loading and disables the form. Used usernames
and emails are rejected without leaving the form. A successful registration
shows a Candidate account confirmation and a Go to sign in button. Registration
does not automatically sign the user in. No role selector is provided.

The created demo account can sign in by username or email while the current tab
remains loaded. Account credentials stay in the mock module's memory; refreshing
or closing the tab clears them. They are not written to local storage, logs,
files or a server. Login's Remember Me continues to store only an identifier.
No real account, token, verification email or API request is created.

The prototype's Terms of Use and Privacy Policy links open short, explicitly
labelled demo notices in a dialog. These are not finalized legal documents.

## Files

- `frontend/src/views/auth/RegisterView.vue`: form, notices, success and unavailable states.
- `frontend/src/utils/registration-validation.js`: the provisional form rules.
- `frontend/src/components/simrecrut/PasswordInput.vue`: shared password visibility control.
- `frontend/src/config/demo.js`: registration display switch.
- `frontend/.env.example`: optional demo switch example.
- `frontend/tests/registration.test.js`: registration validation and mock account checks.

The existing `mock/auth.js` now supports temporary registration and duplicate
checks. `router/index.js` adds the lazy-loaded Register route and redirects signed-in
demo users away from it. `AuthLayout.vue` accepts a title ID without changing
Login's default. `FieldIcon.vue` adds an email icon. `LoginView.vue` enables the
registration entry and uses PasswordInput; the approved layout stays the same.

Startup and the unavailable-state switch are documented in [Local setup](local-setup.md).
The existing [Login page](frontend-login.md) remains part of the regression check.

## Verification

On 2026-10-10, all six Node tests and the production build passed. Browser checks
in headless Microsoft Edge covered required fields, invalid formats, password
mismatch and revalidation, independent keyboard password visibility, terms and
privacy dialogs, duplicate username/email correction, loading, Enter submission,
success and Candidate-only accounts. Registered accounts signed in by username
and email, and were cleared by refresh without storing credentials in browser
storage. The default demo login, Remember Me, logout and public-page guards
passed regression checks.

Normal, validation-error and dialog states had no horizontal overflow at 1440,
1024, 768, 390 and 320 pixels. Desktop, mobile, error and success screenshots
were visually reviewed. A separate Vite process with the demo switch disabled
verified the unavailable state and disabled Login registration entry. It was
stopped after verification. The production preview passed registration through
success, Login, temporary dashboard and logout. No console errors or external
requests were observed.

Frontend source and production output passed the Han-character scan. The
repository checker passed 240 files when excluding the preserved, pre-existing
IDE metadata. The unfiltered checker still reports Han characters in
`.idea/vcs.xml`; that user modification was not changed.

## Design references

The supplied prototype's page 3 defines the normal, invalid-input, mismatch,
success and unavailable states. Its correction limits registration to ordinary
Candidate accounts and defers final email and password policy to the backend.
The current frontend-only user request takes precedence over future backend
instructions in the preparation document.

The form/rules/loading and return-to-login structure was compared with the
[official RuoYi-Vue3 Register source](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/register.vue)
on 2026-10-10. Upstream implementation, Chinese content and API calls were not copied.

P02 and P03 have been reviewed and approved. P04 is implemented separately and requires
confirmation before another page unit is developed.
