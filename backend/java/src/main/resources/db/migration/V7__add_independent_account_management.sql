-- Keep account edits and role configuration changes protected against stale writes.
ALTER TABLE sim_user ADD COLUMN revision BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_sim_user_revision CHECK (revision > 0);
ALTER TABLE sim_role ADD COLUMN revision BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_sim_role_revision CHECK (revision > 0);

INSERT INTO sim_permission (permission_code, name, description)
VALUES
    ('simrecrut:user:list', 'Read accounts', 'Read safe account summaries and details.'),
    ('simrecrut:user:create', 'Create accounts', 'Create accounts requiring mailbox password setup.'),
    ('simrecrut:user:edit', 'Edit accounts', 'Change account email with mailbox reverification.'),
    ('simrecrut:user:status', 'Change account status', 'Enable or disable an account.'),
    ('simrecrut:user:roles', 'Assign account roles', 'Replace an account role set.'),
    ('simrecrut:role:edit', 'Edit role permissions', 'Configure fixed application roles.'),
    ('simrecrut:permission:list', 'Read permissions', 'Read the server-owned permission catalogue.');

INSERT INTO sim_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sim_role r JOIN sim_permission p
    ON p.permission_code IN ('simrecrut:user:list', 'simrecrut:user:create', 'simrecrut:user:edit',
        'simrecrut:user:status', 'simrecrut:user:roles', 'simrecrut:role:edit', 'simrecrut:permission:list')
WHERE r.role_code = 'ADMIN';
-- No accounts or credentials are created and no existing account is promoted.
