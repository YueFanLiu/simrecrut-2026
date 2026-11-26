# Interface languages

The Vue application supports `en`, `zh`, and `fr`. English is the default;
`simrecrut.language` in localStorage remembers an explicit selection. Switching
updates existing views without remounting the router or clearing draft inputs.
Storage failures do not prevent switching for the current session.

Use `$t('Interface text')` in templates and `t('Interface text')` in scripts.
Add both `zh` and `fr` translations to `messages.json`, using the original English
text as the key. Use named parameters for sentences, for example
`$t('Welcome, {name}', { name: user.displayName })`.

Translate UI labels, choices, statuses and feedback. Keep model values, IDs,
permissions, API payloads, names, job descriptions, CV facts, evidence and review
reasons in their original form. A native option with translated text must have
an explicit canonical `value`; Element Plus options likewise retain their `value`.
Unknown service diagnostics fall back to their source text.

`elementLocale` configures Element Plus, and `dateLocale` configures Intl formatters.
`useLocalizedValidation(formRef)` revalidates already-visible form errors after a
language change without touching values or untouched fields. A small catalogue
of parameterized messages also localizes legacy validation strings at display time.

Run `node --test tests/i18n.test.js` from `frontend` for catalogue coverage,
canonical option values, storage recovery, locale selection, validation and
server-rendered component checks. The standalone legacy utility under
`public/resume-workspace` is separate from the Vue application.

The main API transport sends `Accept-Language` for the selected interface language.
The Java backend localizes API messages, validation details, authentication failures
and registration/recovery mail. The frontend retains localized server messages
and translates known messages if the selection changes while a request is pending.
Connection and timeout failures use a translated fallback. Response bodies and
request data remain intact; rejected requests preserve status, request IDs, machine
error codes and retry headers. `tests/api-language.test.js` checks these boundaries
and verifies that backend and frontend message catalogues agree.
