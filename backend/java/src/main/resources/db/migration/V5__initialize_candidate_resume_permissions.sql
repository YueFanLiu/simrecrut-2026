-- Grant own-resume operations to Candidate accounts only.
INSERT INTO sim_permission (permission_code, name, description)
SELECT permissions.code, permissions.name, permissions.description
FROM (
    SELECT 'simrecrut:resume:list' AS code, 'Read own resumes' AS name,
        'List and inspect the authenticated Candidate account resumes.' AS description
    UNION ALL SELECT 'simrecrut:resume:upload', 'Import own resume', 'Import a temporary PDF for the current account.'
    UNION ALL SELECT 'simrecrut:resume:edit', 'Edit own resume', 'Extract and correct professional facts.'
    UNION ALL SELECT 'simrecrut:resume:confirm', 'Confirm own resume', 'Confirm, publish and clean up reviewed facts.'
    UNION ALL SELECT 'simrecrut:resume:delete', 'Delete own resume', 'Revoke access and delete owned resume data.'
) AS permissions
WHERE NOT EXISTS (
    SELECT 1 FROM sim_permission p WHERE p.permission_code = permissions.code
);

INSERT INTO sim_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sim_role r
INNER JOIN sim_permission p ON p.permission_code IN (
    'simrecrut:resume:list', 'simrecrut:resume:upload', 'simrecrut:resume:edit',
    'simrecrut:resume:confirm', 'simrecrut:resume:delete'
)
WHERE r.role_code = 'CANDIDATE'
  AND NOT EXISTS (
      SELECT 1 FROM sim_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
