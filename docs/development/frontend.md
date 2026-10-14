# SimRecrut frontend

English research, reviewer and administration pages based on the first-round prototype collection dated 2026-10-10.

## Start

Install Node.js 20.19+ or 22.12+ and pnpm, then run from the frontend directory:

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Open http://127.0.0.1:5173/research/experiments. Run `pnpm build` to produce `dist`.

## Scope

R01-R06, V01-V02 and A01-A07 are implemented. Experiment creation requires seven fields and creates a draft without starting execution. Reviews require Accept/Reject, a reason and confirmation, and lock after submission. Protocols and audit details are read-only. Model release transitions and controlled retries have confirmation panels.

This is a frontend prototype. All displayed records are synthetic; edits remain in memory and reset on reload. Authentication, authorization, database persistence, execution, metric computation and server-side audit filtering must be connected to the Java 17 backend before production use. Unknown health and missing metrics are explicitly shown as unavailable.

## Source

- `src/App.vue`: application shell and workspace provider.
- `src/views/research/`: R01-R06, one Vue component per page.
- `src/views/reviewer/`: V01-V02, one Vue component per page.
- `src/views/admin/`: A01-A07, one Vue component per page.
- `src/components/`: shared navigation, record table and record drawer.
- `src/router/`: page definitions and component mapping.
- `src/state/workspace.js`: shared session state and demo actions.
- `src/api/demoFixtures.js`: explicitly synthetic fixtures.
- `src/style.css`: shared visual design and responsive layout.
- `public/resume-workspace`: existing CV workspace retained separately.

The frontend uses Vue and Vite. The backend JDK 17 requirement is unchanged.

## Team maintenance

Edit a page in its own `views` file. Modify `components` only for behavior shared across pages. Shared demo records and actions belong in `state`; production API integration should use dedicated files under `api`, not inline network calls inside templates. Register new pages in both router files. See [page ownership guide](frontend-page-ownership.md).

The standalone resume workspace uses ordered deferred scripts, with domain files under `public/resume-workspace/views`, transport under `services` and shared DOM/state under `core`. Its root `app.js` is startup wiring. These scripts share lexical state and must load in the order specified in `index.html`. The Java launcher copies the full asset directory recursively.

## RuoYi integration

See [RuoYi migration](ruoyi-migration.md) for identity endpoints, permissions, deployment modes and candidate resume routes. The authenticated shell is now `frontend/src/layout/index.vue`; `App.vue` delegates to Vue Router.
