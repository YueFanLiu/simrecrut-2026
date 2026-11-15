# API and mail languages

The main Vue app sends its selected language in `Accept-Language` on every API
request, including public registration and recovery calls. Supported languages
are English, Chinese and French; unsupported or malformed preferences fall back
to English. Regional tags and weighted preferences are supported. Preferences
are request-scoped; the backend does not change JVM defaults or stored accounts.

`ApiLanguageFilter` runs before security, binds the request locale and restores the
previous context on every exit. `ApiLanguageConfiguration` supplies the same
locale negotiation to Spring MVC and synchronous mail delivery. Responses include
`Content-Language` and `Vary: Accept-Language` without changing existing cache or
retry headers.

`ApiLanguageResponseAdvice` translates only the standard `ApiResponse.message`,
job validation issues/warnings and the typed case-status diagnostic. It never
walks arbitrary JSON, translates user documents or changes machine codes, IDs,
revision numbers, flags or stored data. Security handlers translate their JSON
errors directly because those responses bypass MVC advice. Registration and
password-reset mail subjects and bodies use the same request locale.

The UTF-8 `messages.properties`, `messages_zh.properties` and
`messages_fr.properties` catalogues share keys. API message keys are
`ApiMessages.keyFor(originalEnglishMessage)`; the original English message stays
in the base bundle. Named mail and validation templates use one-pass parameters.
Add matching frontend fallback translations when adding an API message; the
frontend's `api-language.test.js` checks that both sides stay synchronized.
Unknown service diagnostics keep their original text. User content must not be
added to translation catalogues or supplied to the message translator.

Validation:

- `./mvnw.cmd test`: backend regression suite.
- `./mvnw.cmd -Dtest=ApiLanguageTest test`: language negotiation, envelope/data
  boundaries, validation, pre-controller security failures, request isolation,
  template parity and mail generation through a mocked sender.
- From `frontend`, `node --test tests/*.test.js` and the production build verify
  request headers, translated errors, retained error metadata and UI behavior.

Mail tests do not send email. Redis and scanned-PDF OCR integration checks require
`SIMRECRUT_TEST_REDIS` and `TESSDATA_PATH` respectively. Start the configured backend
and frontend for a live database/SMTP workflow; frontend build success alone does
not verify that deployment configuration.

On this Windows setup, `scripts/dev/test-local.ps1` validates the installed OCR
models, starts loopback-only Redis if needed, enables both integration checks and
runs the full backend suite. OCR models are stored outside version control under
`runtime/ocr/tessdata`; the script restores its temporary process variables after
finishing. It leaves an already-running Redis instance and its data intact.

Known dynamic category/code-catalogue diagnostics and typed review-draft messages
now use named templates at the HTTP boundary. Objective explanation text is
translated only inside typed Candidate/HR result projections; arbitrary JSON,
job text and human review reasons remain outside that translation boundary.
