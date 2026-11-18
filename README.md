# SimRecrut

SimRecrut is a recruitment assessment system with Candidate, HR, Researcher,
Reviewer, and Admin work areas. The assessment workflow presents Objective Rule,
Neutral AI, Biased AI, Standard Human, and Instructed-Bias Human results through
authorized views. Matching is an intermediate calculation used by the rules and
models.

The Vue frontend and Java backend implement identity, structured resume review,
HR job publication, Candidate cases, objective assessment and human review.
Research experiments and several administration screens remain demonstration-only.
The offline ML area implements source-backed extraction, 39-input feature
preparation and FairCVtest-style Keras training. The independent inference service
and versioned online model packages still need implementation. See the
[current implementation status](docs/development/project-status.md).

The [original Lark specification](docs/research/lark-specification-source.md)
records the data preparation, extraction and training requirements.

Source identifiers, comments, documentation and default messages use English.
Chinese and French interface text belongs in the declared translation catalogues;
API messages and verification mail follow the requested interface language. Java
development, compilation, CI, and runtime use **JDK 17**.

The [job workflow API](docs/api/job-workflow.md) implements document-defined HR
drafts, structured requirements, weights, extraction, publication, version
history and Candidate published-job reads. It requires Flyway V6 and a configured
frozen matching rule. Connected Vue clients use these API contracts.

Local startup uses `scripts/dev/launch-local.ps1`, with `JAVA_PORT` from the root
`.env` and dependency-aware readiness checks. Run all backend checks, including
local Redis and OCR, using `scripts/dev/test-local.ps1`. The
[container deployment guide](docs/deployment/container-stack.md) describes the
frontend proxy, backend, Redis and read-only configuration/model mounts.

The backend also provides [Swagger UI and OpenAPI YAML](docs/api/api-documentation.md)
for the implemented controllers. The generated snapshot can be shared with the
frontend without changing its source files.

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
|   |-- src/simrecrut_ml/
|   |   |-- data/{methods,scripts}/
|   |   |-- extraction/{methods,scripts}/
|   |   |-- features/{methods,scripts}/
|   |   |-- models/{methods,scripts}/
|   |   |-- training/{methods,scripts}/
|   |   |-- evaluation/{methods,scripts}/
|   |   `-- packaging/{methods,scripts}/
|   |-- datasets/{raw,clean,manifests}/
|   |-- runs/
|   |-- reports/{validation,final-test}/
|   `-- tests/{data,extraction,features,models,training,evaluation,packaging}/
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

The initial 36-table business migration is available for review and controlled
installation. See [initial schema](docs/database/initial-schema.md) for its
scope, identity boundary, remaining business rules and execution checks.
See [schema version 8](docs/database/schema-version-8.md) for fresh installation
and upgrade checks.

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
Each source module has `methods/` for reusable functions and `scripts/` for
operations with explicit paths and settings. Extraction keeps its two method
files and two operation scripts. The original experiment selected 500 Djinni
profiles and 60 jobs. The current revision uses the same 300 training and
100 validation people in `datasets/clean/training-500-multimodal-v3/`; the
100 test people remain sealed in the original batch. Archived batches stay
unchanged.

Each development person needs at least two complete job pairs, with three
preferred. Missing mandatory evidence holds a pair; it is never filled in. Job quotes, mapping proposals, replies and rejected
cases stay with the local batch. Explicit prose targets take priority over
publisher summary tags. Education and project criteria are included when their
source evidence supports an assessment.

Split people 300/100/100 before making variants. Portrait identities and assigned
full names also stay within one split. The final test people are excluded from
training, preprocessing fits, cutoff selection and validation reports.
Synthetic portraits and names describe controlled cases, not the real candidates.
The 39-position schema contains 16 professional and 23 research values. Five
research families cover school tier, referral, gender, ethnicity and name cues.
The rule cutoff remains the uncalibrated research setting 0.70; each network's
decision cutoff is selected separately on validation data. A fixed fictional
bias table is saved in the batch manifest; its effects are not claims about real
groups or hiring decisions.

The Keras adaptation of FairCVtest combines 39 structured inputs, 20 projected
face values and 64 biography values, followed by layers of 40, 20 and 1 units.
ResNet-50 portrait features are cached once; its weights and fastText stay frozen.
Biased trains the face projector, BiLSTM and scoring layers. Neutral masks the
research block and both media branches inside its saved model. Its training view
shares the same scoring layers and skips computations whose outputs are zero.
Missing biographies use reserved token ID 2; missing face vectors contain zeros.
Their branch outputs are masked inside the saved models. Whole-branch dropout
during Biased training also covers cases with missing text or photos.

The training operation first fits the structured inputs, then tunes Biased with
text and portraits. Controlled pairs include ordinary cases and extra cases near
the rule boundary, made by changing HR weights without changing CV facts. Pair
loss penalizes missed or incorrect decision changes. Both conditions run with
five seeds. Each run saves epoch logs, best weights, complete models, cutoffs
and validation predictions for all four media combinations. Checkpoint selection
uses validation class performance, name/gender contrasts and raw hard-check
errors. The mandatory gate is part of each saved model; raw head scores and
error counts are retained. Training uses float32 with TF32 disabled. These
results measure learning of the declared research rules, not real hiring accuracy.
Education and project coverage remains limited and is recorded in the cohort
audit; a successful simulation does not resolve that data limitation.
Use the existing operation scripts; paths and run settings are at their top.
For GPU training, use WSL and the `training-gpu` dependency group with the same
Keras script. Data, pretrained assets, run outputs and `.env` remain outside Git.

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
is ignored by Git. Native Spring Boot does not automatically read this file;
configure backend variables in the IDE or shell environment. Future Compose configuration
must inject only each service's required variables. Frontend configuration must
contain public values only.

```powershell
Copy-Item .env.example .env
$env:JAVA_HOME = 'D:/Java/jdk-17'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
.\backend\java\mvnw.cmd -f backend/java/pom.xml --batch-mode verify
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
