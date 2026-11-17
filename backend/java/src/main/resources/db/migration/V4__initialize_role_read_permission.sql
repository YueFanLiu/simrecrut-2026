-- Register the role catalogue permission without creating or promoting accounts.
INSERT INTO sim_permission (permission_code, name, description)
SELECT
    'simrecrut:role:list',
    'Read roles',
    'Read the application role catalogue.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sim_permission
    WHERE permission_code = 'simrecrut:role:list'
);

INSERT INTO sim_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sim_role r
INNER JOIN sim_permission p
    ON p.permission_code = 'simrecrut:role:list'
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM sim_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
