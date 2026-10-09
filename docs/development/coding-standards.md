# Coding Standards

These are SimRecrut project rules. They adapt selected practices from established open-source
projects; they do not adopt any upstream guide wholesale. The sources and deliberate differences
are listed below.

## Language and files

- Use English for identifiers, comments, docstrings, UI text, logs, error messages, documentation,
  filenames, configuration descriptions, and checked-in examples. Do not add Chinese characters to
  project files or paths. Translate imported prose before committing it.
- Use ASCII identifiers, descriptive English names, UTF-8 without a BOM, LF line endings, a final
  newline, and no trailing whitespace.
- Use four spaces for Java, Python, and PowerShell; two for JavaScript, Vue, JSON, and YAML. Follow
  `.editorconfig` when editing files.
- Keep Java lines within 120 characters and JavaScript lines within 100 where practical. Python
  code may use up to 99 characters; wrap Python comments and docstrings at about 72. Long URLs and
  data literals may remain intact when wrapping would damage them.

## Java compatibility and boundaries

- Build, test, and run the backend with **JDK 17**. Keep the compiler release at 17. Do not introduce
  later Java syntax, APIs, preview features, or dependencies that require a newer runtime.
- Use lowercase Java package names, `UpperCamelCase` types, `lowerCamelCase` members, and
  `UPPER_SNAKE_CASE` constants. Java records are available for appropriate immutable DTOs.
- Keep controllers responsible for HTTP concerns. Put access checks and business operations in
  their assigned services; domain calculations must not call databases or external services.
- Keep request DTOs, role-specific response DTOs, and database entities distinct. Derive ownership
  from the authenticated principal, never an editable request field.
- Use MyBatis mapper interfaces with their SQL mappings. Do not write mapper implementations or
  add repository wrappers that only forward calls.
- Keep transactions short. OCR, extraction, and model HTTP calls must run outside database
  transactions. Preserve task lease checks, version checks, and privacy checks when saving results.
- Add an interface when it defines a core business or external-system boundary. Simple helpers and
  queries can be concrete classes. Do not create forwarding classes just to fill a layer.

## Comments that earn their place

Explain a decision, contract, invariant, or surprising constraint that names and code cannot make
clear. A reviewer asking why an operation is ordered a certain way is a useful signal that a comment
belongs there, following the approach in the
[Kubernetes coding conventions](https://www.kubernetes.dev/docs/guide/coding-convention/).

Useful subjects in this project include:

- Why a guarded update rejects an expired task lease.
- Why confirmation must commit before temporary CV files are removed.
- Why network calls happen outside a transaction.
- Why feature order, decimal rounding, or an unknown-category encoding must match a shared contract.
- Why a role-specific response omits a field.

Place the explanation beside the relevant code and link to a contract or architecture decision when
that provides the detailed rationale. Update or remove the comment in the same change as the code.
Use concise, complete English sentences. Prefer a clear name or a smaller function before adding a
long explanation. Do not narrate obvious assignments, getters, imports, or loops.

Do not add decorative comment boxes, empty comment templates, commented-out code, author/date
history banners, or copied descriptions that contradict the implementation. Git records change
history. Preserve required upstream license notices when reusing third-party code.

## Java documentation

Write Javadoc for core service and gateway contracts, public domain rules, and public methods with
non-obvious preconditions or effects. State the purpose first, then document relevant nullability,
units, ranges, side effects, exceptions, access assumptions, and transaction expectations. Use
`{@code ...}` for values and `{@link ...}` for types or methods. Use meaningful `@param`, `@return`,
and `@throws` descriptions where needed; do not fill tags with the parameter name alone.

Document a contract on its interface; implementations should explain only additional behavior.
Trivial DTO accessors, record-generated methods, and obvious private helpers do not need repetitive
Javadoc. Add `@since` only for a real versioned compatibility promise; do not invent release numbers
or add `@author` tags to every file.

These choices adapt the
[Spring Framework source style and Javadoc guidance](https://github.com/spring-projects/spring-framework/blob/main/CONTRIBUTING.md#source-code-style).
SimRecrut uses spaces rather than Spring's tabs and does not inherit its universal type-level
`@since` rule.

## Python documentation

Follow [PEP 8](https://peps.python.org/pep-0008/) naming and readability conventions: four-space
indentation, `snake_case` functions and modules, `UpperCamelCase` classes, explicit imports, and
sparingly used inline comments.

Following [PEP 257](https://peps.python.org/pep-0257/), public modules, classes, and exported functions
need docstrings. Start with a short action-oriented summary. Use triple double quotes; separate a
multiline summary from details with a blank line. Explain arguments, returns, raised errors, and
side effects when relevant instead of repeating the signature. For ML functions, document array
shape, feature order, numeric type, version expectations, and accepted value ranges.

Private helpers need documentation when their contract is not apparent. Do not add a docstring to
every constant or an empty placeholder package solely to increase documentation coverage.

## JavaScript and Vue documentation

Use `const` by default and `let` for reassignment, descriptive names, and clear component props and
events. Use JSDoc for shared utilities, composables, and exported functions when types or behavior
need explanation. Document polling stop conditions, cancellation, response shapes, or permissions
where relevant. Ordinary local functions and obvious handlers do not require JSDoc.

Use `//` or `/* ... */` for implementation rationale and reserve `/** ... */` for JSDoc. This
distinction follows the
[Google JavaScript guide](https://google.github.io/styleguide/jsguide.html#formatting-comments);
selective JSDoc coverage is a SimRecrut choice rather than Google's universal coverage rule.

## TODOs and review

Use `TODO: <issue URL> - <specific action and completion condition>.` for temporary work. The issue
must exist and explain the missing behavior. Avoid owner-only reminders or vague promises such as
"improve later". Remove the TODO when resolved. This adapts the
[Google Java TODO convention](https://google.github.io/styleguide/javaguide.html#s4.8.6.2-todo-comments).
Do not file external issues merely to decorate an unfinished skeleton.

Keep credentials, CV content, personal data, and internal model payloads out of examples and logs.
Use correlation identifiers and actionable English messages for diagnostics.

Reviewers check language, useful comments, layer boundaries, and compatibility as part of each
change. Run the repository checks documented in [Local Setup](local-setup.md). A text scan can find
forbidden characters, but it cannot establish correct English, useful comments, or correct Javadoc;
those remain review responsibilities. Only claim a formatter, linter, or documentation check is
enforced when its configuration and runnable check actually exist.
