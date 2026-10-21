-- Preserve published titles and enforce one editable draft per logical job.
ALTER TABLE sim_job_version
    ADD COLUMN title_snapshot VARCHAR(255) NULL,
    ADD COLUMN active_criteria_json JSON NULL,
    ADD COLUMN extraction_status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    ADD COLUMN extraction_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN extraction_error VARCHAR(64) NULL,
    ADD COLUMN extraction_started_at DATETIME(3) NULL,
    ADD COLUMN editable_job_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'DRAFT' THEN job_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_sim_job_single_draft (editable_job_id),
    ADD CONSTRAINT ck_sim_job_extraction_status
        CHECK (extraction_status IN ('IDLE', 'RUNNING', 'COMPLETE', 'FAILED_RETRYABLE'));

UPDATE sim_job_version v JOIN sim_job j ON j.id = v.job_id
SET v.title_snapshot = j.title, v.active_criteria_json = JSON_ARRAY(
    'SKILLS', 'EXPERIENCE', 'EDUCATION', 'LANGUAGES', 'PROJECTS');

ALTER TABLE sim_job_version
    MODIFY title_snapshot VARCHAR(255) NOT NULL,
    MODIFY active_criteria_json JSON NOT NULL,
    ADD CONSTRAINT ck_sim_job_published_weights CHECK (
        status != 'PUBLISHED' OR (published_at IS NOT NULL AND
        weight_skills + weight_experience + weight_education + weight_languages + weight_projects = 1));

ALTER TABLE sim_job_requirement
    ADD COLUMN assessment_included TINYINT NOT NULL DEFAULT 1,
    ADD COLUMN reviewed TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN evidence_location VARCHAR(255) NULL,
    ADD COLUMN warnings_json JSON NULL,
    ADD UNIQUE KEY uq_sim_job_requirement_code (job_version_id, requirement_code),
    ADD CONSTRAINT ck_sim_job_req_included CHECK (assessment_included IN (0, 1)),
    ADD CONSTRAINT ck_sim_job_req_reviewed CHECK (reviewed IN (0, 1));
UPDATE sim_job_requirement SET warnings_json = JSON_ARRAY();
ALTER TABLE sim_job_requirement MODIFY warnings_json JSON NOT NULL;

-- Durable replay receipts contain job metadata only. All writes serialize on sim_user first.
CREATE TABLE sim_job_operation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operation_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    job_public_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_sim_job_operation (user_id, operation_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sim_permission (permission_code, name, description)
SELECT p.code, p.name, p.name FROM (
    SELECT 'simrecrut:job:list' AS code, 'Read managed jobs' AS name
    UNION ALL SELECT 'simrecrut:job:create', 'Create managed job drafts'
    UNION ALL SELECT 'simrecrut:job:edit', 'Edit and extract managed drafts'
    UNION ALL SELECT 'simrecrut:job:publish', 'Validate and publish managed jobs'
    UNION ALL SELECT 'simrecrut:job:browse', 'Browse published Candidate jobs'
) p WHERE NOT EXISTS (SELECT 1 FROM sim_permission x WHERE x.permission_code = p.code);

INSERT INTO sim_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sim_role r JOIN sim_permission p ON
    (r.role_code = 'HR' AND p.permission_code IN ('simrecrut:job:list', 'simrecrut:job:create',
        'simrecrut:job:edit', 'simrecrut:job:publish'))
    OR (r.role_code = 'CANDIDATE' AND p.permission_code = 'simrecrut:job:browse')
WHERE NOT EXISTS (SELECT 1 FROM sim_role_permission x WHERE x.role_id = r.id AND x.permission_id = p.id);
