# Local branch integration

## Ownership and retained sources

The integrated WANG checkout combines yuefan `ed467f5` frontend pages, Zheng
`845c6c2` database baseline, and WANG `5975b31` RuoYi authentication and shared
resume cleaner. Page conflicts use yuefan's implementation; database conflicts
use Zheng's baseline. Backend identity and resume adapters remain from WANG.
No remote branch is modified by this local merge.

## Frontend runtime

Use `pnpm install --frozen-lockfile`, `pnpm test` and `pnpm build` in frontend.
The pnpm lock is authoritative; the competing npm lock has been removed.
Development preserves the full prototype in explicit demo mode. Connected mode
uses the same yuefan login design with RuoYi tokens and captcha, then routes to
`/candidate/resumes`. Existing backend resume permissions remain authoritative.
The real resume adapter is retained alongside yuefan's simulated case pages.
It does not replace the preferred prototype's case workflow.

Set `VITE_APP_DEMO_MODE=false` for connected development and restart Vite.
The `/dev-api` proxy forwards to localhost:8080. Production uses `/prod-api`
and the existing reverse proxy. Demo checkpoints never restore a real session.
Business screens with mock-only APIs remain accessible in demo mode; connected
navigation cannot silently report mock actions as successful backend operations.

## Database boundary and remaining implementation

Follow [database initialization](../database/initial-schema.md) and
[RuoYi setup](ruoyi-migration.md). The original business SQL is unchanged;
identity documentation now points to the actual RuoYi `sys_user.user_id`.
Initialization is explicit because the RuoYi runtime disables Flyway.
No cloud database initialization, Redis deployment or live login is performed
by this source integration.

The current resume adapter still has its separate temporary SQLite index and
`sim_cv_cleaned` JSON publication table. Business cases, job versions, experiments,
reviews, scoring and model lifecycle APIs must be implemented against Zheng's
business schema before yuefan's mock stores can be replaced. Model training is
outside these three branch changes.

## Validation

Run the Java build with JDK 17, the frontend tests and production build, and
`scripts/maintenance/check_repository.py` before sharing the integrated branch.
Local integration commits are not pushed automatically.
