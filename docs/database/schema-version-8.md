# Schema version 8

Flyway initializes an empty MySQL schema with
[`B8__initialize_schema_through_v8.sql`](../../backend/java/src/main/resources/db/migration/B8__initialize_schema_through_v8.sql).
The file combines V1 through V8 in version order, including role and permission
seeds. Databases with existing Flyway history continue through the original
versioned migrations. Future schema changes start at V9.

## Verify both installation paths

1. Start the connected Java backend against a dedicated empty MySQL schema.
   Check that `flyway_schema_history` records the successful B8 script at version 8.
2. Use a second test schema with Flyway history from a previous release.
   Start the same backend, check that the missing V migrations succeeded through
   V8, and confirm that existing accounts and business records remain readable.
   For full parity, also run V1 through V8 on a separate empty reference schema
   using a migration location that contains only those eight files.
3. Compare both fresh schemas' tables, columns, indexes, and seeded role and
   permission mappings. Run [the uniqueness checks](verify-review-model-uniqueness.sql)
   on both. Restart the backend to verify that Flyway validates the recorded history.

The baseline installs schema and access metadata. For workflow testing, prepare
test accounts, a frozen matching rule and protocol, and active neutral and biased
model versions with artifacts supported by the configured inference service.
Review assignment also needs a retained case and an active research access grant.
