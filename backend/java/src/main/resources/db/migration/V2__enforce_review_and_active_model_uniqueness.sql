
ALTER TABLE `sim_human_review`
    DROP INDEX `uq_sim_human_review_02`,
    ADD UNIQUE KEY `uq_sim_review_case_assignment`
        (`case_id`, `condition_type`, `reviewer_user_id`),
    ADD UNIQUE KEY `uq_sim_review_variant_assignment`
        (`experiment_variant_id`, `condition_type`, `reviewer_user_id`),
    ADD COLUMN `primary_case_id` BIGINT GENERATED ALWAYS AS (
        CASE WHEN `purpose` = 'PRIMARY' THEN `case_id` ELSE NULL END
    ) VIRTUAL,
    ADD UNIQUE KEY `uq_sim_review_primary_case`
        (`primary_case_id`, `condition_type`);

-- Only ACTIVE rows occupy a model-type slot. Historical versions evaluate to
-- NULL and may coexist. NEUTRAL and BIASED have independent active slots.
ALTER TABLE `sim_model_version`
    ADD COLUMN `active_model_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'ACTIVE' THEN `model_type` ELSE NULL END
        ) VIRTUAL,
    ADD UNIQUE KEY `uq_sim_model_active_type` (`active_model_type`);
