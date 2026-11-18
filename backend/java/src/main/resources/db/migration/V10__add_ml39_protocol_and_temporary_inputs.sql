ALTER TABLE sim_bias_protocol_version ADD COLUMN name_cue_effect_json JSON NULL;

ALTER TABLE sim_research_profile_attribute
    DROP CHECK ck_sim_research_attr_type,
    ADD CONSTRAINT ck_sim_research_attr_type CHECK (attribute_type IN
        ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY', 'NAME_CUE'));
ALTER TABLE sim_case_research_attribute
    DROP CHECK ck_sim_case_research_attr_type,
    ADD CONSTRAINT ck_sim_case_research_attr_type CHECK (attribute_type IN
        ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY', 'NAME_CUE'));
ALTER TABLE sim_experiment
    DROP CHECK ck_sim_experiment_controlled_attr,
    ADD CONSTRAINT ck_sim_experiment_controlled_attr CHECK (controlled_attribute IN
        ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY', 'NAME_CUE'));
ALTER TABLE sim_experiment_variant
    DROP CHECK ck_sim_experiment_variant_changed_attr,
    ADD CONSTRAINT ck_sim_experiment_variant_changed_attr CHECK (changed_attribute IN
        ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY', 'NAME_CUE'));

CREATE TABLE sim_case_model_input (
    case_id BIGINT NOT NULL PRIMARY KEY,
    enabled_text BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_portrait BOOLEAN NOT NULL DEFAULT FALSE,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    confirmed_at DATETIME(3) NULL,
    purged_at DATETIME(3) NULL,
    cleanup_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NONE',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE sim_async_task
    DROP INDEX uq_active_case_task,
    MODIFY COLUMN active_case_task VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (CASE WHEN task_type IN
            ('CASE_EXTRACT', 'CASE_ASSESS', 'CASE_CLEANUP', 'CASE_MODEL_INPUT_CLEANUP', 'CASE_PURGE')
            AND status IN ('QUEUED', 'RUNNING', 'RETRYABLE')
            THEN CONCAT(resource_public_id, ':', task_type) ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_active_case_task (active_case_task);
