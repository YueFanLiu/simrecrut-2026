# Documentation index

This directory contains the authoritative human-readable project documentation.
The root [README](../README.md) summarizes the repository and links here.
Machine-readable API and feature contracts belong in `contracts/`; SQL migrations
belong in the Java resources directory. Keep each topic in one authoritative page.

## Architecture

- [System overview](architecture/system-overview.md): components, layers, data
  ownership, tasks, and model releases.
- [ADR 0001](architecture/decisions/0001-repository-and-runtime-boundaries.md):
  repository organization, runtime boundaries, and JDK 17.

## Development

- [Coding standards](development/coding-standards.md): English-only content,
  formatting, contracts, and comments.
- [Documentation policy](development/documentation-policy.md): locations,
  naming, decisions, sources, and maintenance.
- [Local setup](development/local-setup.md): authoritative environment and
  configuration instructions.
- [Java setup](development/java-setup.md): Java 17 build and runtime prerequisites.
- [Repository checks](development/repository-checks.md): automated scaffold,
  language, and documentation checks.

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

- [RuoYi migration and setup](development/ruoyi-migration.md)
