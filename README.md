# SimRecrut

SimRecrut is a recruitment assessment system with Candidate, HR, Researcher,
Reviewer, and Admin work areas. The assessment workflow presents Objective Rule,
Neutral AI, Biased AI, Standard Human, and Instructed-Bias Human results through
authorized views. Matching is an intermediate calculation used by the rules and
models.

This repository provides the project layout, development rules, configuration
examples and a Java build setup. Offline tools download and clean datasets,
then prepare review drafts with source evidence. DeepSeek handles structured
extraction through a replaceable provider. PyTorch tools encode features,
split data by person and run development training on prepared numeric inputs.
Actual data still requires human review before training.

Product workflows, frontend pages, inference endpoints, final ML evaluation
and packaging, database migrations and deployment are developed in their
designated modules.

See [Dataset preparation](docs/research/dataset-preparation.md) to download the
sources, create local review drafts, and check the required training protocol.
See [Pilot review and PyTorch preparation](docs/research/pilot-review-and-training.md)
for the inspected 20-CV/20-job pilot, local mapping questions and remaining
real-data training prerequisites.

All project-owned content must be in English, including documentation, comments,
identifiers, interface text, logs, errors, examples, and commit messages. Java
development, compilation, CI, and runtime use **JDK 17**.

## Repository structure

| Source component | Location | Responsibility |
| --- | --- | --- |
| Frontend | `frontend/` | Vue 3 pages, navigation, forms, authorized results, and public API calls. |
| Backend | `backend/java/`, `backend/inference/` | Spring Boot business operations and a separate FastAPI inference process. |
| Offline ML | `offline-ml/` | Research data preparation, training, evaluation, and model packaging. |

The cloud MySQL database is an external dependency. Redis supports sessions and
caches. Durable case, task, result, and deletion state belongs in MySQL.

```text
.
|-- frontend/
|   |-- public/
|   `-- src/
|       |-- api/simrecrut/
|       |-- assets/
|       |-- components/simrecrut/
|       |-- composables/
|       |-- layouts/
|       |-- router/modules/
|       |-- store/modules/
|       |-- utils/
|       `-- views/
|           |-- auth/
|           |-- candidate/{jobs,cases}/
|           |-- hr/{jobs,cases}/
|           |-- research/{experiments,cases,models,metrics}/
|           |-- reviewer/tasks/
|           `-- admin/{users,roles,models,protocols,system-health,failures,audit}/
|-- backend/
|   |-- java/
|   |   |-- pom.xml
|   |   |-- mvnw
|   |   |-- mvnw.cmd
|   |   `-- src/
|   |       |-- main/java/fr/isep/simrecrut/
|   |       |-- main/resources/{mapper,db/migration}/
|   |       `-- test/{java/fr/isep/simrecrut,resources}/
|   `-- inference/
|       |-- app/{api,schemas,features,models,loaders,services,core}/
|       `-- tests/{contracts,features,prediction}/
|-- offline-ml/
|   |-- configs/
|   |-- src/simrecrut_ml/{data,extraction,features,models,training,evaluation,packaging}/
|   |-- datasets/{raw,clean,manifests}/
|   |-- runs/
|   |-- reports/{validation,final-test}/
|   `-- tests/{data,extraction,features,training,evaluation}/
|-- contracts/
|   |-- api/
|   |-- fact-schemas/
|   |-- feature-schemas/
|   |-- ml/
|   |-- schemas/{professional-features,research-attributes,model-package}/
|   `-- examples/golden_cases/
|-- model_artifacts/{neutral,biased}/
|-- runtime/cases/
|-- deploy/nginx/
|-- scripts/{dev,release,maintenance}/
|-- docs/
|   |-- README.md
|   |-- architecture/decisions/
|   |-- api/
|   |-- database/
|   |-- research/
|   |-- development/
|   `-- deployment/
|-- .github/workflows/
|-- .env.example
|-- .gitignore
|-- .editorconfig
`-- README.md
```

Brace notation groups sibling directories. Empty source directories are retained
with `.gitkeep` files; they are not implementations. Compose files, Dockerfiles,
component dependency manifests, and public/internal API contracts are added with
the corresponding working component. They must not imply an operational stack
before that stack exists.

The planned frontend feature clients are `candidateJobs.js`, `candidateCases.js`,
`hrJobs.js`, `hrCases.js`, `researchExperiments.js`, `reviewerTasks.js`, and
`admin.js` under `src/api/simrecrut/`. Shared UI components are `CvUploadCard.vue`,
`CaseProgressStepper.vue`, `EvidencePanel.vue`, and `FiveResultsPanel.vue` under
`src/components/simrecrut/`. HR job requirements and weights share `wizard.vue`;
case polling belongs in `composables/useCasePolling.js`.

## Java layers

Java packages are organized by layer first and business responsibility second:

```text
fr/isep/simrecrut/
|-- SimRecrutApplication.java
|-- controller/{identity,candidate,job,assessment,research,review,admin}/
|-- dto/{identity,candidate,job,assessment,research,review,admin}/
|-- application/{candidate,job,assessment,casefile,research,review,modelregistry,privacy,audit}/
|-- service/
|   |-- identity/
|   |-- job/
|   |-- assessment/{casecore,result}/
|   |-- casefile/
|   |-- research/{dataset,experiment}/
|   |-- review/
|   |-- modelregistry/metadata/
|   |-- protocol/
|   |-- async/
|   |-- idempotency/
|   |-- privacy/
|   |-- audit/
|   `-- impl/{job,assessment,research,review,modelregistry,protocol,async,idempotency,privacy}/
|-- domain/{job,assessment,research}/
|-- mapper/{identity,job,assessment,research,review,modelregistry,protocol,async,idempotency,privacy,audit}/
|-- entity/{identity,job,assessment,research,review,modelregistry,protocol,async,idempotency,privacy,audit}/
|-- gateway/{ml,extraction,ocr}/
|-- integration/{ml,pdf,ocr,deepseek}/
|-- worker/{assessment,extraction,privacy,recovery}/
|-- security/{authentication,authorization,currentuser}/
|-- config/properties/
`-- common/{api,error,identifiers}/
```

| Layer | Responsibility |
| --- | --- |
| Controller | Bind HTTP input, use the authenticated user, and return permitted DTOs. |
| DTO | Describe allowed request and response fields; separate role-specific projections. |
| Application | Coordinate multi-step actions, idempotency, capabilities, and task dispatch. |
| Service | Enforce business state, references, access, and short database transactions. |
| Domain | Calculate Matching and Objective results and enforce pure business rules. |
| Mapper / Entity | Execute parameterized MyBatis SQL and represent database rows. |
| Gateway / Integration | Define external capabilities and implement ML, PDF, OCR, and DeepSeek calls. |
| Worker | Execute durable tasks, maintain leases, recover work, and perform cleanup. |
| Security / Config | Resolve identity and resource access and configure Spring infrastructure. |

Simple queries can use `Controller -> Query Service -> Mapper`. Coordinated
workflows use `Controller -> Application -> Business Service -> Mapper`.
Domain calculations and gateways are side calls. Network calls and OCR run
outside database transactions. Core capability interfaces have implementations
under `service/impl/`; focused helpers remain concrete classes.

Candidate-facing controllers and application services reuse `assessment` case
state, snapshots, grants, and results. A `candidate` package is not duplicated in
every layer. MyBatis creates Mapper implementations; do not add `MapperImpl` or a
redundant repository wrapper. Mapper XML uses the same business grouping under
`src/main/resources/mapper/`.

## Online inference and offline ML

`backend/inference/` is reserved for a separate Python service on the internal
network. The planned service will validate versioned requests, encode features,
load approved model packages and calculate predictions. Java will check returned
identities, versions and numbers before saving results. Inference must receive
no application database credentials and expose no training endpoint.

`offline-ml/` owns source manifests, data cleaning, pairing, grouped splits,
training, validation, held-out evaluation, and model export. Live Candidate
uploads do not automatically become training samples.
Data preparation, extraction and a development training runner are implemented.
Reviewed pairing, label calibration, final evaluation and release packaging
remain unfinished.

Shared contracts belong in `contracts/`. Planned model releases will use
`model_artifacts/{neutral,biased}/{version}/` with weights, schema, preprocessing,
thresholds, manifest, checksums, and evaluation evidence. The planned Java model
registry will record imported provenance and release decisions. Online workers
will perform inference only.

## Documentation and contribution rules

Start with the [documentation index](docs/README.md). Human-readable project
guides and specifications live under `docs/`; this README provides the repository
entry point. Keep one authoritative page for each topic and update it with the
implementation. Do not scatter module-level README files or temporary design
notes across source directories.

- [Documentation policy](docs/development/documentation-policy.md)
- [Coding and commenting standards](docs/development/coding-standards.md)
- [System architecture](docs/architecture/system-overview.md)
- [Repository and runtime decision](docs/architecture/decisions/0001-repository-and-runtime-boundaries.md)
- [Local configuration](docs/development/local-setup.md)
- [Java 17 setup](docs/development/java-setup.md)
- [Scaffold verification](docs/development/repository-checks.md)

Comments explain intent, contracts, invariants, transaction boundaries, and
non-obvious decisions. Use Javadoc for Java contracts, docstrings for Python
contracts, and JSDoc where JavaScript callers need clarification. Keep comments
in English, current, and proportional to the code.

## Local configuration and verification

Copy `.env.example` to the root `.env` and fill in local values. The local `.env`
is ignored by Git. Native Spring Boot does not automatically read this file; the
development helper loads it before invoking Java. Future Compose configuration
must inject only each service's required variables. Frontend configuration must
contain public values only.

```powershell
Copy-Item .env.example .env
# Set JAVA_HOME in .env to the local JDK 17 directory, then fill in dependencies.
.\scripts\dev\java.ps1 -Action verify
python scripts/maintenance/check_repository.py
```

Compilation can be checked before cloud database access is available. Starting
the backend requires database configuration and the database migrations for the
implemented feature set; end-to-end startup is not yet available at this stage.

The repository check verifies English-only project files, local documentation
links, and required scaffold directories. CI also verifies the Java build on
JDK 17. Actual credentials, CV files, research data, run outputs, and model weights
are excluded from Git.

Version control uses [YueFanLiu/simrecrut-2026](https://github.com/YueFanLiu/simrecrut-2026).
