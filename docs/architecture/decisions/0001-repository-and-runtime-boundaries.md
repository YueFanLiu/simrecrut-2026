# ADR 0001: Repository and runtime boundaries

- Status: Superseded for identity by the 2026-10-10 RuoYi decision
- Current identity decision: [RuoYi migration](../../development/ruoyi-migration.md)
- Date: 2026-10-09
- Scope: Initial repository scaffold and intended architecture

## Context

The [Lark architecture specification](https://ijppwkmdd5yg.jp.larksuite.com/docx/UGOYdlnb5or8prxDdtrjKzyvpwd) separates frontend, Java business logic, storage, online prediction, and offline model development. The repository needs three clear source components while retaining these logical responsibilities. The backend must be independent of RuoYi, use JDK 17, and keep online prediction separate from offline training.

## Decision

1. Use `frontend/`, `backend/`, and `offline-ml/` as the three source components. Place Spring Boot in `backend/java/` and FastAPI in `backend/inference/`. They run as distinct processes/containers with separate dependencies and health checks.
2. Use an independent Spring Boot, Spring Security, MyBatis, and Flyway backend. Provide standalone identity management rather than assuming RuoYi backend tables or services.
3. Organize Java packages by layer first, then business area. Retain core service interfaces and implementations, but use concrete helpers where an interface would only forward calls. Add application coordination only when a use case needs it. MyBatis supplies Mapper implementations; no duplicate repository wrapper is introduced.
4. Keep online ML limited to inference. Java owns application-database writes and validates results before persistence. Python receives no database credentials. Import offline provenance under `modelregistry/metadata`; no online training module is created.
5. Treat cloud MySQL as external infrastructure. Keep Redis and restricted case/model storage explicit deployment dependencies. Put local secrets in the ignored root `.env` and document placeholders in `.env.example`; inject configuration per runtime.
6. Centralize maintained project documentation under `docs/`, with the root README as the navigation and startup entry point. Keep shared specifications under `contracts/`; keep versioned model outputs and private runtime data outside Git. All maintained project text, code comments, identifiers, and user-facing strings use English.

## Consequences

Source grouping does not collapse Java and Python into one runtime. Deployments must configure internal routing, authentication, readiness, timeouts, and recovery between them. Offline training is excluded from the default online deployment.

Layer-first organization makes responsibilities visible without requiring a class in every business/layer combination. Candidate routes coordinate shared assessment capabilities rather than duplicating assessment persistence. DTO projections, reference validation, durable task leases, privacy, idempotency, and short transactions remain required even when a query has a shorter call chain.

Shared schema versions and package manifests prevent online/offline feature drift. Future changes to these boundaries require another ADR. See the [system overview](../system-overview.md) for the intended workflow and implementation requirements. Acceptance of this decision does not imply that those workflows are already implemented.
