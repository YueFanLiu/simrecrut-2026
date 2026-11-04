-- Select the target development schema before running this script.
-- The rule is for testing the job workflow.
-- An existing version is preserved so published jobs retain their original rule.

INSERT INTO sim_matching_rule_version
    (version_code, config_path, config_sha256, description, status, frozen_at)
SELECT 'job-rule-dev-v1', 'job-rule-dev-v1.json',
       '0a99d721e4a46d77bdd30be20af5643c10352fd547faa542f00f0e0bf6b79af7',
       'Development catalog for the job workflow.', 'FROZEN', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sim_matching_rule_version WHERE version_code = 'job-rule-dev-v1'
);

SELECT version_code, status, config_path, config_sha256,
       (status = 'FROZEN' AND config_path = 'job-rule-dev-v1.json'
        AND config_sha256 = '0a99d721e4a46d77bdd30be20af5643c10352fd547faa542f00f0e0bf6b79af7') AS ready
FROM sim_matching_rule_version
WHERE version_code = 'job-rule-dev-v1';
