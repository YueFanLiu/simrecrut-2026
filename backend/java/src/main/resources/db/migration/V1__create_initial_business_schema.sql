-- SimRecrut initial business schema: 36 tables, 65 documented logical links.
-- Derived from the supplied Word sections 5.6-5.9 and PDF pages 16-17.
-- Read docs/database/initial-schema.md before applying this migration.
-- This is the original business baseline, not the complete application schema.
-- Identity tables, name-study extensions and later business-rule fixes are separate.
-- User references retain the original sys_user.user_id logical target only.
-- No identity table is required to execute this DDL; application writes need identity.
-- Requires MySQL 8.0.16+ (enforced CHECK constraints), InnoDB and UTC timestamps.
-- Apply to a dedicated empty schema. No CREATE DATABASE, USE, DROP or seed accounts.
-- Physical relationships and automatic deletion actions are deliberately absent.

-- sim_matching_rule_version: versioned matching definitions.
-- Write/validation owner: ProtocolService.
CREATE TABLE `sim_matching_rule_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `config_path` VARCHAR(512) NOT NULL,
    `config_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `description` TEXT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `frozen_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_matching_rule_version_01` (`version_code`),
    CONSTRAINT `ck_sim_matching_rule_status` CHECK (`status` IN ('DRAFT', 'FROZEN', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_feature_schema_version: model input layout.
-- Write/validation owner: ProtocolService.
CREATE TABLE `sim_feature_schema_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `dimension_count` INT NOT NULL,
    `schema_path` VARCHAR(512) NOT NULL,
    `schema_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `frozen_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_feature_schema_version_01` (`version_code`),
    CONSTRAINT `ck_sim_feature_schema_dim` CHECK (`dimension_count` > 0),
    CONSTRAINT `ck_sim_feature_schema_status` CHECK (`status` IN ('DRAFT', 'FROZEN', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_bias_protocol_version: controlled research effects.
-- Write/validation owner: ProtocolService.
CREATE TABLE `sim_bias_protocol_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `school_effect_json` JSON NOT NULL,
    `referral_effect_json` JSON NOT NULL,
    `gender_effect_json` JSON NOT NULL,
    `ethnicity_effect_json` JSON NOT NULL,
    `unknown_effect` DECIMAL(9,8) NOT NULL DEFAULT 0,
    `calibration_summary_json` JSON NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `frozen_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_bias_protocol_version_01` (`version_code`),
    CONSTRAINT `ck_sim_bias_unknown_zero` CHECK (`unknown_effect` = 0),
    CONSTRAINT `ck_sim_bias_protocol_status` CHECK (`status` IN ('DRAFT', 'FROZEN', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_protocol_version: one consistent rule/schema/bias package.
-- Write/validation owner: ProtocolService.
-- Logical link: matching_rule_version_id -> sim_matching_rule_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: feature_schema_version_id -> sim_feature_schema_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: bias_protocol_version_id -> sim_bias_protocol_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_protocol_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `matching_rule_version_id` BIGINT NOT NULL,
    `feature_schema_version_id` BIGINT NOT NULL,
    `bias_protocol_version_id` BIGINT NOT NULL,
    `version_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `t_rule` DECIMAL(8,7) NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_protocol_version_01` (`version_code`),
    KEY `ix_sim_protocol_version_01` (`matching_rule_version_id`),
    KEY `ix_sim_protocol_version_02` (`feature_schema_version_id`),
    KEY `ix_sim_protocol_version_03` (`bias_protocol_version_id`),
    CONSTRAINT `ck_sim_protocol_t_rule` CHECK (`t_rule` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_protocol_status` CHECK (`status` IN ('DRAFT', 'FROZEN', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_dataset_source: research-source provenance.
-- Write/validation owner: ResearchDatasetService.
CREATE TABLE `sim_dataset_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `version_label` VARCHAR(64) NOT NULL,
    `manifest_path` VARCHAR(512) NOT NULL,
    `source_url` VARCHAR(2048) NULL,
    `license` VARCHAR(255) NULL,
    `downloaded_at` DATETIME(3) NULL,
    `source_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `row_count` BIGINT NULL,
    `metadata_json` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_dataset_source_01` (`public_id`),
    UNIQUE KEY `uq_sim_dataset_source_02` (`name`, `version_label`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_job: logical job.
-- Write/validation owner: JobService.
-- Logical link: source_dataset_id -> sim_dataset_source.id; nullable=true; on parent deletion=BLOCK.
-- Logical link: owner_user_id -> sys_user.user_id; nullable=true; on parent deletion=CLEAR_REFERENCE.
CREATE TABLE `sim_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `source_dataset_id` BIGINT NULL,
    `owner_user_id` BIGINT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `source_record_id` VARCHAR(128) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_job_01` (`public_id`),
    KEY `ix_sim_job_01` (`owner_user_id`),
    KEY `ix_sim_job_02` (`source_dataset_id`),
    CONSTRAINT `ck_sim_job_source_type` CHECK (`source_type` IN ('HR_CREATED', 'DATASET')),
    CONSTRAINT `ck_sim_job_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_job_version: one editable or published configuration.
-- Write/validation owner: JobService.
-- Logical link: job_id -> sim_job.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: matching_rule_version_id -> sim_matching_rule_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: created_by -> sys_user.user_id; nullable=false; on parent deletion=BLOCK.
-- Logical link: published_by -> sys_user.user_id; nullable=true; on parent deletion=CLEAR_REFERENCE.
CREATE TABLE `sim_job_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_id` BIGINT NOT NULL,
    `matching_rule_version_id` BIGINT NOT NULL,
    `created_by` BIGINT NOT NULL,
    `published_by` BIGINT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `version_no` INT NOT NULL,
    `description` LONGTEXT NOT NULL,
    `public_requirements_json` JSON NOT NULL,
    `weight_skills` DECIMAL(8,7) NOT NULL,
    `weight_experience` DECIMAL(8,7) NOT NULL,
    `weight_education` DECIMAL(8,7) NOT NULL,
    `weight_languages` DECIMAL(8,7) NOT NULL,
    `weight_projects` DECIMAL(8,7) NOT NULL,
    `revision_no` BIGINT NOT NULL DEFAULT 0,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `published_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_job_version_01` (`public_id`),
    UNIQUE KEY `uq_sim_job_version_02` (`job_id`, `version_no`),
    KEY `ix_sim_job_version_01` (`matching_rule_version_id`),
    KEY `ix_sim_job_version_02` (`created_by`),
    KEY `ix_sim_job_version_03` (`published_by`),
    CONSTRAINT `ck_sim_job_weight_skills` CHECK (`weight_skills` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_job_weight_experience` CHECK (`weight_experience` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_job_weight_education` CHECK (`weight_education` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_job_weight_languages` CHECK (`weight_languages` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_job_weight_projects` CHECK (`weight_projects` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_job_version_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_job_requirement: reviewed detail used in matching.
-- Write/validation owner: JobService.
-- Logical link: job_version_id -> sim_job_version.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_job_requirement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_version_id` BIGINT NOT NULL,
    `criterion_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `requirement_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `payload_json` JSON NOT NULL,
    `mandatory` TINYINT NOT NULL DEFAULT 0,
    `public_visible` TINYINT NOT NULL DEFAULT 1,
    `display_order` INT NOT NULL DEFAULT 0,
    `source_excerpt` TEXT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_sim_job_requirement_01` (`job_version_id`),
    CONSTRAINT `ck_sim_job_req_type` CHECK (`criterion_type` IN ('SKILLS', 'EXPERIENCE', 'EDUCATION', 'LANGUAGES', 'PROJECTS')),
    CONSTRAINT `ck_sim_job_req_mandatory` CHECK (`mandatory` IN (0, 1)),
    CONSTRAINT `ck_sim_job_req_public` CHECK (`public_visible` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_research_profile: persistent reviewed research baseline.
-- Write/validation owner: ResearchDatasetService.
-- Logical link: dataset_source_id -> sim_dataset_source.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: reviewed_by -> sys_user.user_id; nullable=true; on parent deletion=CLEAR_REFERENCE.
CREATE TABLE `sim_research_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dataset_source_id` BIGINT NOT NULL,
    `reviewed_by` BIGINT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_record_id` VARCHAR(128) NOT NULL,
    `base_profile_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `professional_json` JSON NOT NULL,
    `schema_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `review_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_research_profile_01` (`public_id`),
    UNIQUE KEY `uq_sim_research_profile_02` (`dataset_source_id`, `source_record_id`),
    UNIQUE KEY `uq_sim_research_profile_03` (`base_profile_key`),
    KEY `ix_sim_research_profile_01` (`reviewed_by`),
    CONSTRAINT `ck_sim_research_profile_review` CHECK (`review_status` IN ('PENDING', 'REVIEWED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_research_profile_attribute: research labels with provenance.
-- Write/validation owner: ResearchDatasetService.
-- Logical link: research_profile_id -> sim_research_profile.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_research_profile_attribute` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `research_profile_id` BIGINT NOT NULL,
    `attribute_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `attribute_value` VARCHAR(128) NOT NULL,
    `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `provenance_json` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_research_profile_attribute_01` (`research_profile_id`, `attribute_type`, `source_type`),
    CONSTRAINT `ck_sim_research_attr_type` CHECK (`attribute_type` IN ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY')),
    CONSTRAINT `ck_sim_research_attr_source` CHECK (`source_type` IN ('DATASET', 'USER_SUPPLIED', 'SYNTHETIC'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_corpus_version: frozen training/evaluation selection.
-- Write/validation owner: TrainingService.
CREATE TABLE `sim_corpus_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `manifest_path` VARCHAR(512) NOT NULL,
    `manifest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `profile_count` BIGINT NOT NULL,
    `job_count` BIGINT NOT NULL,
    `split_strategy_json` JSON NOT NULL,
    `frozen_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_corpus_version_01` (`version_code`),
    CONSTRAINT `ck_sim_corpus_profile_count` CHECK (`profile_count` >= 0),
    CONSTRAINT `ck_sim_corpus_job_count` CHECK (`job_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_corpus_profile_member: base profile's partition.
-- Write/validation owner: TrainingService.
-- Logical link: corpus_version_id -> sim_corpus_version.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: research_profile_id -> sim_research_profile.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_corpus_profile_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `corpus_version_id` BIGINT NOT NULL,
    `research_profile_id` BIGINT NOT NULL,
    `partition_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_corpus_profile_member_01` (`corpus_version_id`, `research_profile_id`),
    KEY `ix_sim_corpus_profile_member_01` (`research_profile_id`),
    CONSTRAINT `ck_sim_corpus_partition` CHECK (`partition_code` IN ('TRAIN', 'VALIDATION', 'TEST'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_corpus_job_member: jobs in a corpus.
-- Write/validation owner: TrainingService.
-- Logical link: corpus_version_id -> sim_corpus_version.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: job_version_id -> sim_job_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_corpus_job_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `corpus_version_id` BIGINT NOT NULL,
    `job_version_id` BIGINT NOT NULL,
    `held_out_test` TINYINT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_corpus_job_member_01` (`corpus_version_id`, `job_version_id`),
    KEY `ix_sim_corpus_job_member_01` (`job_version_id`),
    CONSTRAINT `ck_sim_corpus_job_heldout` CHECK (`held_out_test` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_training_case: generated training sample.
-- Write/validation owner: TrainingService.
-- Logical link: corpus_version_id -> sim_corpus_version.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: research_profile_id -> sim_research_profile.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: job_version_id -> sim_job_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: protocol_version_id -> sim_protocol_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: feature_schema_version_id -> sim_feature_schema_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_training_case` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `corpus_version_id` BIGINT NOT NULL,
    `research_profile_id` BIGINT NOT NULL,
    `job_version_id` BIGINT NOT NULL,
    `protocol_version_id` BIGINT NOT NULL,
    `feature_schema_version_id` BIGINT NOT NULL,
    `partition_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `weight_config_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `variant_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `target_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `match_skills` DECIMAL(8,7) NOT NULL,
    `match_experience` DECIMAL(8,7) NOT NULL,
    `match_education` DECIMAL(8,7) NOT NULL,
    `match_languages` DECIMAL(8,7) NOT NULL,
    `match_projects` DECIMAL(8,7) NOT NULL,
    `weight_skills` DECIMAL(8,7) NOT NULL,
    `weight_experience` DECIMAL(8,7) NOT NULL,
    `weight_education` DECIMAL(8,7) NOT NULL,
    `weight_languages` DECIMAL(8,7) NOT NULL,
    `weight_projects` DECIMAL(8,7) NOT NULL,
    `school_category` VARCHAR(128) NOT NULL,
    `referral` VARCHAR(128) NOT NULL,
    `gender` VARCHAR(128) NOT NULL,
    `ethnicity` VARCHAR(128) NOT NULL,
    `mandatory_pass` TINYINT NOT NULL,
    `objective_score` DECIMAL(8,7) NOT NULL,
    `biased_score` DECIMAL(8,7) NOT NULL,
    `neutral_label` TINYINT NOT NULL,
    `biased_label` TINYINT NOT NULL,
    `school_effect` DECIMAL(9,8) NOT NULL,
    `referral_effect` DECIMAL(9,8) NOT NULL,
    `gender_effect` DECIMAL(9,8) NOT NULL,
    `ethnicity_effect` DECIMAL(9,8) NOT NULL,
    `noise` DECIMAL(9,8) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_sim_training_case_01` (`corpus_version_id`),
    KEY `ix_sim_training_case_02` (`research_profile_id`),
    KEY `ix_sim_training_case_03` (`job_version_id`),
    KEY `ix_sim_training_case_04` (`protocol_version_id`),
    KEY `ix_sim_training_case_05` (`feature_schema_version_id`),
    CONSTRAINT `ck_sim_training_partition` CHECK (`partition_code` IN ('TRAIN', 'VALIDATION', 'TEST')),
    CONSTRAINT `ck_sim_training_g` CHECK (`mandatory_pass` IN (0, 1)),
    CONSTRAINT `ck_sim_training_neutral_label` CHECK (`neutral_label` IN (0, 1)),
    CONSTRAINT `ck_sim_training_biased_label` CHECK (`biased_label` IN (0, 1)),
    CONSTRAINT `ck_sim_training_objective_score` CHECK (`objective_score` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_training_biased_score` CHECK (`biased_score` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_training_target_mode` CHECK (`target_mode` IN ('NEUTRAL', 'BIASED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_training_experiment: development configuration.
-- Write/validation owner: TrainingService.
-- Logical link: corpus_version_id -> sim_corpus_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: protocol_version_id -> sim_protocol_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: created_by -> sys_user.user_id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_training_experiment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `corpus_version_id` BIGINT NOT NULL,
    `protocol_version_id` BIGINT NOT NULL,
    `created_by` BIGINT NOT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `model_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `architecture_json` JSON NOT NULL,
    `optimizer_json` JSON NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_training_experiment_01` (`public_id`),
    KEY `ix_sim_training_experiment_01` (`corpus_version_id`),
    KEY `ix_sim_training_experiment_02` (`protocol_version_id`),
    KEY `ix_sim_training_experiment_03` (`created_by`),
    CONSTRAINT `ck_sim_training_experiment_type` CHECK (`model_type` IN ('NEUTRAL', 'BIASED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_training_run: one seed run.
-- Write/validation owner: TrainingService.
-- Logical link: training_experiment_id -> sim_training_experiment.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_training_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `training_experiment_id` BIGINT NOT NULL,
    `seed` BIGINT NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QUEUED',
    `selected_epoch` INT NULL,
    `selected_threshold` DOUBLE NULL,
    `metrics_json` JSON NULL,
    `artifact_path` VARCHAR(512) NULL,
    `completed_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_training_run_01` (`training_experiment_id`, `seed`),
    CONSTRAINT `ck_sim_training_run_status` CHECK (`status` IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_model_version: loadable frozen artifact.
-- Write/validation owner: ModelRegistryService.
-- Logical link: training_run_id -> sim_training_run.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: feature_schema_version_id -> sim_feature_schema_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: protocol_version_id -> sim_protocol_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_model_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `training_run_id` BIGINT NOT NULL,
    `feature_schema_version_id` BIGINT NOT NULL,
    `protocol_version_id` BIGINT NOT NULL,
    `version_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `model_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `artifact_path` VARCHAR(512) NOT NULL,
    `artifact_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `threshold` DOUBLE NOT NULL,
    `common_threshold` DOUBLE NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `evaluation_json` JSON NULL,
    `activated_at` DATETIME(3) NULL,
    `retired_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_model_version_01` (`version_code`),
    KEY `ix_sim_model_version_01` (`training_run_id`),
    KEY `ix_sim_model_version_02` (`feature_schema_version_id`),
    KEY `ix_sim_model_version_03` (`protocol_version_id`),
    CONSTRAINT `ck_sim_model_threshold` CHECK (`threshold` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_model_common_threshold` CHECK (`common_threshold` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_model_status` CHECK (`status` IN ('TRAINED', 'VALIDATED', 'TESTED', 'APPROVED', 'ACTIVE', 'RETIRED')),
    CONSTRAINT `ck_sim_model_type` CHECK (`model_type` IN ('NEUTRAL', 'BIASED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_assessment_case: one simulation.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: candidate_user_id -> sys_user.user_id; nullable=false; on parent deletion=BLOCK.
-- Logical link: job_version_id -> sim_job_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: protocol_version_id -> sim_protocol_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: neutral_model_version_id -> sim_model_version.id; nullable=true; on parent deletion=BLOCK.
-- Logical link: biased_model_version_id -> sim_model_version.id; nullable=true; on parent deletion=BLOCK.
CREATE TABLE `sim_assessment_case` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `candidate_user_id` BIGINT NOT NULL,
    `job_version_id` BIGINT NOT NULL,
    `protocol_version_id` BIGINT NOT NULL,
    `neutral_model_version_id` BIGINT NULL,
    `biased_model_version_id` BIGINT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `processing_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'AWAITING_UPLOAD',
    `human_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_ASSIGNED',
    `privacy_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    `uploaded_at` DATETIME(3) NULL,
    `confirmed_at` DATETIME(3) NULL,
    `automated_started_at` DATETIME(3) NULL,
    `automated_completed_at` DATETIME(3) NULL,
    `purge_after` DATETIME(3) NOT NULL,
    `safe_error_code` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_assessment_case_01` (`public_id`),
    KEY `ix_sim_assessment_case_01` (`candidate_user_id`),
    KEY `ix_sim_assessment_case_02` (`job_version_id`),
    KEY `ix_sim_assessment_case_03` (`protocol_version_id`),
    KEY `ix_sim_assessment_case_04` (`neutral_model_version_id`),
    KEY `ix_sim_assessment_case_05` (`biased_model_version_id`),
    CONSTRAINT `ck_sim_case_processing_status` CHECK (`processing_status` IN ('AWAITING_UPLOAD', 'UPLOADED', 'EXTRACTING', 'REVIEW_REQUIRED', 'CONFIRMED', 'AUTOMATED_RUNNING', 'AUTOMATED_COMPLETE', 'FAILED_RETRYABLE', 'FAILED_FINAL', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT `ck_sim_case_human_status` CHECK (`human_status` IN ('NOT_ASSIGNED', 'PENDING', 'PARTIAL', 'COMPLETE')),
    CONSTRAINT `ck_sim_case_privacy_status` CHECK (`privacy_status` IN ('ACTIVE', 'DELETE_REQUESTED', 'PURGING', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_case_snapshot: confirmed professional facts without direct identifiers.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_case_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `schema_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `professional_json` JSON NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_case_snapshot_01` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_case_research_attribute: optional online research values.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_case_research_attribute` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `attribute_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `attribute_value` VARCHAR(128) NOT NULL,
    `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `confirmed` TINYINT NOT NULL DEFAULT 0,
    `mapping_version` VARCHAR(64) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_case_research_attribute_01` (`case_id`, `attribute_type`),
    CONSTRAINT `ck_sim_case_research_attr_type` CHECK (`attribute_type` IN ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY')),
    CONSTRAINT `ck_sim_case_research_source` CHECK (`source_type` IN ('USER_SUPPLIED', 'USER_CONFIRMED', 'UNKNOWN')),
    CONSTRAINT `ck_sim_case_research_confirmed` CHECK (`confirmed` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_case_temp_artifact: file lifecycle metadata.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_case_temp_artifact` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `artifact_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `relative_path` VARCHAR(512) NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CREATED',
    `delete_after` DATETIME(3) NOT NULL,
    `mime_type` VARCHAR(128) NULL,
    `size_bytes` BIGINT NULL,
    `sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `deleted_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_sim_case_temp_artifact_01` (`case_id`),
    CONSTRAINT `ck_sim_case_artifact_type` CHECK (`artifact_type` IN ('PDF', 'EXTRACTED_TEXT', 'OCR_TEXT', 'REDACTED_TEXT', 'EXTRACTION_RESPONSE', 'REVIEW_DRAFT')),
    CONSTRAINT `ck_sim_case_artifact_status` CHECK (`status` IN ('CREATED', 'DELETED', 'DELETE_FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_case_access_grant: explicit case permission.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: user_id -> sys_user.user_id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: granted_by -> sys_user.user_id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_case_access_grant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `granted_by` BIGINT NOT NULL,
    `access_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `expires_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_case_access_grant_01` (`case_id`, `user_id`, `access_type`),
    KEY `ix_sim_case_access_grant_01` (`user_id`),
    KEY `ix_sim_case_access_grant_02` (`granted_by`),
    CONSTRAINT `ck_sim_case_access_type` CHECK (`access_type` IN ('HR_READ', 'RESEARCH_READ'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_matching_result: frozen professional matching.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: matching_rule_version_id -> sim_matching_rule_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_matching_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `matching_rule_version_id` BIGINT NOT NULL,
    `match_skills` DECIMAL(8,7) NOT NULL,
    `match_experience` DECIMAL(8,7) NOT NULL,
    `match_education` DECIMAL(8,7) NOT NULL,
    `match_languages` DECIMAL(8,7) NOT NULL,
    `match_projects` DECIMAL(8,7) NOT NULL,
    `mandatory_pass` TINYINT NOT NULL,
    `evidence_json` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_matching_result_01` (`case_id`),
    KEY `ix_sim_matching_result_01` (`matching_rule_version_id`),
    CONSTRAINT `ck_sim_matching_g` CHECK (`mandatory_pass` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_objective_result: deterministic rule result.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: protocol_version_id -> sim_protocol_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_objective_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `protocol_version_id` BIGINT NOT NULL,
    `t_rule` DECIMAL(8,7) NOT NULL,
    `contribution_skills` DECIMAL(8,7) NOT NULL,
    `contribution_experience` DECIMAL(8,7) NOT NULL,
    `contribution_education` DECIMAL(8,7) NOT NULL,
    `contribution_languages` DECIMAL(8,7) NOT NULL,
    `contribution_projects` DECIMAL(8,7) NOT NULL,
    `score` DECIMAL(8,7) NOT NULL,
    `decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_objective_result_01` (`case_id`),
    KEY `ix_sim_objective_result_01` (`protocol_version_id`),
    CONSTRAINT `ck_sim_objective_t_rule` CHECK (`t_rule` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_objective_score` CHECK (`score` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_objective_decision` CHECK (`decision` IN ('Accept', 'Reject'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_ml_result: one model-type result per case.
-- Write/validation owner: AssessmentCaseService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: model_version_id -> sim_model_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: feature_schema_version_id -> sim_feature_schema_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_ml_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NOT NULL,
    `model_version_id` BIGINT NOT NULL,
    `feature_schema_version_id` BIGINT NOT NULL,
    `model_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `logit` DOUBLE NOT NULL,
    `score` DOUBLE NOT NULL,
    `threshold` DOUBLE NOT NULL,
    `common_threshold` DOUBLE NOT NULL,
    `decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `common_decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SUCCESS',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_ml_result_01` (`case_id`, `model_type`),
    KEY `ix_sim_ml_result_01` (`model_version_id`),
    KEY `ix_sim_ml_result_02` (`feature_schema_version_id`),
    CONSTRAINT `ck_sim_ml_result_type` CHECK (`model_type` IN ('NEUTRAL', 'BIASED')),
    CONSTRAINT `ck_sim_ml_score` CHECK (`score` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_ml_threshold` CHECK (`threshold` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_ml_common_threshold` CHECK (`common_threshold` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_ml_decision` CHECK (`decision` IN ('Accept', 'Reject')),
    CONSTRAINT `ck_sim_ml_common_decision` CHECK (`common_decision` IN ('Accept', 'Reject')),
    CONSTRAINT `ck_sim_ml_status` CHECK (`status` IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_experiment: fixed comparison configuration.
-- Write/validation owner: ExperimentService.
-- Logical link: job_version_id -> sim_job_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: protocol_version_id -> sim_protocol_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: neutral_model_version_id -> sim_model_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: biased_model_version_id -> sim_model_version.id; nullable=false; on parent deletion=BLOCK.
-- Logical link: created_by -> sys_user.user_id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_experiment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_version_id` BIGINT NOT NULL,
    `protocol_version_id` BIGINT NOT NULL,
    `neutral_model_version_id` BIGINT NOT NULL,
    `biased_model_version_id` BIGINT NOT NULL,
    `created_by` BIGINT NOT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `experiment_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `controlled_attribute` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `frozen_weights_json` JSON NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    `started_at` DATETIME(3) NULL,
    `completed_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_experiment_01` (`public_id`),
    KEY `ix_sim_experiment_01` (`created_by`),
    KEY `ix_sim_experiment_02` (`job_version_id`),
    KEY `ix_sim_experiment_03` (`protocol_version_id`),
    KEY `ix_sim_experiment_04` (`neutral_model_version_id`),
    KEY `ix_sim_experiment_05` (`biased_model_version_id`),
    CONSTRAINT `ck_sim_experiment_type` CHECK (`experiment_type` IN ('BASELINE', 'CONTROLLED_PAIR')),
    CONSTRAINT `ck_sim_experiment_controlled_attr` CHECK (`controlled_attribute` IN ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY')),
    CONSTRAINT `ck_sim_experiment_status` CHECK (`status` IN ('DRAFT', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_experiment_subject: one experiment's selected source.
-- Write/validation owner: ExperimentService.
-- Logical link: experiment_id -> sim_experiment.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: research_profile_id -> sim_research_profile.id; nullable=true; on parent deletion=BLOCK.
-- Logical link: candidate_case_id -> sim_assessment_case.id; nullable=true; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_experiment_subject` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `experiment_id` BIGINT NOT NULL,
    `research_profile_id` BIGINT NULL,
    `candidate_case_id` BIGINT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_sim_experiment_subject_01` (`experiment_id`),
    KEY `ix_sim_experiment_subject_02` (`research_profile_id`),
    KEY `ix_sim_experiment_subject_03` (`candidate_case_id`),
    CONSTRAINT `ck_sim_experiment_subject_one_source` CHECK ((`research_profile_id` IS NOT NULL AND `candidate_case_id` IS NULL) OR (`research_profile_id` IS NULL AND `candidate_case_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_experiment_subject_assessment: shared professional baseline.
-- Write/validation owner: ExperimentService.
-- Logical link: experiment_subject_id -> sim_experiment_subject.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_experiment_subject_assessment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `experiment_subject_id` BIGINT NOT NULL,
    `match_skills` DECIMAL(8,7) NOT NULL,
    `match_experience` DECIMAL(8,7) NOT NULL,
    `match_education` DECIMAL(8,7) NOT NULL,
    `match_languages` DECIMAL(8,7) NOT NULL,
    `match_projects` DECIMAL(8,7) NOT NULL,
    `weight_skills` DECIMAL(8,7) NOT NULL,
    `weight_experience` DECIMAL(8,7) NOT NULL,
    `weight_education` DECIMAL(8,7) NOT NULL,
    `weight_languages` DECIMAL(8,7) NOT NULL,
    `weight_projects` DECIMAL(8,7) NOT NULL,
    `mandatory_pass` TINYINT NOT NULL,
    `objective_score` DECIMAL(8,7) NOT NULL,
    `decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_experiment_subject_assessment_01` (`experiment_subject_id`),
    CONSTRAINT `ck_sim_exp_subject_assessment_g` CHECK (`mandatory_pass` IN (0, 1)),
    CONSTRAINT `ck_sim_exp_subject_assessment_score` CHECK (`objective_score` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_exp_subject_assessment_decision` CHECK (`decision` IN ('Accept', 'Reject'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_experiment_variant: one controlled version.
-- Write/validation owner: ExperimentService.
-- Logical link: experiment_subject_id -> sim_experiment_subject.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_experiment_variant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `experiment_subject_id` BIGINT NOT NULL,
    `variant_no` INT NOT NULL,
    `label` VARCHAR(128) NOT NULL,
    `changed_attribute` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `school_category` VARCHAR(128) NOT NULL,
    `referral` VARCHAR(128) NOT NULL,
    `gender` VARCHAR(128) NOT NULL,
    `ethnicity` VARCHAR(128) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_experiment_variant_01` (`experiment_subject_id`, `variant_no`),
    CONSTRAINT `ck_sim_experiment_variant_changed_attr` CHECK (`changed_attribute` IN ('SCHOOL_CATEGORY', 'REFERRAL', 'GENDER', 'ETHNICITY'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_experiment_result: a variant/model output.
-- Write/validation owner: ExperimentService.
-- Logical link: variant_id -> sim_experiment_variant.id; nullable=false; on parent deletion=DELETE_CHILDREN.
-- Logical link: model_version_id -> sim_model_version.id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_experiment_result` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `variant_id` BIGINT NOT NULL,
    `model_version_id` BIGINT NOT NULL,
    `model_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `score` DOUBLE NOT NULL,
    `decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `common_decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_experiment_result_01` (`variant_id`, `model_version_id`),
    KEY `ix_sim_experiment_result_01` (`model_version_id`),
    CONSTRAINT `ck_sim_experiment_result_type` CHECK (`model_type` IN ('NEUTRAL', 'BIASED')),
    CONSTRAINT `ck_sim_experiment_result_score` CHECK (`score` BETWEEN 0 AND 1),
    CONSTRAINT `ck_sim_experiment_result_decision` CHECK (`decision` IN ('Accept', 'Reject')),
    CONSTRAINT `ck_sim_experiment_result_common_decision` CHECK (`common_decision` IN ('Accept', 'Reject'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_experiment_metric: group or experiment statistics.
-- Write/validation owner: ExperimentService.
-- Logical link: experiment_id -> sim_experiment.id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_experiment_metric` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `experiment_id` BIGINT NOT NULL,
    `metric_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `group_code` VARCHAR(128) NULL,
    `scalar_value` DOUBLE NULL,
    `result_json` JSON NULL,
    `parameters_json` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_sim_experiment_metric_01` (`experiment_id`, `metric_code`, `group_code`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_human_review: assigned review.
-- Write/validation owner: HumanReviewService.
-- Logical link: case_id -> sim_assessment_case.id; nullable=true; on parent deletion=DELETE_CHILDREN.
-- Logical link: experiment_variant_id -> sim_experiment_variant.id; nullable=true; on parent deletion=DELETE_CHILDREN.
-- Logical link: reviewer_user_id -> sys_user.user_id; nullable=false; on parent deletion=BLOCK.
CREATE TABLE `sim_human_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_id` BIGINT NULL,
    `experiment_variant_id` BIGINT NULL,
    `reviewer_user_id` BIGINT NOT NULL,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `condition_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `purpose` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `candidate_visible` TINYINT NOT NULL DEFAULT 0,
    `protocol_text` TEXT NOT NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    `decision` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `reason` TEXT NULL,
    `instruction` TEXT NULL,
    `opened_at` DATETIME(3) NULL,
    `submitted_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_human_review_01` (`public_id`),
    UNIQUE KEY `uq_sim_human_review_02` (`case_id`, `experiment_variant_id`, `condition_type`, `reviewer_user_id`),
    KEY `ix_sim_human_review_01` (`reviewer_user_id`),
    KEY `ix_sim_human_review_02` (`experiment_variant_id`),
    CONSTRAINT `ck_sim_human_review_one_target` CHECK ((`case_id` IS NOT NULL AND `experiment_variant_id` IS NULL) OR (`case_id` IS NULL AND `experiment_variant_id` IS NOT NULL)),
    CONSTRAINT `ck_sim_human_review_condition` CHECK (`condition_type` IN ('STANDARD', 'INSTRUCTED_BIAS')),
    CONSTRAINT `ck_sim_human_review_purpose` CHECK (`purpose` IN ('PRIMARY', 'RESEARCH_REPEAT')),
    CONSTRAINT `ck_sim_human_review_candidate_visible` CHECK (`candidate_visible` IN (0, 1)),
    CONSTRAINT `ck_sim_human_review_status` CHECK (`status` IN ('PENDING', 'OPENED', 'SUBMITTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT `ck_sim_human_review_decision` CHECK (`decision` IN ('Accept', 'Reject'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_async_task: durable background work.
-- Write/validation owner: AsyncTaskService.
CREATE TABLE `sim_async_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `task_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `resource_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `resource_public_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QUEUED',
    `attempt_count` INT NOT NULL DEFAULT 0,
    `max_attempts` INT NOT NULL DEFAULT 3,
    `retry_at` DATETIME(3) NULL,
    `safe_error_code` VARCHAR(64) NULL,
    `safe_error_message` VARCHAR(512) NULL,
    `payload_json` JSON NULL,
    `output_json` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_async_task_01` (`public_id`),
    CONSTRAINT `ck_sim_async_task_status` CHECK (`status` IN ('QUEUED', 'RUNNING', 'RETRYABLE', 'SUCCEEDED', 'FAILED_FINAL', 'CANCELLED')),
    CONSTRAINT `ck_sim_async_task_attempt` CHECK (`attempt_count` >= 0 AND `max_attempts` > 0 AND `attempt_count` <= `max_attempts`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_idempotency_record: duplicate-safe operation result.
-- Write/validation owner: IdempotencyService.
-- Logical link: user_id -> sys_user.user_id; nullable=false; on parent deletion=DELETE_CHILDREN.
CREATE TABLE `sim_idempotency_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `operation_scope` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `request_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `http_status` SMALLINT NULL,
    `response_json` JSON NULL,
    `expires_at` DATETIME(3) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sim_idempotency_record_01` (`user_id`, `operation_scope`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_audit_event: non-content operational history.
-- Write/validation owner: AuditService.
-- Logical link: actor_user_id -> sys_user.user_id; nullable=true; on parent deletion=CLEAR_REFERENCE.
CREATE TABLE `sim_audit_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `actor_user_id` BIGINT NULL,
    `action` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `resource_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `resource_ref_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `safe_details_json` JSON NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_sim_audit_event_01` (`actor_user_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- sim_privacy_deletion_receipt: evidence of cleanup.
-- Write/validation owner: PrivacyService.
CREATE TABLE `sim_privacy_deletion_receipt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `case_public_id_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `deletion_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `deleted_at` DATETIME(3) NOT NULL,
    `cleanup_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_sim_privacy_deletion_type` CHECK (`deletion_type` IN ('USER_REQUEST', 'RETENTION_EXPIRY', 'ACCOUNT_DELETION')),
    CONSTRAINT `ck_sim_privacy_cleanup_status` CHECK (`cleanup_status` IN ('PENDING', 'COMPLETE', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
