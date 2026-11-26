INSERT INTO sim_permission (permission_code, name, description)
SELECT wanted.permission_code, wanted.name, wanted.description
FROM (
    SELECT 'simrecrut:hr-case:list' AS permission_code, 'List HR cases' AS name,
           'List confirmed cases within the HR access scope.' AS description
    UNION ALL SELECT 'simrecrut:hr-case:detail', 'Read HR case details',
           'Read confirmed case details within the HR access scope.'
    UNION ALL SELECT 'simrecrut:research:review:assign', 'Assign primary reviews',
           'Assign reviewers to authorized cases.'
    UNION ALL SELECT 'simrecrut:review:list', 'List assigned reviews',
           'List review tasks assigned to the current account.'
    UNION ALL SELECT 'simrecrut:review:detail', 'Read assigned reviews',
           'Read the permitted details of an assigned review task.'
    UNION ALL SELECT 'simrecrut:review:submit', 'Submit assigned reviews',
           'Submit a decision and reason for an assigned review task.'
) wanted
WHERE NOT EXISTS (
    SELECT 1 FROM sim_permission p WHERE p.permission_code = wanted.permission_code
);

INSERT INTO sim_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sim_role r
JOIN sim_permission p ON (
    r.role_code = 'HR' AND p.permission_code IN ('simrecrut:hr-case:list', 'simrecrut:hr-case:detail')
) OR (
    r.role_code = 'RESEARCHER' AND p.permission_code = 'simrecrut:research:review:assign'
) OR (
    r.role_code IN ('HR', 'RESEARCHER')
    AND p.permission_code IN ('simrecrut:review:list', 'simrecrut:review:detail', 'simrecrut:review:submit')
)
WHERE NOT EXISTS (
    SELECT 1 FROM sim_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
