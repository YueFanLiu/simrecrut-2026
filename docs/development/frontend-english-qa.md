# Frontend English and Browser QA

Date: 18 October 2026. Branch: `yuefan`. Base: `336c586` from `origin/develop`.

The existing local changes were preserved when switching branches. The local
`yuefan` branch was fast-forwarded to the current develop baseline. No changes
were committed or pushed during this check.

## Environment and scope

- Vue development server: `http://127.0.0.1:5174` (5173 was already occupied).
- Effective development setting: `VITE_APP_DEMO_MODE=true`.
- Browser checks used the built-in Candidate, HR, Researcher and Admin demo accounts.
- PDF checks used a generated, one-page synthetic PDF, an empty PDF and a text file.
- No personal CV, real account, database, email delivery or backend write was used.
- Browser checks inspected English interface text, navigation and the interactions
  listed below. They are not a complete backend or production acceptance test.

## Browser checklist

| # | Area | Result | Evidence / scope |
| --- | --- | --- | --- |
| 1 | Empty login | Pass | English username and password validation. |
| 2 | Invalid login | Pass | English invalid-credentials message; retry remained available. |
| 3 | Demo login | Pass | All four built-in accounts opened their appropriate dashboards. |
| 4 | Registration validation | Pass | Empty fields, invalid email and mismatched passwords produced English messages. No account was created. |
| 5 | Password recovery | Fixed and rechecked | Demo mode now explains that recovery requires a connected service, without requesting backend settings. Real recovery remains untested. |
| 6 | Published jobs | Pass | List and English dates rendered; search worked. Sorting/filter combinations also have automated coverage. |
| 7 | No matching jobs | Pass | English empty state; Clear Filters restored the list. |
| 8 | Job details | Pass | Software Engineer details opened and Start Simulation created a disposable demo case. |
| 9 | Wrong file type | Pass | A text file was rejected with an English PDF-only message. |
| 10 | Invalid PDF size | Pass | Empty PDF rejected in the browser. Oversized PDFs are covered by automated validation tests. |
| 11 | Valid PDF | Pass | Generated one-page PDF accepted; simulated extraction reached Ready for Fact Review. |
| 12 | Fact edits | Pass | Edited skill saved, retained its new value and displayed USER SUPPLIED. |
| 13 | Fact confirmation | Pass | Missing acknowledgement blocked confirmation in English; confirmation locked the facts. |
| 14 | Simulation and results | Pass | All three automated demo stages completed; five result cards showed separate scales and pending human reviews. |
| 15 | History and deletion | Pass | English dates, retained result, English deletion dialog, cancel preserving the case, then deletion of the disposable test case. |
| 16 | HR job form | Pass | Blank submission displayed English title, department and description errors. |
| 17 | Unsaved navigation | Fixed and rechecked | Keep Editing preserved the route and input; the browser title now also remains on Job configuration. |
| 18 | HR versions and cases | Pass | Version history, authorized case list and professional/result detail sections loaded in English. |
| 19 | Assigned review | Pass | Empty decision/reason rejected in English; synthetic review confirmation succeeded and locked editing. |
| 20 | Research experiments | Pass | List, blank creation validation, existing experiment details and frozen configuration rendered. |
| 21 | Research subjects/models/metrics | Pass | All three pages loaded in English; absent evaluation evidence remained explicitly unavailable. |
| 22 | Admin users and roles | Pass | User list, Add User validation and permission editor displayed English labels. No account or permission changes were saved. |
| 23 | Admin models and protocols | Pass | Lists and detail drawers loaded in English; no model release was performed. |
| 24 | Health, failures and audit | Pass | Service health remained Unknown; failure list/detail and audit events rendered in English. Unsupported retry stayed disabled. |
| 25 | Forbidden and missing routes | Pass | Candidate access to admin users showed 403; an unknown route showed 404, both in English. |
| 26 | Narrow layout | Pass for inspected views | Candidate progress at 451 px and Research/Admin views at 671 px had no document-level horizontal overflow. HR form and model drawer were visually inspected. This is not a full device matrix. |

## Changes made

- Applied English UI defaults and safe English fallbacks for service diagnostics,
  while retaining user-authored names, filenames and CV facts.
- Fixed English dates, confirmation buttons and standalone workspace validation.
- Replaced the standalone workspace's browser-native deletion confirmation with
  an English dialog.
- Removed three obsolete UI claims that the connected workspace uses RuoYi.
  API endpoints and authentication contracts were left unchanged.
- Prevented cancelled router navigation from changing the browser document title.
- Prevented frontend-demo password recovery from calling unavailable account APIs.

## Automated verification

- `npm test`: 153 passed, 0 failed.
- `npm run build`: passed.
- `git diff --check`: passed.
- Source, public files and production assets: no literal Han characters found.
- The build reports chunk-size and mixed static/dynamic import warnings.
- API tests can report a Vite WebSocket port warning when middleware servers run
  concurrently; assertions still pass.

## Remaining integration checks

- Real sign-in, registration, session expiry, CAPTCHA and password reset require
  the new backend API contract and running services.
- Real resume persistence, extraction and publication require backend integration.
- The standalone resume workspace's backend-served interactions were not exercised
  in this browser run. Its diagnostic fallback is covered by an automated test.
- Live role names, department names and audit titles still depend on backend data.
  Their English representation must be agreed during API integration; arbitrary
  user-authored content must not be translated or removed.
- Backend code, database migrations and startup scripts were not edited.
