ALTER TABLE sim_assessment_case
    ADD COLUMN revision_no BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN draft_json JSON NULL,
    ADD COLUMN confirmed_absent_json JSON NULL,
    ADD COLUMN stage VARCHAR(32) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN cleanup_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN assessment_reference_month CHAR(7) NULL,
    ADD COLUMN deletion_reason VARCHAR(32) NULL,
    ADD CONSTRAINT ck_case_revision CHECK (revision_no > 0);

ALTER TABLE sim_async_task
    ADD COLUMN lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN lease_until DATETIME(3) NULL,
    ADD COLUMN worker_id VARCHAR(100) NULL,
    ADD COLUMN active_case_task VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (CASE WHEN task_type IN ('CASE_EXTRACT', 'CASE_ASSESS', 'CASE_CLEANUP', 'CASE_PURGE')
            AND status IN ('QUEUED', 'RUNNING', 'RETRYABLE')
            THEN CONCAT(resource_public_id, ':', task_type) ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_active_case_task (active_case_task);

-- Replay receipts contain operation metadata, never CV facts or results.
CREATE TABLE sim_case_operation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operation_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    case_public_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action VARCHAR(32) NOT NULL,
    http_status SMALLINT NOT NULL,
    response_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_case_operation (user_id, operation_key),
    KEY ix_case_operation_target (case_public_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Private saved stage outputs retain fixed inputs; Candidate DTOs expose allowed fields only.
CREATE TABLE sim_case_stage_result (
    case_id BIGINT NOT NULL,
    method VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (case_id, method),
    CONSTRAINT ck_case_result_method CHECK (method IN ('PROFESSIONAL', 'NEUTRAL', 'BIASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sim_permission (permission_code, name, description)
SELECT CONCAT('simrecrut:case:', code), name, name FROM (
    SELECT 'list' AS code, 'Read own cases' AS name UNION ALL
    SELECT 'create', 'Create own cases' UNION ALL SELECT 'upload', 'Upload case CV' UNION ALL
    SELECT 'edit', 'Review own case facts' UNION ALL SELECT 'confirm', 'Confirm own case facts' UNION ALL
    SELECT 'run', 'Run and retry own cases' UNION ALL SELECT 'results', 'Read own case results' UNION ALL
    SELECT 'delete', 'Delete own cases'
) permissions;
INSERT INTO sim_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sim_role r JOIN sim_permission p ON p.permission_code LIKE 'simrecrut:case:%'
WHERE r.role_code = 'CANDIDATE';
