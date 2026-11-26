# Container stack

Run commands from the repository root. Docker Engine and Docker Compose must be
available. The external MySQL database and SMTP/extraction providers require
their own configured credentials and network access.

1. Copy `.env.example` to the ignored root `.env` and supply the real configuration.
2. Verify the database migration policy and permissions before starting Java.
   Startup can apply Flyway migrations; use a disposable database for acceptance.
3. Provide the frozen matching-rule and feature-schema files referenced by the
   database under `model_artifacts/rules` and `model_artifacts/assessment`.
   Their paths and SHA-256 values must match the stored version metadata.
4. For full assessment, provide compatible registered Neutral and Biased model
   packages and an externally reachable inference service using `ML_BASE_URL`,
   `ML_INTERNAL_TOKEN`, `ML_NEUTRAL_PATH` and `ML_BIASED_PATH`. A loopback address
   inside the backend container refers to that container, not the host.
   This repository does not yet supply the inference runtime or trained packages.
5. `CASE_OBJECTIVE_ONLY=true` explicitly selects the implemented objective-only
   workflow. It still requires the frozen rule/protocol/schema. AI slots remain
   skipped and must not be presented as trained-model results.

```powershell
docker compose --env-file .env -f deploy/compose.yaml config --quiet
docker compose --env-file .env -f deploy/compose.yaml up --build -d
```

The frontend is served at `http://127.0.0.1:8088/login`. Its Nginx configuration
maps `/prod-api/api/v1/...` to Java `/api/v1/...`, retains Accept-Language and
supports SPA navigation and PDF uploads. Container backend and Redis ports are
internal. The supplied host binding is loopback-only; public hosting requires a
reviewed TLS endpoint and appropriate trusted proxy/network configuration.

The backend uses port 10000 inside the stack regardless of the local `JAVA_PORT`.
Redis readiness precedes Java; Java's `/actuator/health/readiness` must report UP
before the frontend starts. Readiness includes MySQL and Redis. Missing model
configuration still blocks the relevant business operations explicitly.

`model_artifacts` is bind-mounted read-only. Persisted case/resume workspace data
uses the `backend-runtime` volume, and Redis uses `redis-data`. Redis is accessible
only on the Compose network, has no host port and is configured without a password
in this local stack. Review access controls when adapting it to shared hosting.
Never put credentials in frontend build arguments or publish configuration output
that includes the backend environment. Root and backend Docker ignore files exclude
private environment files, dependency caches and runtime data from image contexts.

The standalone Java image keeps its `backend/java` build context. Mount the same
configuration/model tree at `/app/model_artifacts` when running it separately.
Creating an empty artifact directory does not install the required configuration.

Validate image builds, browser calls, SMTP delivery and database-backed flows in
the target environment. A passing Compose syntax check does not verify those
external dependencies. Keep deletion/retention tasks and workspace backups in the
deployment acceptance plan; do not remove persisted volumes as a routine restart.
