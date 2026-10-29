# Generate and share backend API documentation

The backend serves Swagger UI and generates OpenAPI 3 JSON/YAML from the active
Controller mappings and DTOs. Implemented routes cover authentication, account
and role management, the Candidate resume library, HR/Candidate jobs, Candidate
cases, authorized HR case results and assigned Human reviews. Research coverage
currently includes eligible-reviewer search and primary case review assignment;
experiment APIs are not implemented by this module. No files under `frontend/`
are needed or changed.

## Installed components

`springdoc-openapi-starter-webmvc-ui` 2.8.17 is configured in the backend POM
for this Spring Boot 3.5/Java 17 application. It includes Swagger UI and YAML
generation; there is no separate YAML program to install. Reload the Maven
project in the IDE after pulling this change.

[ApiDocumentationConfig](../../backend/java/src/main/java/fr/isep/simrecrut/config/ApiDocumentationConfig.java)
sets the title, version, tags, Bearer scheme, operation descriptions, success
statuses and error envelopes. It supplements dynamic JsonNode/Map responses
using [documentation models](../../backend/java/src/main/java/fr/isep/simrecrut/config/ApiDocumentationModels.java).
These records describe existing wire fields; they do not change business
serialization, ownership checks or database behavior.

The generated [OpenAPI YAML snapshot](../../contracts/api/openapi.yaml) covers
the current implemented controllers. It was exported using the isolated
documentation test, not by calling live business operations. Existing
[job-only JSON](../../contracts/api/jobs.openapi.json) is a separately maintained
contract; springdoc does not import or automatically rewrite that file.
Use the generated YAML as the combined frontend handoff document. Reconcile
the older job-only file when changing that module's contract.

## Open Swagger UI

1. Reload Maven dependencies in the IDE. Keep JDK 17 selected.
2. Start `fr.isep.simrecrut.SimRecrutApplication` with the existing application
   environment. Use the normal backend profile, not `resume-workspace`.
3. Open `http://localhost:8081/swagger-ui.html`. If `JAVA_PORT` differs, use that
   port. The page redirects to `/swagger-ui/index.html`.
4. The all definition is selected by default and shows every implemented API.
   Module sections are expanded so the colored GET/POST/PUT/DELETE operation
   rows are visible immediately; individual operation details remain collapsed.
   The Schemas section starts collapsed. Select authentication, administration,
   resumes or jobs in the dropdown to focus on one module. Expand an operation
   to read its request, response and errors.

Normal application startup still needs its existing database/session settings
and may apply pending Flyway migrations. Documentation does not itself verify
live MySQL, mail, extraction or publication. Job creation requires the frozen
matching rule configuration described in [job workflow](job-workflow.md).

| URL on the backend | Content |
| --- | --- |
| `/swagger-ui.html` | Interactive documentation page |
| `/v3/api-docs` | Combined generated OpenAPI JSON |
| `/v3/api-docs.yaml` | Combined generated OpenAPI YAML |
| `/v3/api-docs/jobs` | Job-only JSON |
| `/v3/api-docs.yaml/jobs` | Job-only YAML |

Replace `jobs` with `authentication`, `administration` or `resumes` for the other
groups. Documentation GET routes are readable without a token. Business routes
retain their authentication, role, action and ownership checks.

## Test an authenticated operation

1. Open the authentication definition and expand `POST /api/v1/auth/login`.
2. Click Try it out, enter your real `identifier` and `password`, then Execute.
3. Copy only `data.accessToken` from the successful response.
4. Click Authorize and paste the token without adding a `Bearer ` prefix.
5. Select the relevant definition and authorize there if necessary. Choose an
   operation allowed by that account's roles and permissions.

The UI sends `Authorization: Bearer <token>`. Tokens are not configured for
persistent storage. After logout, expiry or a page refresh, sign in/authorize
again as needed. Login returns the token; the page does not automatically copy
it into Authorize.

Writes require the documented `Idempotency-Key`. Use a fresh UUID for a new
action and preserve the same key and body for an unchanged retry. Send the
latest `revision` for job writes or `expectedRevision` for resume edits. The
POST validate operation is read-only. HTTP 202 is pending processing, not
completion; follow the documented polling/read operation. Try it out sends
real requests, so using a dedicated test account makes results easier to track.

## Export YAML for the frontend

With the backend running, execute from the repository root:

```powershell
.\scripts\dev\export-openapi.ps1
```

This updates `contracts/api/openapi.yaml` using the current running backend.
To choose a backend or a single group:

```powershell
.\scripts\dev\export-openapi.ps1 -BaseUrl 'http://localhost:8081' -Group jobs
```

The group example writes `contracts/api/openapi-jobs.yaml`. Use `-OutputPath`
to choose another output file. The script downloads documentation only.
It does not start the backend or invoke any write operation.

The checked-in combined YAML can already be read/imported without starting
Java, MySQL or Redis. To regenerate from code without those services, run the
isolated documentation test with an explicit export destination:

```powershell
.\backend\java\mvnw.cmd -f backend/java/pom.xml test `
  '-Dtest=ApiDocumentationTest' `
  "-Dsimrecrut.docs.export=$PWD/contracts/api/openapi.yaml"
```

Alternatively run `ApiDocumentationTest` from the IDE with VM option
`-Dsimrecrut.docs.export=<absolute-path-to-contracts/api/openapi.yaml>`.
The test loads the actual controllers but substitutes their business services,
disables database/mail/Redis auto-configuration and makes no business calls.
It checks generation, security boundaries, schemas and Swagger assets only.

## Share and maintain

Give the frontend either the YAML file or the Swagger UI URL on a reachable
deployed backend. `localhost` on your machine is not a teammate's server address.
The generated server URL is `/`, so an online Swagger UI targets its own backend;
an imported YAML file needs the target backend base URL configured in that tool.
Publishing a separate static Swagger UI can use the same exported YAML later.

After changing an interface:

1. Update its actual Controller/DTO and business behavior.
2. Add `@Operation`, `@Schema` or documentation-model/customizer details for
   meaning, examples and dynamic response fields. Existing routes are described
   centrally; new controllers are scanned automatically under the controller
   package. Group path filters are configured in `application.yml`.
3. Restart the backend or regenerate with the isolated test.
4. Export/review the YAML and share the updated version with the frontend.

When adding a new controller, also import it and mock its services in
`ApiDocumentationTest` if using isolated generation. A live-backend export
automatically includes all active controllers matched by the configured scans.

Do not edit generated YAML as the only source of a change: regeneration would
overwrite it. Future routes can be planned separately, but a documented route
is not proof of successful live integration. The document release starts at
`0.1.0`; `/api/v1` remains the independent HTTP compatibility prefix.

Set `API_DOCS_ENABLED=false` in the backend environment and restart to disable
the generated spec and Swagger UI. Business APIs are unaffected. All project
documentation and identifiers remain English.

Reference: [springdoc v2 documentation](https://springdoc.org/v2/).
