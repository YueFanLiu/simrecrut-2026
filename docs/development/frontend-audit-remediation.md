# Frontend Audit Remediation

Date: 2026-10-11. This document updates the findings in [the compliance audit](frontend-requirements-compliance.md). Scope remains frontend only.

## Completed changes

- R02 uses explicit English validation with `novalidate`. Empty input produces `Complete all seven required fields.` instead of a browser-language constraint tooltip. Valid configuration still creates a draft.
- The three stale page notices were replaced. Additional HR version/wizard notices were updated to match the new checkpoint behaviour.
- Personal `.idea` files were removed from Git tracking. Their existing local copies are preserved and the existing ignore rule applies. No IDE setting was rewritten. The repository language/link/layout checker now passes.
- HR public publications reach Candidate list and detail views, including new versions and publicly visible requirements. Internal weights and hidden requirements are excluded. Previously approved catalog examples remain available.
- Admin model registration/release metadata feeds the Research registry and compatible active-model dropdowns. Historical experiment versions are not rewritten. HR configuration and Admin model metadata survive role switching and reloads in the same tab.
- Built-in demo sessions survive reloads for up to six hours using an identifier-only tab checkpoint. No password, JWT or genuine authentication credential is saved. Registered demo accounts retain their original memory-only behaviour.
- Confirmed Candidate checkpoints retain public job/version references, confirmation time and method statuses. An interrupted run restores as retryable and keeps completed methods. Results are regenerated from fixed fixtures. PDF data, file metadata, evidence, professional blocks, research attributes and review reasons are not persisted.
- Confirmation now discards the full professional block, which fixed mock scoring never consumes. Unconfirmed review drafts remain temporary mock state and disappear on reload/logout.
- Research experiment configuration, selected fixture IDs, controlled pair metadata and synthetic outputs survive reload. Professional blocks are reconstructed from existing synthetic subject fixtures; they are not persisted in experiment checkpoints. Review assignments and submissions remain memory-only.
- New Research executions produce explicitly labelled deterministic teaching outputs. Comparison counts and metrics are calculated from those outputs and synthetic labels. Empty denominators return Unavailable. Genuine held-out and demographic evaluation data stays unavailable.
- Job publications, model approvals/releases and review submissions create local safe audit events. Only operational metadata is recorded, with a maximum of 100 local events per tab. These sit beside bundled examples and are not a production audit trail.

## Core files

| Files | Purpose |
| --- | --- |
| `frontend/src/views/research/ExperimentCreate.vue` | English validation and active-model selection |
| `frontend/src/mock/shared-demo.js` | Public job and model metadata shared between workspaces |
| `frontend/src/mock/jobs.js`, `job-details.js` | Public catalog and detail integration |
| `frontend/src/utils/demo-session.js` | Identifier-only demo session and private-field-free case projection |
| `frontend/src/store/demo-checkpoints.js`, `store/index.js` | Tab checkpoint installation and interruption recovery |
| `frontend/src/store/modules/auth.js`, `cases.js` | Restore/clear behaviour and professional data cleanup |
| `frontend/src/mock/research-results.js` | Deterministic synthetic outputs and denominator-aware summaries |
| `frontend/src/store/modules/research-module.js`, `composables/useResearchWorkspace.js` | Execution, permitted outputs and model registry use |
| `frontend/src/views/research/ExperimentDetails.vue`, `ResearchMetrics.vue` | Synthetic comparison and metric presentation |
| `frontend/src/mock/admin-audit.js` and operation stores | Safe local event recording |
| Candidate/HR/Admin notice components | Correct descriptions of current demo behaviour |
| `frontend/tests/demo-improvements.test.js` | Publication privacy, checkpoint privacy/recovery, synthetic calculations and shared model tests |

## Verification

- Browser: empty Research creation shows an English error; valid draft creation, subject selection, pair creation and synthetic execution work. After reload, the same experiment retains completed comparison outputs.
- Browser: completed the HR seven-step clone/validation/publication flow, switched to Candidate and verified Data Analyst version 1.1 in the public list/detail with Start Simulation available.
- Browser: the new metric table fits widths 1440, 768, 390 and 320 with no document-level horizontal overflow or rendered Han text. No warnings/errors were captured in the regression tab.
- Unit regression: checkpoint reload restores unfinished stages, excludes private fields, regenerates fixed result data and removes case checkpoints on sign out. Shared public job/model projections and metric counts/undefined ratios are verified.
- `npm test`: 148 tests passed, no failures.
- `npm run build`: passed.
- `scripts/maintenance/check_repository.py`: passed. Local ignored credentials and personal IDE files are outside the submitted repository check.

Screenshots outside the repository: `improvements-shared-job.png` and `improvements-research-metrics.png` in the existing audit artifact directory.

## Boundaries retained deliberately

Candidate CV facts are not automatically published into HR or Research. The complete requirements require explicit resource grants and permitted projections; the approved 31-unit UI does not supply a Candidate grant creation flow. Blind review and existing authorised subject/case fixtures remain intact. Role matrix edits still demonstrate configuration without creating real permissions. The service-health page correctly remains Unknown without backend health checks; a browser must not connect directly to MySQL.

These boundaries are not silently bypassed to make the demo appear fully connected. A real authorised case workflow, durable user/reviewer submissions and real metrics require the later API contracts and backend milestone. Browser checkpoints are convenience data, provide no security, and are not a substitute for that work. Before real CV extraction, replace temporary mock drafts with page-scoped data loaded from the approved API.

Checkpoint storage is tab-scoped and optional. If browser storage is unavailable, the demo remains usable in memory. Closing the tab ends stored state; sign out clears private cases, while public job/model metadata remains available for switching demonstration roles.
