START TRANSACTION;

INSERT INTO sim_feature_schema_version
    (version_code, dimension_count, schema_path, schema_sha256, status, frozen_at)
SELECT 'feature-schema-dev-v1', 32, 'assessment/feature-schema-dev-v1.json',
       '451f8e2a7556f15722e666e30a220c3ccee4e17c84a8568e70bc25451806deec', 'FROZEN', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sim_feature_schema_version WHERE version_code = 'feature-schema-dev-v1'
);

INSERT INTO sim_bias_protocol_version
    (version_code, school_effect_json, referral_effect_json, gender_effect_json,
     ethnicity_effect_json, status, frozen_at)
SELECT 'bias-protocol-dev-v1', JSON_OBJECT(), JSON_OBJECT(), JSON_OBJECT(),
       JSON_OBJECT(), 'FROZEN', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sim_bias_protocol_version WHERE version_code = 'bias-protocol-dev-v1'
);

INSERT INTO sim_protocol_version
    (matching_rule_version_id, feature_schema_version_id, bias_protocol_version_id,
     version_code, t_rule, status)
SELECT r.id, f.id, b.id, 'objective-only-dev-v1', 0.5, 'FROZEN'
FROM sim_matching_rule_version r
JOIN sim_feature_schema_version f ON f.version_code = 'feature-schema-dev-v1'
    AND f.status = 'FROZEN' AND f.dimension_count = 32
    AND f.schema_path = 'assessment/feature-schema-dev-v1.json'
    AND f.schema_sha256 = '451f8e2a7556f15722e666e30a220c3ccee4e17c84a8568e70bc25451806deec'
JOIN sim_bias_protocol_version b ON b.version_code = 'bias-protocol-dev-v1' AND b.status = 'FROZEN'
WHERE r.version_code = 'job-rule-dev-v1' AND r.status = 'FROZEN'
  AND NOT EXISTS (
    SELECT 1 FROM sim_protocol_version WHERE version_code = 'objective-only-dev-v1'
  );

COMMIT;

SELECT p.version_code AS protocol_version, p.status, r.version_code AS matching_rule,
       f.version_code AS feature_schema, f.dimension_count, b.version_code AS bias_protocol,
       (p.status = 'FROZEN' AND r.status = 'FROZEN' AND r.version_code = 'job-rule-dev-v1'
        AND f.status = 'FROZEN' AND f.version_code = 'feature-schema-dev-v1'
        AND f.dimension_count = 32 AND f.schema_path = 'assessment/feature-schema-dev-v1.json'
        AND f.schema_sha256 = '451f8e2a7556f15722e666e30a220c3ccee4e17c84a8568e70bc25451806deec'
        AND b.status = 'FROZEN' AND b.version_code = 'bias-protocol-dev-v1') AS ready
FROM sim_protocol_version p
JOIN sim_matching_rule_version r ON r.id = p.matching_rule_version_id
JOIN sim_feature_schema_version f ON f.id = p.feature_schema_version_id
JOIN sim_bias_protocol_version b ON b.id = p.bias_protocol_version_id
WHERE p.version_code = 'objective-only-dev-v1';
