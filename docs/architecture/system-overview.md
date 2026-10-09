# System overview

This document defines the intended architecture. The repository is an initial scaffold; directory names and interfaces do not establish that the workflows, database schema, or operational guarantees are implemented.

The source specification is [SimRecrut - Third Meeting Preparation](https://ijppwkmdd5yg.jp.larksuite.com/docx/UGOYdlnb5or8prxDdtrjKzyvpwd). [ADR 0001](decisions/0001-repository-and-runtime-boundaries.md) records the agreed repository adjustments, including the independent Java backend and layer-first packages. The [root README](../../README.md) is the repository entry point. Maintain architecture details here rather than duplicating them in component READMEs.

## Components and runtime boundaries

| Source component | Responsibility | Runtime boundary |
| --- | --- | --- |
| `frontend/` | Vue 3 pages, permitted user actions, request handling, and progress polling | Browser application served through Nginx |
| `backend/java/` | JDK 17 Spring Boot API, authentication, authorization, business rules, persistence, and background work | One Java business service |
| `backend/inference/` | FastAPI input validation, feature encoding, model loading, and prediction | Separate internal Python process/container |
| `offline-ml/` | Data preparation, training, evaluation, and release-package export | Independent offline commands; excluded from the default online deployment |

The three source components are frontend, backend, and offline ML. The backend contains two runtimes with separate dependencies and health checks. Cloud MySQL is external infrastructure. Redis supports temporary runtime data; MySQL stores durable business state. Restricted storage holds temporary case files and versioned model artifacts.

```text
Browser -> Nginx -> Java API -> cloud MySQL
                         |--> Redis
                         |--> internal FastAPI -> read-only model packages
                         |--> PDFBox / Tesseract / DeepSeek
                         `--> restricted case workspace

Offline ML -> versioned model package -> validated registry import -> inference
```

Java owns application-database writes. The inference service receives neither database credentials nor public browser traffic. Root `.env` contains local configuration; `.env.example` documents placeholders. Startup and deployment inject only the configuration each runtime needs. Frontend configuration contains public values only.

## Java package responsibilities

Packages under `fr.isep.simrecrut` are organized by layer, then by business area such as `job`, `assessment`, `research`, or `review`.

| Layer | Responsibility |
| --- | --- |
| `controller` and `dto` | HTTP binding, basic validation, and explicit permitted request/response fields |
| `application` | Coordinate multi-step use cases, external work, and task dispatch |
| `service` and selected `service/impl` | Enforce state/access/reference rules and own short persistence transactions |
| `domain` | Pure matching, Objective Rule, weight, and state calculations |
| `mapper` and `entity` | Parameterized SQL and database rows; XML mirrors Mapper business grouping under resources |
| `gateway` and `integration` | External-capability interfaces and their HTTP/process/library implementations |
| `worker` | Background execution, heartbeat, recovery, and cleanup entry points |
| `security`, `config`, and `common` | Current identity, access policy, typed configuration, and small shared infrastructure |

Complex workflows follow `Controller -> Application -> Service -> Mapper -> MySQL`. Simple reads may use `Controller -> concrete query Service -> Mapper`. Domain calculations and gateways are side calls, not additional sequential persistence layers. Controllers do not access mappers, entities, model HTTP clients, or files directly.

Core business interfaces retain meaningful implementation boundaries. Application coordinators, validators, query helpers, and pure calculations can be concrete classes. MyBatis creates Mapper implementations: do not add handwritten `MapperImpl` classes or a duplicate generic repository layer. DTOs are interface contracts, not copies created for every internal layer or table.

## Workflow guarantees to implement

- Derive the acting user from the authenticated context. Check ownership, grants, resource state, and privacy before reads or writes. Candidate, HR, Reviewer, and Admin responses use explicit projections. Reviewer is an assigned responsibility for an authorized HR or Researcher, not an independent identity role.
- Freeze published job, confirmed professional facts, protocol/schema, and selected model versions. Preserve the inputs associated with historical results.
- Validate logical references inside the owning service transaction. The planned MySQL design has no physical foreign keys; primary keys, unique constraints, checks, indexes, parent locking, and explicit cleanup remain required.
- Keep database transactions short. OCR, extraction HTTP calls, prediction, and physical file operations execute outside them. Confirmation commits structured facts before raw-file cleanup; cleanup failures remain recoverable.
- Persist background tasks with worker identity, lease token, expiry, and heartbeat. Guard result writes with the current lease and resource state. Expired or revoked workers cannot save results.
- Scope idempotency keys to the authenticated operation and resource; compare request hashes and recheck current access/privacy before returning an earlier outcome.
- Revoke content access and writers when deletion starts. Retry cleanup while the case remains inaccessible; retain tracking until files and linked content are removed. Preserve only permitted non-content receipts.

These are implementation requirements and acceptance criteria, not guarantees supplied by empty directories.

## Prediction and model release

`contracts/` holds the versioned API, feature-schema, model-package, and reference-example specifications shared by Java, inference, and offline ML. Both online models use separately trained parameters with the specified `32 -> 16 -> 8 -> 1` network. Neutral uses the professional block plus a zero research block; Biased uses the same professional block plus the declared category encoding. Unknown attributes remain explicit; they are not inferred from a CV.

Offline ML exports weights, schema/preprocessing, thresholds, manifest, checksums, and evaluation evidence to `model_artifacts/{neutral,biased}/{version}/`. `service/modelregistry/metadata` imports and validates offline provenance and run evidence; it does not run training. Registration, approval, activation, and retirement preserve compatible historical versions.

The internal inference API returns predictions and version identifiers. Java checks request/case identity, frozen versions, finite values, privacy, and task ownership before saving a successful result. Technical failures remain task/case failures; never fabricate zero scores or Reject decisions. Public DTOs expose only the fields permitted for the caller.
