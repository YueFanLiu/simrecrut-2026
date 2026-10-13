-- Grant only the real resume workflow to the Candidate role.
INSERT INTO sys_role (role_name, role_key, role_sort, status, del_flag, create_by, create_time)
SELECT 'Candidate', 'candidate', 3, '0', '0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'candidate');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_key = 'candidate' AND r.del_flag = '0'
  AND m.menu_id IN (9100, 9101, 9190, 9191, 9192, 9193)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id = r.role_id AND existing.menu_id = m.menu_id
  );
