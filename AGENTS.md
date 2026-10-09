# Repository instructions

Read [README.md](README.md), the [documentation index](docs/README.md), and the
applicable development guides before changing this repository.

- Use English for every project-owned file, identifier, comment, example, UI
  string, log, error message, documentation page, and commit message.
- Use JDK 17 for Java development, compilation, verification, and runtime. Do not
  use syntax or APIs introduced after Java 17.
- Keep human-readable documentation under `docs/`. Update `docs/README.md` when
  adding a guide. Root README is the entry point, not a second specification.
- Follow [coding standards](docs/development/coding-standards.md) and the
  [documentation policy](docs/development/documentation-policy.md). Comments
  explain contracts and non-obvious intent; do not create boilerplate comments.
- Preserve layer-first Java packages, explicit authorized response DTOs, short
  transactions, pure domain calculations, and Gateway/Integration boundaries.
- Keep online inference in `backend/inference/` and training in `offline-ml/`.
  Do not give inference application database credentials or a training endpoint.
- Keep secrets in the root local `.env`, with placeholders in `.env.example`.
  Inject only each service's required variables. Never expose secrets to Vite.
- Run `python scripts/maintenance/check_repository.py` after repository changes
  and the relevant component checks after implementation changes.
