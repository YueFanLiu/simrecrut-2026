# Initial business schema

The first Flyway migration creates the original 36 SimRecrut business tables.
It documents all 65 logical references, retains 36 primary keys, 34 additional
unique keys and 80 named checks, and declares zero SQL foreign keys. This is a
derived initial implementation, not a claim that A003 or A006 has been accepted.

## Sources and implementation choices

The supplied Third Meeting Preparation Word document, sections 5.6 through 5.9,
provides the table inventory, reference columns, nullability, service ownership,
deletion policies, unique column groups and check names. The supplied
SimRecrut Specification PDF dated 2026-10-09, pages 16 and 17, confirms the
original business scope. Those documents contain design summaries rather than
complete executable DDL. Do not describe this migration as an exact copy of an
existing upstream SQL file.

The [migration](../../backend/java/src/main/resources/db/migration/V1__create_initial_business_schema.sql)
materializes the summaries with these reviewable choices:

- Every table has a signed auto-increment BIGINT `id`, UTC millisecond
  `created_at` and `updated_at`; reference columns use signed BIGINT.
- Public identifiers use CHAR(26) with ASCII binary comparison. Version and
  operation codes also use ASCII binary comparison. SHA-256 fields use CHAR(64).
- Human-readable text uses utf8mb4. Code fields use VARCHAR(32) or VARCHAR(64),
  titles use VARCHAR(255), internal paths use VARCHAR(512), and source URLs use
  VARCHAR(2048). These lengths and several descriptive field names were selected
  for this implementation because the sources do not fully specify them.
- Matching, weights, contributions and rule scores use DECIMAL(8,7). Bias effects
  use DECIMAL(9,8). ML scores, thresholds and metrics use DOUBLE. Service
  validation must reject non-finite values and invalid business payloads.
- Documents with variable structure use JSON. JSON validity does not validate
  the professional-feature schema or authorize the content.
- Status and category domains are explicit CHECK expressions in the migration.
  Where the sources specify only a domain description, the literal codes are
  implementation choices. Review them against API contracts before first use.
  In particular, review attribute source codes, artifact types, access types,
  experiment types, human summary states, task states and deletion types.
- Every reference column has an explicit leading index or is the leading column
  of a retained unique key. The migration has 48 ordinary explicit indexes,
  including a metric lookup index. The sources mention 28 additional indexes
  but do not enumerate all original index declarations; this migration rebuilds
  index coverage rather than claiming exact preservation of that index list.

## Logical references and identity boundary

[Logical relations](logical-relations.csv) enumerates exactly 65 references:
39 BLOCK policies, 22 DELETE_CHILDREN policies and four CLEAR_REFERENCE policies.
The SQL comments repeat each relation and its owning validation service. These
are application obligations, not database-enforced parent relationships.

The baseline retains the original logical identity target `sys_user.user_id`
in documentation only. It creates no RuoYi tables and imports no RuoYi code.
No identity table is required to execute this migration because there are no
SQL foreign keys. Creating business tables therefore does not provide accounts,
password authentication, role assignment or permission enforcement.

The project has selected RuoYi identity management. The integrated account target is `sys_user.user_id`. Before business writes
are implemented, validate the RuoYi user target and align account-deletion behavior. Do not invent an account
ID in a request and treat it as an authenticated user. Referencing services must
validate the parent, access and state inside the write transaction.

## Intentionally separate work

This migration preserves the requested original baseline. It does not include
identity tables, seed users, name-study fields, task leases or the later fixes
described in Word section 5.8. Implement those as subsequent migrations.

The retained nullable composite review key does not prevent all duplicate
assignments. Primary human-review slots and active core model slots have no
conditional unique keys yet. ML result status permits FAILED in this baseline,
but scores are required: services must insert successful predictions only and
record technical failures in tasks/cases. Subject deduplication, active criteria,
published-weight totals, training membership, generated-sample identity, metric
replacement and retry-safe deletion receipts also require later changes.

Reference existence, ownership, compatible versions, frozen content, parent-lock
order, explicit child cleanup and permission-limited response fields must be
implemented by services. MySQL does not cascade deletion. The baseline alone is
not a working login or assessment application.

## Apply in the integrated RuoYi runtime

The WANG runtime disables automatic Flyway migration. The original V1 remains
unchanged as Zheng's reviewed business baseline. Do not enable automatic Flyway
or baseline an existing RuoYi schema just to suppress migration errors.

1. Inspect `SELECT VERSION(), DATABASE();` and `SHOW FULL TABLES;` using the
   intended database connection. MySQL 8.0.16 or newer is required. Check for
   existing business tables and migration history before making any changes.
2. Initialize the official RuoYi system and Quartz tables in the selected empty
   application schema according to [RuoYi migration](../development/ruoyi-migration.md).
   The upstream initialization contains DROP statements; never run it against
   an existing populated schema without reviewing every affected table.
3. Apply the unchanged business SQL through a database client to the same schema,
   only after confirming that none of its 36 tables already exists. This is an
   explicit deployment step and has not been performed by the local integration.
   Manual execution does not create a Flyway history record.
4. Run [verification SQL](verify-initial-schema.sql). Inspect partial tables and
   the failed statement if execution fails: MySQL multi-table DDL is not atomic.
   Later changes belong in new migrations, not edits to an applied baseline.
5. Apply the SimRecrut permission menu after RuoYi system initialization. Configure
   the backend database, Redis and token secret, then test real login and resume
   confirmation. A frontend build does not verify this deployment path.

The resume adapter currently publishes JSON to its existing `sim_cv_cleaned`
table and uses a per-user SQLite temporary index. These are separate from the
36 business tables. Converting uploaded resumes into frozen assessment cases
requires a business service, version validation and transaction rules; this
integration does not invent rows or identifiers in Zheng's baseline.

## Validation status

Static validation passed for the expected tables, constraint counts, reference
inventory, nullability, index coverage and zero physical relationships. A MySQL
dialect parser accepted all 36 CREATE TABLE statements and all seven read-only
verification queries. This is not a MySQL server execution or a Flyway migration
run. No migration has been applied to Aiven as part of generating these files.
MySQL execution and service-level integration tests remain required for A006
and subsequent implementation work.
