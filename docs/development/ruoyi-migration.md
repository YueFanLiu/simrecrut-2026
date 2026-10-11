# RuoYi migration

The 2026-10-10 user decision replaces the independent identity backend with RuoYi for both frontend and backend. Java remains JDK 17. Existing research pages retain their independent files and visual design.

## Official dependencies

The backend uses the official `RuoYi-Vue` Spring Boot 3 branch at commit `a51a838b71b446ea27256900efe7ed2faa2a02fd`, version 3.9.2. The upstream admin, framework, common, system, quartz and generator modules are built as ordinary Maven libraries. Sources are cached outside the authored repository; no binary libraries or upstream Chinese comments are committed here. This is a real dependency on RuoYi, not a replacement authentication mock.

Run `scripts/dev/bootstrap-ruoyi.ps1 -Maven <mvn.cmd>` on Windows, or `bash scripts/dev/bootstrap-ruoyi.sh` on Linux, with JDK 17 and Maven on PATH. Then build `backend/java` with the Maven wrapper. IDEA must reload Maven after bootstrapping.

The frontend adapts the official RuoYi-Vue3 token, Axios, Pinia and permission conventions. Vue Router owns navigation, Element Plus supplies form controls, and RuoYi owns `/login`, `/captchaImage`, `/getInfo`, `/getRouters`, `/logout` and `/system/*`. The MIT notice is in [RuoYi-LICENSE](../licenses/RuoYi-LICENSE).

## Database and Redis setup

Use a dedicated empty MySQL schema. Upstream `sql/ry_20260417.sql` and `sql/quartz.sql` in the source cache initialize RuoYi system tables; these scripts contain DROP statements and must never be run blindly against existing application tables. Database initialization is an explicit deployment step, not an application startup action. Flyway is disabled for the RuoYi runtime to avoid mixing incompatible identity schema histories.

Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, optional `REDIS_PASSWORD`, and a unique `RUOYI_TOKEN_SECRET` of at least 32 random characters in the server environment. Keep verified TLS enabled for Aiven. Rotate the upstream initial administrator password after initialization. RuoYi requires Redis for tokens and captcha challenges; Aiven MySQL alone is insufficient to start the identity service.

Apply [simrecrut-menu.sql](../../backend/java/src/main/resources/db/ruoyi/simrecrut-menu.sql) after the official system schema exists. Assign the new menu permissions to the intended roles in RuoYi. Menu permissions do not grant ownership of another user's resume.

## Resume business module

Authenticated routes are `/simrecrut/resumes`, `/import`, `/{id}`, `/{id}/clean`, `/{id}/save`, `/{id}/upload` and `/{id}/cleanup`. Permission checks use the official `@ss.hasPermi` service. The authenticated user ID selects a separate local directory and SQLite index. Request bodies and extracted facts are excluded from operation audit content. Up to 32 user services can be resident; restart releases this cache. This is a bounded classroom adapter, not a distributed job queue.

The team adapter reads cloud secrets from server environment variables `AIVEN_HOST`, `AIVEN_PORT`, `AIVEN_USER`, `AIVEN_PASSWORD`, `AIVEN_DATABASE` and `AIVEN_CA_FILE`. It never accepts cloud passwords from the frontend. Raw PDFs are temporary; successful confirmed JSON publication removes the original. Failed publication retains it for retry. The offline dataset watcher still uses the same Java cleaner and remains separate from web uploads. No DeepSeek request is introduced.

The legacy localhost workspace on port 8765 remains a separate development adapter, not the authenticated production frontend. The new candidate routes are `/candidate/resumes` and `/candidate/resumes/:id` in `frontend/src/views/candidate/`.

## Frontend modes and deployment

Development defaults to explicit demo mode in `.env.development`. Set `VITE_APP_DEMO_MODE=false` when testing login against the Java server; `/dev-api` is proxied to localhost:8080. Production uses `.env.production` and requires a reverse proxy for `/prod-api` to the Java backend and an SPA fallback for frontend routes. Demo privileges never create a server token.

Users and roles read the real RuoYi list endpoints in connected mode. Research experiments, reviews and model release operations remain the existing prototype: demo actions are blocked in connected mode until their business APIs are implemented. This migration does not claim those research APIs are complete.

The reference proxy is [ruoyi.conf](../../deploy/nginx/ruoyi.conf). Validation: Java 17 Maven verify passed with six tests, including user isolation; frontend production build and repository checks passed. Startup reached the official database initialization path with a deliberately unavailable local test database; a live login test requires initialized MySQL and Redis. Browser preview verification was blocked by the browser URL policy.
