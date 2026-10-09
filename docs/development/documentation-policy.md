# Documentation Policy

Human-readable project documentation lives under `docs/`. The root `README.md` is the entry point;
`docs/README.md` is the central index. This location policy is specific to SimRecrut.

## Where information belongs

| Location | Responsibility |
| --- | --- |
| `README.md` | Project purpose, source layout, current implementation status, and links to setup and detailed guides. |
| `docs/README.md` | Index of maintained documents and their audience. |
| `docs/architecture/` | Component responsibilities, flows, boundaries, and architecture decisions. |
| `docs/api/` | Human-readable API behavior, authorization, errors, and integration guidance. |
| `docs/database/` | Logical relationships, table rationale, retention, and migration guidance. |
| `docs/research/` | Experiment protocols, evaluation methodology, and interpretation of model evidence. |
| `docs/development/` | Setup, coding standards, contribution workflow, and this documentation policy. |
| `docs/deployment/` | Deployment, health checks, model activation, recovery, and operational procedures. |
| `contracts/` | Machine-readable API specifications, feature schemas, model package schemas, and sanitized reference cases. |
| `backend/java/src/main/resources/db/migration/` | Executable Flyway migrations. |
| `.env.example` | Supported environment variable names, safe placeholders, and concise variable comments. |

Do not scatter module READMEs, setup guides, or meeting notes throughout source directories. Add
module-specific guidance to the appropriate central document and link to its source files. Build
files, SQL migrations, executable configuration, and concise source comments stay with their code.
Add a root metadata file only when a tool or repository convention requires that location; link it
to the central documentation instead of duplicating policies.

## One authoritative explanation

[Local Setup](local-setup.md) owns environment loading, local prerequisites, and local startup
instructions. `.env.example` owns the variable inventory and placeholders. The root README links to
setup; deployment guides explain production-specific behavior and reference the same variable
inventory. Do not maintain competing configuration tables in multiple guides. `.env` contains local
values and is never committed.

Machine-readable contracts are authoritative for wire formats and feature schemas. Documentation
explains their meaning and links to them rather than reproducing long definitions. A database guide
explains migration rationale; executable migrations define the implemented schema. Clearly label
planned behavior, implemented behavior, and known limitations. A placeholder is not an implemented
endpoint or a verified deployment.

## Writing and naming

- Write all project content in English, including headings, diagrams, examples, and filenames. Do
  not commit Chinese text or untranslated copies of external project documents.
- Use descriptive lowercase kebab-case Markdown filenames, such as `model-activation.md`. Reserve
  `README.md` for the root entry point and the central docs index.
- Use a single top-level heading, short sections, active verbs, fenced code blocks with language
  identifiers, and tables only when they clarify a comparison or mapping.
- Write procedures in execution order. State prerequisites, working directory, command, and
  expected result. Use safe placeholders and sanitized examples.
- Link relatively to repository files. Keep external links pointed at the specific primary source
  that supports a claim; include the source revision or access date when behavior is version-sensitive.
- Keep documentation in UTF-8 without a BOM and use LF line endings. Keep text searchable; a diagram
  should also have a concise textual explanation.

The guidance on clear terminology, structure, and code formatting is informed by the
[Kubernetes documentation style guide](https://kubernetes.io/docs/contribute/style/style-guide/).
The directory layout, English-only requirement, and single-authority rules above are SimRecrut
decisions, not Kubernetes requirements.

## Architecture decisions

Record consequential choices in `docs/architecture/decisions/NNNN-short-title.md`, using sequential
four-digit numbers. Each Architecture Decision Record (ADR) contains a title, status, date, context,
decision, consequences, and links to relevant contracts or source files. Use statuses such as
`Proposed`, `Accepted`, or `Superseded` and record the actual decision state.

Add a new ADR when replacing an accepted decision. Mark the old one as superseded and link both
records. Do not silently rewrite the rationale of an earlier accepted decision. Fix factual errors
and broken links in place. Add new ADRs to `docs/README.md`.

## Changes and verification

Update affected documentation, contracts, sanitized examples, and index entries in the same change
as the implementation. Move a document instead of leaving several competing copies, and repair
incoming links when moving or renaming it. Keep source rationale in comments and general operating
instructions in the central guides.

Before submitting a change, verify that local links resolve, commands match the supported tooling,
JDK requirements remain at 17, examples contain no secrets or personal data, and implementation
status is accurate. Run the repository checks listed in [Local Setup](local-setup.md). Automated
checks cover only their implemented scope; technical accuracy, clarity, and comment quality still
require review.
