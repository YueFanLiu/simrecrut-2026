START TRANSACTION;

INSERT INTO sim_feature_schema_version
    (version_code, dimension_count, schema_path, schema_sha256, status, frozen_at)
SELECT 'feature-schema-dev-v2', 32, 'assessment/feature-schema-dev-v2.json',
       '1b2e140b3c9d173a6cdee796b8430e6983a419a096160bc1e26616986fd98e72', 'FROZEN', UTC_TIMESTAMP(3)
WHERE NOT EXISTS (
    SELECT 1 FROM sim_feature_schema_version WHERE version_code = 'feature-schema-dev-v2'
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
SELECT r.id, f.id, b.id, 'objective-only-dev-v2', 0.5, 'FROZEN'
FROM sim_matching_rule_version r
JOIN sim_feature_schema_version f ON f.version_code = 'feature-schema-dev-v2'
    AND f.status = 'FROZEN' AND f.dimension_count = 32
    AND f.schema_path = 'assessment/feature-schema-dev-v2.json'
    AND f.schema_sha256 = '1b2e140b3c9d173a6cdee796b8430e6983a419a096160bc1e26616986fd98e72'
JOIN sim_bias_protocol_version b ON b.version_code = 'bias-protocol-dev-v1' AND b.status = 'FROZEN'
WHERE r.version_code = 'job-rule-dev-v1' AND r.status = 'FROZEN'
  AND NOT EXISTS (
    SELECT 1 FROM sim_protocol_version WHERE version_code = 'objective-only-dev-v2'
  );

COMMIT;

SELECT p.version_code AS protocol_version, p.status, r.version_code AS matching_rule,
       f.version_code AS feature_schema, f.dimension_count, b.version_code AS bias_protocol,
       (p.status = 'FROZEN' AND r.status = 'FROZEN' AND r.version_code = 'job-rule-dev-v1'
        AND f.status = 'FROZEN' AND f.version_code = 'feature-schema-dev-v2'
        AND f.dimension_count = 32 AND f.schema_path = 'assessment/feature-schema-dev-v2.json'
        AND f.schema_sha256 = '1b2e140b3c9d173a6cdee796b8430e6983a419a096160bc1e26616986fd98e72'
        AND b.status = 'FROZEN' AND b.version_code = 'bias-protocol-dev-v1') AS ready
FROM sim_protocol_version p
JOIN sim_matching_rule_version r ON r.id = p.matching_rule_version_id
JOIN sim_feature_schema_version f ON f.id = p.feature_schema_version_id
JOIN sim_bias_protocol_version b ON b.id = p.bias_protocol_version_id
WHERE p.version_code = 'objective-only-dev-v2';
