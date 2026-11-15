# Current implementation status

Reviewed against the local develop implementation on 2026-11-16.

Implemented code covers the Vue workspaces, JWT/Redis identity, registration and
recovery mail, structured resume import/review, HR job drafts and publication,
Candidate case state and tasks, deterministic objective matching, and primary
human-review assignment/submission. These modules have API adapters and backend
services. Many database and model test boundaries use mocks; their passing results
do not certify a complete live multi-role workflow.

The independent Python inference process, offline training/evaluation/packaging,
Neutral and Biased model artifacts, persistent research experiments and several
administration operations remain incomplete. Their demonstration pages and client
function declarations must not be counted as implemented services. Connected
routes for unavailable modules remain explicit.

## Repair coverage

- Local commands share literal `.env` parsing and the configured Java port.
  Launch readiness uses `/actuator/health/readiness` and its UP status rather than
  the removed RuoYi captcha endpoint or a legacy success envelope.
- Repository policy allows Chinese only in declared zh translation values. Source
  literals use Unicode escapes where needed in tests; other language/layout checks
  remain enabled. Translation and readiness helper tests run in repository CI.
- Dynamic known-category and code-catalogue validation messages use named backend
  templates. Typed draft diagnostics and objective explanations localize at the
  HTTP boundary while user facts, jobs, human reasons and machine codes stay intact.
- Objective strengths/gaps are derived from computed matches and mandatory gates.
  Categories containing hidden requirements are omitted from public explanations.
  Numerical scores and gates keep their original calculation. Older results with
  no explanations remain valid and display an explicit empty state.
- Frontend/Nginx/Java/Redis Compose wiring and read-only artifact mounts are
  provided in the [container guide](../deployment/container-stack.md).

## Checks

Use `scripts/dev/test-local.ps1` for the complete local Java suite with Redis and
OCR. From frontend, run `node --test tests/*.test.js` and the Vite build. Also run:

```powershell
python scripts/maintenance/check_repository.py
python scripts/maintenance/test_check_repository.py
powershell -NoProfile -File scripts/dev/test-local-environment.ps1
docker compose --env-file .env -f deploy/compose.yaml config --quiet
```

Before acceptance, exercise real MySQL migrations and transactions, SMTP delivery,
role/session invalidation, full case/HR/review flows, and the model/research runtime
with actual versioned packages. Container syntax and unit tests are separate from
those deployment checks. Historical October audit documents describe their dated
state and can differ from this implementation.
