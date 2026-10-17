-- Read-only A009 preflight and post-migration verification in the target schema.
-- The first four queries must return zero rows BEFORE and AFTER V2.
-- Stop conflicting writes while checking and applying the migration.
SELECT `case_id`, `condition_type`, `reviewer_user_id`, COUNT(*) AS duplicate_count
FROM `sim_human_review`
WHERE `case_id` IS NOT NULL
GROUP BY `case_id`, `condition_type`, `reviewer_user_id`
HAVING COUNT(*) > 1;

SELECT `experiment_variant_id`, `condition_type`, `reviewer_user_id`,
       COUNT(*) AS duplicate_count
FROM `sim_human_review`
WHERE `experiment_variant_id` IS NOT NULL
GROUP BY `experiment_variant_id`, `condition_type`, `reviewer_user_id`
HAVING COUNT(*) > 1;

SELECT `case_id`, `condition_type`, COUNT(*) AS duplicate_count
FROM `sim_human_review`
WHERE `case_id` IS NOT NULL AND `purpose` = 'PRIMARY'
GROUP BY `case_id`, `condition_type`
HAVING COUNT(*) > 1;

SELECT `model_type`, COUNT(*) AS active_count
FROM `sim_model_version`
WHERE `status` = 'ACTIVE'
GROUP BY `model_type`
HAVING COUNT(*) > 1;

-- After V2: four rows, non_unique=0 and the column lists shown in the guide.
SELECT `table_name`, `index_name`, `non_unique`,
       GROUP_CONCAT(`column_name` ORDER BY `seq_in_index` SEPARATOR ',') AS columns_in_order
FROM information_schema.statistics
WHERE `table_schema` = DATABASE()
  AND ((`table_name` = 'sim_human_review' AND `index_name` IN (
      'uq_sim_review_case_assignment', 'uq_sim_review_variant_assignment',
      'uq_sim_review_primary_case'
  )) OR (`table_name` = 'sim_model_version' AND `index_name` = 'uq_sim_model_active_type'))
GROUP BY `table_name`, `index_name`, `non_unique`;

-- After V2: two VIRTUAL GENERATED columns; inspect their expressions.
SELECT `table_name`, `column_name`, `extra`, `generation_expression`
FROM information_schema.columns
WHERE `table_schema` = DATABASE()
  AND ((`table_name` = 'sim_human_review' AND `column_name` = 'primary_case_id')
       OR (`table_name` = 'sim_model_version' AND `column_name` = 'active_model_type'));

-- Expected: zero SQL foreign keys on the affected business tables.
SELECT COUNT(*) AS foreign_keys, 0 AS expected
FROM information_schema.table_constraints
WHERE `constraint_schema` = DATABASE() AND `constraint_type` = 'FOREIGN KEY'
  AND `table_name` IN ('sim_human_review', 'sim_model_version');

-- After Flyway: version 2 must have success=1. No entry is changed by this script.
SELECT `version`, `script`, `checksum`, `success`
FROM `flyway_schema_history`
WHERE `version` = '2';
