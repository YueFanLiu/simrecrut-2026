# Review and active model uniqueness

The [V2 migration](../../backend/java/src/main/resources/db/migration/V2__enforce_review_and_active_model_uniqueness.sql)
implements the A009 database constraints on top of the unchanged V1 baseline.
It changes two existing tables, creates no tables and adds no foreign keys.

## Existing fields and new constraints

V1 already supplies all business fields needed by these rules:

| Table | Existing fields | Rule |
| --- | --- | --- |
| `sim_human_review` | `case_id`, `condition_type`, `reviewer_user_id` | One assignment per case, condition and reviewer. |
| `sim_human_review` | `experiment_variant_id`, `condition_type`, `reviewer_user_id` | One assignment per variant, condition and reviewer. |
| `sim_human_review` | `case_id`, `condition_type`, `purpose` | At most one PRIMARY review per case and condition. |
| `sim_model_version` | `model_type`, `status` | At most one ACTIVE version per model type. |

V1 requires exactly one of the two review targets. The old
`uq_sim_human_review_02` includes both nullable targets and cannot prevent
duplicate assignments. V2 replaces it with separate case and variant unique
indexes. A case ID and a variant ID with the same number remain different targets.
The other existing indexes and checks are retained.

V2 adds two virtual generated columns. They are calculated by MySQL; application
INSERT and UPDATE statements must omit these columns:

- `primary_case_id` is `case_id` for PRIMARY reviews and NULL otherwise.
  A unique key on `(primary_case_id, condition_type)` reserves the primary slot
  without restricting RESEARCH_REPEAT reviews by different reviewers.
- `active_model_type` is `model_type` for ACTIVE models and NULL otherwise.
  A unique key on this column allows one active NEUTRAL and one active BIASED
  model simultaneously, alongside any number of historical versions.

This uses MySQL's support for
[unique indexes on virtual generated columns](https://dev.mysql.com/doc/refman/8.0/en/create-table-secondary-indexes.html).
The migration requires MySQL 8.0.16+ with the V1 checks enforced.

The review rules cover every status, including CANCELLED and EXPIRED. Changing
status does not free an assignment or primary slot. Reassignment must update
the existing task under application authorization and audit rules. V2 does not
introduce a cancellation exception, or a primary-slot limit for variants.

## Applying and inspecting V2

1. Stop conflicting review/model writes and run the first four queries in the
   [read-only verification script](verify-review-model-uniqueness.sql).
   All must return no rows. Investigate conflicts before migration; the migration
   never automatically deletes reviews or retires models.
2. Confirm that Flyway history records a successful V1 and no applied V2.
   Follow the existing [local setup](../development/local-setup.md) to start
   the backend with Flyway enabled. Flyway then applies V2 once.
3. Run the complete verification script. Expect four unique indexes, two virtual
   generated columns, zero foreign keys on the affected tables, and successful
   Flyway version 2. The expected index column lists are:

   | Index | Columns in order |
   | --- | --- |
   | `uq_sim_review_case_assignment` | `case_id,condition_type,reviewer_user_id` |
   | `uq_sim_review_variant_assignment` | `experiment_variant_id,condition_type,reviewer_user_id` |
   | `uq_sim_review_primary_case` | `primary_case_id,condition_type` |
   | `uq_sim_model_active_type` | `active_model_type` |

The initial-schema count script describes V1. Immediately after V1 plus V2,
the business baseline has 36 tables, 36 primary keys, 37 additional unique keys
and 80 checks. MySQL DDL is not atomic across the two ALTER statements. If the
second fails, inspect partial changes and Flyway history before retrying.

Services should translate duplicate-key error 1062 into a conflict response.
Switching models should retire the previous active model and activate its
replacement in the same transaction, serializing competing switches per type.
The unique key remains the final database guard for concurrent writes.

## Required MySQL acceptance checks

Use an isolated test schema with V1 and V2, and valid synthetic records with
distinct public IDs and model version codes. Check these behaviors:

| Attempt | Expected result |
| --- | --- |
| Duplicate case, condition and reviewer | Duplicate-key rejection. |
| Duplicate variant, condition and reviewer | Duplicate-key rejection. |
| Same numeric case and variant ID, same condition and reviewer | Allowed as different targets. |
| Same target and reviewer, different condition | Allowed. |
| Two RESEARCH_REPEAT tasks for a case/condition, different reviewers | Allowed. |
| Two PRIMARY tasks for a case/condition, different reviewers | Duplicate-key rejection. |
| Promote a RESEARCH_REPEAT task into an occupied PRIMARY slot | Duplicate-key rejection. |
| Two ACTIVE NEUTRAL models or two ACTIVE BIASED models | Duplicate-key rejection. |
| One ACTIVE NEUTRAL and one ACTIVE BIASED model | Allowed. |
| Multiple non-ACTIVE models of the same type | Allowed. |
| Activate an existing model when its type has an ACTIVE version | Duplicate-key rejection. |

Repeat each conflicting-write scenario using two independent connections:
start transactions in both, perform the first write without committing, then
issue the competing write in the second connection. Commit the first; the
second must fail with error 1062, leaving one committed record for the slot.
Also repeat with the first transaction rolled back; the competing write should
then succeed. Use different public IDs/version codes so the tested error comes
from the intended new index. Roll back test data or discard only the isolated
test schema after the checks.

Validation so far consists of local static checks. V2 has not been executed on
MySQL or applied to the cloud database; concurrent-write acceptance remains
pending. Generating this migration alone does not complete A009 acceptance.
