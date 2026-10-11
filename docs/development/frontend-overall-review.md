# Frontend Overall Review

Review date: 2026-10-10. Scope: the 31 approved frontend prototype units and their existing entry points, using synthetic local data only.

## Result and repair

One active defect was found: all seven Candidate routes required a demo session but lacked the Candidate role restriction used by the other workspaces. HR, Researcher and Admin could enter Job Details and start a Candidate simulation. The router now adds `demoRole: 'Candidate'` to C01-C07. Existing Candidate pages and workflows are preserved; other roles receive Access Denied. This is navigation control for the demo, not real backend authorisation.

No new page, backend service, API integration, dependency, model operation or user credential was added. Existing WANG reference files and IDE metadata were preserved.

## Verification

- `npm test`: all 143 unit tests passed, including data projection, ownership/grants, validation, async cancellation, immutable versions and release safety.
- `npm run build`: passed before and after the route repair. Production preview verified the repaired routes.
- Browser route matrix: 150 checks across anonymous, Candidate, HR, Researcher and Admin sessions; four email-based logins; unknown URL recovery; production denial of non-Candidate sessions on Candidate routes.
- Full-page sweep: 38 representative pages/entries at widths 1440, 768, 390 and 320, totalling 152 viewport checks. No horizontal overflow, visible Han text or page exceptions were detected. Feature regressions also cover 1024 pixels and meaningful drawers/dialogs.
- No browser console errors or external network requests were observed in the completed workflow regressions.

| Units | Browser workflow coverage |
| --- | --- |
| P01-P04 | Password visibility, English validation, Candidate-only registration, sign-in, role entry points, access denial, unknown URL, logout and reload |
| C01-C07 | Published job to case creation, PDF checks, extraction, fact edits/evidence/save, confirmation locking, simulation stages, result scales and pending Human semantics, case history and deletion/back-forward recovery |
| H01-H05 | Managed scope and list filters, seven-step wizard validation/publication, immutable version history and cloning, authorised case filtering/grants and read-only details |
| R01-R06 | Experiment creation, subject selection, controlled variants, review assignment, local validation, metrics context, model selection and access revocation |
| V01-V02 | Assigned list filters/pagination, blind scope, HR/Researcher responsibility, decision/reason validation, confirmation, independent slots, submitted locking and revoked access |
| A01-A07 | User directory and roles, synthetic model registration/approval/activation/retirement, read-only protocols, unknown health, safe failures, read-only audit, loading/error/empty states and connected navigation |

Earlier page-stage browser scripts expected later-page buttons to remain disabled or deletion to return to the original job list. Their regression copies were updated to the approved final navigation. One old fixture test relied on stopped alternate Vite ports; its copy now uses browser-local scenario interception. These script adjustments were not product defects.

## Mock integration boundaries

The individual flow stores preserve their own state during SPA navigation and clear on logout/reload as documented. They are not a complete shared backend:

- HR publication does not add jobs to the independent Candidate published-job fixture catalog.
- Candidate uploads/results and HR/Research authorised-case fixtures are independent examples. Live Candidate content is not imported into research automatically.
- Admin model releases do not change the independent Research model catalog or existing case results.
- Audit events are static safe examples; they do not record operations performed on other demo pages.
- Health is Unknown/Unavailable without real checks; missing metrics remain unavailable. Admin task Retry remains disabled until a defined supported operation exists.
- Role permission previews and the Admin directory do not change demo login credentials, router enforcement or resource grants.

These are deliberate frontend demonstration limits already documented with the corresponding pages, not evidence of real authentication, service health or scoring.

## Language and repository checks

Authored repository language, local documentation links and LF line endings passed. Frontend source, reference text and generated build output contain no Han characters. The full repository checker still reports the pre-existing 24 Han occurrences in `.idea/vcs.xml`; that user IDE metadata was not changed. The WANG source manifest hashes were checked to confirm the reference snapshot remains intact.

## Next review

The frontend is ready for a manual demonstration of the approved flows. Before future backend connection, define typed safe responses and shared catalogs at the existing mock read/write boundaries. Retest role/resource access, fixed versions, async errors and deletion behavior when actual APIs replace these fixtures.
