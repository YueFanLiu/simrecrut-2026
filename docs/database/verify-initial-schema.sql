-- Read-only acceptance queries for the initial migration; run in its target schema.
-- Expected baseline: 36 tables, 36 PKs, 34 extra unique keys, 80 checks, zero FKs.
-- Counts can change after later migrations. Flyway history is excluded.
SELECT VERSION() AS mysql_version, DATABASE() AS current_schema;

SELECT COUNT(*) AS business_tables, 36 AS expected
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
  AND table_name IN (
        'sim_matching_rule_version',
        'sim_feature_schema_version',
        'sim_bias_protocol_version',
        'sim_protocol_version',
        'sim_dataset_source',
        'sim_job',
        'sim_job_version',
        'sim_job_requirement',
        'sim_research_profile',
        'sim_research_profile_attribute',
        'sim_corpus_version',
        'sim_corpus_profile_member',
        'sim_corpus_job_member',
        'sim_training_case',
        'sim_training_experiment',
        'sim_training_run',
        'sim_model_version',
        'sim_assessment_case',
        'sim_case_snapshot',
        'sim_case_research_attribute',
        'sim_case_temp_artifact',
        'sim_case_access_grant',
        'sim_matching_result',
        'sim_objective_result',
        'sim_ml_result',
        'sim_experiment',
        'sim_experiment_subject',
        'sim_experiment_subject_assessment',
        'sim_experiment_variant',
        'sim_experiment_result',
        'sim_experiment_metric',
        'sim_human_review',
        'sim_async_task',
        'sim_idempotency_record',
        'sim_audit_event',
        'sim_privacy_deletion_receipt'
  );

SELECT constraint_type, COUNT(*) AS actual,
       CASE constraint_type
           WHEN 'PRIMARY KEY' THEN 36
           WHEN 'UNIQUE' THEN 34
           WHEN 'CHECK' THEN 80
           WHEN 'FOREIGN KEY' THEN 0
       END AS expected
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN (
        'sim_matching_rule_version',
        'sim_feature_schema_version',
        'sim_bias_protocol_version',
        'sim_protocol_version',
        'sim_dataset_source',
        'sim_job',
        'sim_job_version',
        'sim_job_requirement',
        'sim_research_profile',
        'sim_research_profile_attribute',
        'sim_corpus_version',
        'sim_corpus_profile_member',
        'sim_corpus_job_member',
        'sim_training_case',
        'sim_training_experiment',
        'sim_training_run',
        'sim_model_version',
        'sim_assessment_case',
        'sim_case_snapshot',
        'sim_case_research_attribute',
        'sim_case_temp_artifact',
        'sim_case_access_grant',
        'sim_matching_result',
        'sim_objective_result',
        'sim_ml_result',
        'sim_experiment',
        'sim_experiment_subject',
        'sim_experiment_subject_assessment',
        'sim_experiment_variant',
        'sim_experiment_result',
        'sim_experiment_metric',
        'sim_human_review',
        'sim_async_task',
        'sim_idempotency_record',
        'sim_audit_event',
        'sim_privacy_deletion_receipt'
  )
GROUP BY constraint_type;

SELECT COUNT(*) AS physical_relationships, 0 AS expected
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE() AND constraint_type = 'FOREIGN KEY'
  AND table_name IN (
        'sim_matching_rule_version',
        'sim_feature_schema_version',
        'sim_bias_protocol_version',
        'sim_protocol_version',
        'sim_dataset_source',
        'sim_job',
        'sim_job_version',
        'sim_job_requirement',
        'sim_research_profile',
        'sim_research_profile_attribute',
        'sim_corpus_version',
        'sim_corpus_profile_member',
        'sim_corpus_job_member',
        'sim_training_case',
        'sim_training_experiment',
        'sim_training_run',
        'sim_model_version',
        'sim_assessment_case',
        'sim_case_snapshot',
        'sim_case_research_attribute',
        'sim_case_temp_artifact',
        'sim_case_access_grant',
        'sim_matching_result',
        'sim_objective_result',
        'sim_ml_result',
        'sim_experiment',
        'sim_experiment_subject',
        'sim_experiment_subject_assessment',
        'sim_experiment_variant',
        'sim_experiment_result',
        'sim_experiment_metric',
        'sim_human_review',
        'sim_async_task',
        'sim_idempotency_record',
        'sim_audit_event',
        'sim_privacy_deletion_receipt'
  );

WITH expected_tables AS (
    SELECT 'sim_matching_rule_version' AS table_name
    UNION ALL
    SELECT 'sim_feature_schema_version' AS table_name
    UNION ALL
    SELECT 'sim_bias_protocol_version' AS table_name
    UNION ALL
    SELECT 'sim_protocol_version' AS table_name
    UNION ALL
    SELECT 'sim_dataset_source' AS table_name
    UNION ALL
    SELECT 'sim_job' AS table_name
    UNION ALL
    SELECT 'sim_job_version' AS table_name
    UNION ALL
    SELECT 'sim_job_requirement' AS table_name
    UNION ALL
    SELECT 'sim_research_profile' AS table_name
    UNION ALL
    SELECT 'sim_research_profile_attribute' AS table_name
    UNION ALL
    SELECT 'sim_corpus_version' AS table_name
    UNION ALL
    SELECT 'sim_corpus_profile_member' AS table_name
    UNION ALL
    SELECT 'sim_corpus_job_member' AS table_name
    UNION ALL
    SELECT 'sim_training_case' AS table_name
    UNION ALL
    SELECT 'sim_training_experiment' AS table_name
    UNION ALL
    SELECT 'sim_training_run' AS table_name
    UNION ALL
    SELECT 'sim_model_version' AS table_name
    UNION ALL
    SELECT 'sim_assessment_case' AS table_name
    UNION ALL
    SELECT 'sim_case_snapshot' AS table_name
    UNION ALL
    SELECT 'sim_case_research_attribute' AS table_name
    UNION ALL
    SELECT 'sim_case_temp_artifact' AS table_name
    UNION ALL
    SELECT 'sim_case_access_grant' AS table_name
    UNION ALL
    SELECT 'sim_matching_result' AS table_name
    UNION ALL
    SELECT 'sim_objective_result' AS table_name
    UNION ALL
    SELECT 'sim_ml_result' AS table_name
    UNION ALL
    SELECT 'sim_experiment' AS table_name
    UNION ALL
    SELECT 'sim_experiment_subject' AS table_name
    UNION ALL
    SELECT 'sim_experiment_subject_assessment' AS table_name
    UNION ALL
    SELECT 'sim_experiment_variant' AS table_name
    UNION ALL
    SELECT 'sim_experiment_result' AS table_name
    UNION ALL
    SELECT 'sim_experiment_metric' AS table_name
    UNION ALL
    SELECT 'sim_human_review' AS table_name
    UNION ALL
    SELECT 'sim_async_task' AS table_name
    UNION ALL
    SELECT 'sim_idempotency_record' AS table_name
    UNION ALL
    SELECT 'sim_audit_event' AS table_name
    UNION ALL
    SELECT 'sim_privacy_deletion_receipt' AS table_name
)
SELECT e.table_name AS missing_table
FROM expected_tables e
LEFT JOIN information_schema.tables t
  ON t.table_schema = DATABASE() AND t.table_name = e.table_name
WHERE t.table_name IS NULL OR t.table_type <> 'BASE TABLE';

WITH expected_references AS (
    SELECT 'sim_protocol_version' AS table_name, 'matching_rule_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_protocol_version' AS table_name, 'feature_schema_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_protocol_version' AS table_name, 'bias_protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_job' AS table_name, 'source_dataset_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_job' AS table_name, 'owner_user_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_job_version' AS table_name, 'job_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_job_version' AS table_name, 'matching_rule_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_job_version' AS table_name, 'created_by' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_job_version' AS table_name, 'published_by' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_job_requirement' AS table_name, 'job_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_research_profile' AS table_name, 'dataset_source_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_research_profile' AS table_name, 'reviewed_by' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_research_profile_attribute' AS table_name, 'research_profile_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_corpus_profile_member' AS table_name, 'corpus_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_corpus_profile_member' AS table_name, 'research_profile_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_corpus_job_member' AS table_name, 'corpus_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_corpus_job_member' AS table_name, 'job_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_case' AS table_name, 'corpus_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_case' AS table_name, 'research_profile_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_case' AS table_name, 'job_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_case' AS table_name, 'protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_case' AS table_name, 'feature_schema_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_experiment' AS table_name, 'corpus_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_experiment' AS table_name, 'protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_experiment' AS table_name, 'created_by' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_training_run' AS table_name, 'training_experiment_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_model_version' AS table_name, 'training_run_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_model_version' AS table_name, 'feature_schema_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_model_version' AS table_name, 'protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_assessment_case' AS table_name, 'candidate_user_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_assessment_case' AS table_name, 'job_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_assessment_case' AS table_name, 'protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_assessment_case' AS table_name, 'neutral_model_version_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_assessment_case' AS table_name, 'biased_model_version_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_case_snapshot' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_case_research_attribute' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_case_temp_artifact' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_case_access_grant' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_case_access_grant' AS table_name, 'user_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_case_access_grant' AS table_name, 'granted_by' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_matching_result' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_matching_result' AS table_name, 'matching_rule_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_objective_result' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_objective_result' AS table_name, 'protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_ml_result' AS table_name, 'case_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_ml_result' AS table_name, 'model_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_ml_result' AS table_name, 'feature_schema_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment' AS table_name, 'job_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment' AS table_name, 'protocol_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment' AS table_name, 'neutral_model_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment' AS table_name, 'biased_model_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment' AS table_name, 'created_by' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_subject' AS table_name, 'experiment_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_subject' AS table_name, 'research_profile_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_subject' AS table_name, 'candidate_case_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_subject_assessment' AS table_name, 'experiment_subject_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_variant' AS table_name, 'experiment_subject_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_result' AS table_name, 'variant_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_result' AS table_name, 'model_version_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_experiment_metric' AS table_name, 'experiment_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_human_review' AS table_name, 'case_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_human_review' AS table_name, 'experiment_variant_id' AS column_name, 'YES' AS expected_nullable
    UNION ALL
    SELECT 'sim_human_review' AS table_name, 'reviewer_user_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_idempotency_record' AS table_name, 'user_id' AS column_name, 'NO' AS expected_nullable
    UNION ALL
    SELECT 'sim_audit_event' AS table_name, 'actor_user_id' AS column_name, 'YES' AS expected_nullable
)
SELECT e.table_name, e.column_name, e.expected_nullable,
       c.column_type, c.is_nullable,
       CASE WHEN c.column_name IS NULL THEN 'MISSING COLUMN'
            WHEN c.column_type <> 'bigint' THEN 'ID TYPE MISMATCH'
            WHEN c.is_nullable <> e.expected_nullable THEN 'NULLABILITY MISMATCH'
            ELSE 'MISSING LEADING INDEX' END AS issue
FROM expected_references e
LEFT JOIN information_schema.columns c
  ON c.table_schema = DATABASE() AND c.table_name = e.table_name
  AND c.column_name = e.column_name
WHERE c.column_name IS NULL OR c.column_type <> 'bigint'
  OR c.is_nullable <> e.expected_nullable
  OR NOT EXISTS (
      SELECT 1 FROM information_schema.statistics s
      WHERE s.table_schema = DATABASE() AND s.table_name = e.table_name
        AND s.column_name = e.column_name AND s.seq_in_index = 1
        AND s.sub_part IS NULL
  );

-- Verify that V1 succeeded and inspect the recorded checksum.
SELECT installed_rank, version, description, script, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
