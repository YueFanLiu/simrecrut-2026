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

The project has selected independent identity management. Before business writes
are implemented, replace the documented identity target with the approved user
table and align ID types and account-deletion behavior. Do not invent an account
ID in a request and treat it as an authenticated user. Referencing services must
validate the parent, access and state inside the write transaction.

## Intentionally separate work

This migration preserves the requested original baseline. It does not include
identity tables, seed users, name-study fields, task leases or the later fixes
described in Word section 5.8. Implement those as subsequent migrations.

In V1 alone, the retained nullable composite review key does not prevent all
duplicate assignments, and primary human-review slots and active core model
slots have no conditional unique keys. The subsequent
[V2 uniqueness migration](review-model-uniqueness.md) adds these constraints;
its MySQL execution and concurrent-write acceptance remain pending.
ML result status permits FAILED in this baseline,
but scores are required: services must insert successful predictions only and
record technical failures in tasks/cases. Subject deduplication, active criteria,
published-weight totals, training membership, generated-sample identity, metric
replacement and retry-safe deletion receipts also require later changes.

Reference existence, ownership, compatible versions, frozen content, parent-lock
order, explicit child cleanup and permission-limited response fields must be
implemented by services. MySQL does not cascade deletion. The baseline alone is
not a working login or assessment application.

## Apply with Flyway

Use a dedicated empty project schema on MySQL 8.0.16 or newer, with enforced
CHECK constraints. MySQL 8.x is the intended dialect; this is not a MariaDB or
SQLite migration. This file contains no CREATE DATABASE, USE, DROP, baseline
operation, password or connection address. It operates in the schema selected
by the application's datasource.

1. Inspect the target database before changing anything:

   ```sql
   SELECT VERSION(), DATABASE();
   SHOW FULL TABLES;
   ```

   If `flyway_schema_history` already exists, inspect its versions and checksum
   history. Do not assume V1 is available or modify an applied migration.

2. Follow [local setup](../development/local-setup.md) to point `DB_URL`,
   `DB_USERNAME` and `DB_PASSWORD` at the intended schema in the ignored `.env`.
   Retain the appropriate TLS connection settings. Never paste credentials into
   SQL or tracked documentation.
3. Flyway is already enabled in the application YAML. If a local environment
   override disables it, set `SPRING_FLYWAY_ENABLED=true`. Do not set
   `baseline-on-migrate=true` to bypass an unexplained non-empty schema. An
   existing `defaultdb` with unknown objects must be inspected first. In
   particular, a default baseline at version 1 would skip this V1 migration.
4. Start the backend from the repository root:

   ```powershell
   .\scripts\dev\java.ps1 -Action run
   ```

   Flyway applies all pending migrations, including V1 and then V2 on an empty
   schema, and creates its history table. Run the V2 preflight described in the
   [uniqueness guide](review-model-uniqueness.md) before starting against an
   existing V1 schema. No manual copying of this SQL into the cloud console is
   necessary. Redis readiness is separate from successful schema migration.
5. Run the [read-only verification SQL](verify-initial-schema.sql) in the same
   target schema. Its initial constraint counts apply to V1 alone; after V2,
   expect 37 additional unique keys rather than 34 and also run the V2
   verification script. Expect no missing tables or unindexed reference columns.
   There should be 36
   business tables plus Flyway history in an otherwise empty schema.

MySQL DDL does not make a multi-table migration atomic. If application fails,
inspect the failed statement, partial tables and Flyway history before retrying.
Do not drop tables or repair history without understanding the partial state.
Once V1 has been applied, keep its contents unchanged and add later versions.

## Validation status

Static validation passed for the expected tables, constraint counts, reference
inventory, nullability, index coverage and zero physical relationships. A MySQL
dialect parser accepted all 36 CREATE TABLE statements and all seven read-only
verification queries. This is not a MySQL server execution or a Flyway migration
run. No migration has been applied to Aiven as part of generating these files.
MySQL execution and service-level integration tests remain required for A006
and subsequent implementation work.
