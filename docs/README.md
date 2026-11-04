# Documentation index

This directory contains the authoritative human-readable project documentation.
The root [README](../README.md) summarizes the repository and links here.
Machine-readable API and feature contracts belong in `contracts/`; SQL migrations
belong in the Java resources directory. Keep each topic in one authoritative page.

## API

- [HR cases and assigned Human reviews](api/hr-cases-and-human-reviews.md): scoped
  HR results, primary assignments, blind review, submission and deferred permissions.

- [Candidate simulation case API](api/candidate-case-workflow.md): job-linked
  cases, reviewed facts, durable assessment tasks, public results and privacy deletion.

- [Independent account and permission management](api/identity-management.md):
  account administration, role permissions and mailbox password setup/recovery.

- [Generate and share API documentation](api/api-documentation.md): springdoc,
  Swagger UI, YAML export, token-based testing and frontend handoff.
- [Job workflow API](api/job-workflow.md): document-first HR and Candidate job
  endpoints, structured requests, revisions, extraction and deployment prerequisites.

## Architecture

- [System overview](architecture/system-overview.md): components, layers, data
  ownership, tasks, and model releases.
- [ADR 0001](architecture/decisions/0001-repository-and-runtime-boundaries.md):
  repository organization, runtime boundaries, and JDK 17.

## Development

- [Connected resume workflow](development/standard-resume-workflow.md): authenticated
  own-resume APIs, permissions, request fields, storage, and verification boundaries.
- [Coding standards](development/coding-standards.md): English-only content,
  formatting, contracts, and comments.
- [Documentation policy](development/documentation-policy.md): locations,
  naming, decisions, sources, and maintenance.
- [Local setup](development/local-setup.md): authoritative environment and
  configuration instructions.
- [Frontend Login](development/frontend-login.md): P01 implementation structure
  and frontend-only demo scope.
- [Frontend Register](development/frontend-register.md): P02 Candidate demo
  registration, form rules, success and unavailable states.
- [Frontend Access Denied](development/frontend-forbidden.md): P03 explanation,
  safe recovery and separation from unauthenticated navigation.
- [Frontend Page Not Found](development/frontend-not-found.md): P04 public
  missing-route handling and session-aware recovery.
- [Frontend Published Jobs](development/frontend-published-jobs.md): C01 current
  public job summaries, local search, filters, sorting and pagination.
- [Frontend Job Detail](development/frontend-job-detail.md): C02 public details,
  published-version consistency, loading and unavailable-job recovery.
- [Frontend CV Upload](development/frontend-cv-upload.md): C03 local PDF checks,
  memory-only cases, simulated extraction, retry and deletion.
- [Frontend CV Fact Review](development/frontend-cv-review.md): C04 professional
  corrections, short mock evidence, saves and confirmation locking.
- [Frontend Simulation Progress](development/frontend-simulation-progress.md): C05 mock method states, retries and confirmation locks.
- [Frontend Simulation Results](development/frontend-simulation-results.md): C06 five result cards, separate score scales, human status refresh and local deletion.
- [Frontend My Simulations](development/frontend-my-simulations.md): C07 owned case history, state-aware continuation, expiry and deletion.
- [Frontend HR Jobs](development/frontend-hr-jobs.md): H01 managed-job list, filters, pagination and HR demo role.
- [Frontend HR Job Wizard](development/frontend-hr-job-wizard.md): H02 shared seven-step editor, reviewed requirements, exact weights and local publication.
- [Frontend HR Job Versions](development/frontend-hr-job-versions.md): H03 immutable history, read-only detail and single-draft cloning.
- [Frontend HR Authorized Cases](development/frontend-hr-cases.md): H04 managed/granted case summaries, query filtering and privacy-aware visibility.
- [Frontend HR Case Details](development/frontend-hr-case-detail.md): H05 read-only snapshots, contributions, five outcomes and blind visibility.
- [Integrated Research frontend](development/frontend-research-module.md): adapted WANG R01-R06, review findings, repaired interactions and verification.
- [Previous Research Experiments](development/frontend-research-experiments.md): archived standalone R01 implementation history.
- [WANG frontend import](development/wang-frontend-import.md): source snapshot, Research view locations and integration boundary.
- [Frontend My Review Tasks](development/frontend-my-review-tasks.md): V01 assigned HR/Researcher tasks, blind summaries, filters and Research assignment connections.
- [Frontend Review Task Details](development/frontend-review-task-detail.md): V02 blind detail projection, validated independent review and confirmation locking.
- [Frontend User Management](development/frontend-admin-users.md): A01 Admin mock directory, account metadata, validation and confirmed status changes.
- [Frontend Roles and Permissions](development/frontend-admin-roles.md): A02 fixed roles, menu/action trees, permission matrix and mock account assignment.
- [Frontend Model Versions and Release](development/frontend-admin-models.md): A03 synthetic offline metadata registration, checked lifecycle transitions and active-slot replacement.
- [Frontend Frozen Protocols](development/frontend-admin-protocols.md): A04 read-only protocol metadata, frozen configuration summaries and missing-data states.
- [Frontend Service Health](development/frontend-admin-health.md): A05 unknown runtime health, check freshness, model slots and workspace metadata.
- [Frontend Failed Jobs and Errors](development/frontend-admin-failures.md): A06 safe task metadata, combined filters, technical details and unavailable manual retry.
- [Frontend Audit Logs](development/frontend-admin-audit.md): A07 safe read-only events, applied queries, pagination and nullable metadata.
- [Frontend Overall Review](development/frontend-overall-review.md): 31-unit workflow verification, Candidate route repair and explicit mock integration boundaries.
- [Java setup](development/java-setup.md): Java 17 build and runtime prerequisites.
- [Repository checks](development/repository-checks.md): automated scaffold,
  language, and documentation checks.

## Database

- [Initial business schema](database/initial-schema.md): the 36-table baseline,
  logical reference inventory, implementation choices and Flyway verification.
- [Review and active model uniqueness](database/review-model-uniqueness.md):
  V2 constraints, preflight checks and concurrent-write acceptance for A009.

## Reserved topics

| Location | Content added with the corresponding implementation |
| --- | --- |
| `api/` | Public and internal API semantics, roles, errors, and contract guidance. |
| `database/` | Schema ownership, logical references, migration and backup procedures. |
| `research/` | Research protocol, data provenance, training, and evaluation methods. |
| `deployment/` | Containers, internal networking, readiness, releases, and recovery. |

Add a link here when creating a page in a reserved topic. Do not create duplicate
module guides or place implementation status notes in unrelated source folders.

- [Shared resume cleaning](development/resume-cleaning.md): Java online/offline draft extraction.

## Frontend development

- [Frontend setup and architecture](development/frontend.md)
- [Page ownership and maintenance](development/frontend-page-ownership.md)
