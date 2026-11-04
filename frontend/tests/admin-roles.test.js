import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAdminRolesStore } from '../src/store/modules/admin-roles.js'
import { useAdminUsersStore } from '../src/store/modules/admin-users.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { filterAdminRoles, permissionModules, rolePermissionTree, validateRoleConfiguration } from '../src/mock/admin-roles.js'
const admin = { username: 'admin', role: 'Admin' }
function setup() { setActivePinia(createPinia()); return { roles: useAdminRolesStore(), users: useAdminUsersStore() } }
test('fixed four roles exclude Reviewer/Platform and only Admin can read or change configurations', async () => {
  const { roles } = setup()
  assert.deepEqual(roles.listRoles(admin).map(row => row.name), ['Admin', 'HR', 'Researcher', 'Candidate'])
  for (const role of ['HR', 'Researcher', 'Candidate', 'Reviewer']) {
    const user = { username: 'other', role }
    assert.throws(() => roles.listRoles(user), /Admin/)
    await assert.rejects(roles.savePermissions({ name: 'HR' }, user), /Admin/)
    await assert.rejects(roles.assignRole('USER-008', 0, 'HR', user), /Admin/)
  }
  assert.throws(() => roles.readRole('Reviewer', admin), /no longer available/)
})
test('minimal role copies omit user content and counts update from A01 directory', async () => {
  const { roles, users } = setup()
  roles.roles[1].secret = 'PRIVATE'
  users.users[2].cv = 'PRIVATE'
  const row = roles.readRole('HR', admin)
  assert.deepEqual(Object.keys(row).sort(), ['name', 'description', 'status', 'revision', 'permissions', 'userCount'].sort())
  assert.equal(JSON.stringify(row).includes('PRIVATE'), false)
  assert.equal(row.userCount, 4)
  row.permissions.length = 0; assert.ok(roles.readRole('HR', admin).permissions.length > 0)
  await roles.assignRole('USER-008', 0, 'HR', admin)
  assert.equal(roles.readRole('HR', admin).userCount, 5)
  assert.equal(roles.readRole('Candidate', admin).userCount, 2)
  assert.equal(users.readUser('USER-008', admin).role, 'HR')
})
test('permission validation restricts leaf codes, role scope, dependencies and protected Admin core', () => {
  const { roles } = setup(), hr = roles.readRole('HR', admin)
  assert.equal(validateRoleConfiguration(hr), '')
  for (const permissions of [['admin.users.view'], ['unknown'], ['hr.jobs'], ['hr.jobs.view', 'hr.jobs.view']]) assert.match(validateRoleConfiguration({ ...hr, permissions }), /valid menu/)
  assert.match(validateRoleConfiguration({ ...hr, permissions: ['hr.jobs.write'] }), /View permission/)
  assert.equal(validateRoleConfiguration({ ...hr, permissions: [] }), '')
  assert.match(validateRoleConfiguration({ ...hr, description: ' '.repeat(20) }), /description/)
  assert.match(validateRoleConfiguration({ ...hr, description: 'a'.repeat(501) }), /description/)
  assert.match(validateRoleConfiguration({ ...hr, name: 'Platform' }), /supported/)
  assert.match(validateRoleConfiguration({ ...roles.readRole('Admin', admin), permissions: [] }), /protected/)
  assert.ok(rolePermissionTree('Admin').every(group => group.disabled && group.children.every(row => row.disabled)))
})
test('search, module preview and saved configurations are independent copies', async () => {
  const { roles } = setup(), row = roles.readRole('HR', admin)
  assert.equal(filterAdminRoles(roles.listRoles(admin), ' RESEARCHER ')[0].name, 'Researcher')
  assert.deepEqual(permissionModules(['hr.jobs.view']), ['Job Management'])
  assert.deepEqual(permissionModules(['hr.jobs.write']), [])
  const form = { ...row, description: '  Limited HR demonstration.  ', permissions: ['hr.jobs.view'] }
  await roles.savePermissions(form, admin)
  form.permissions.push('hr.jobs.write')
  const saved = roles.readRole('HR', admin)
  assert.equal(saved.description, 'Limited HR demonstration.'); assert.deepEqual(saved.permissions, ['hr.jobs.view'])
})
test('stale and concurrent permission saves cannot overwrite a newer configuration', async () => {
  const { roles } = setup(), row = roles.readRole('HR', admin)
  const results = await Promise.allSettled([roles.savePermissions({ ...row, permissions: ['hr.jobs.view'] }, admin), roles.savePermissions({ ...row, permissions: [] }, admin)])
  assert.equal(results.filter(row => row.status === 'fulfilled').length, 1)
  assert.deepEqual(roles.readRole('HR', admin).permissions, ['hr.jobs.view'])
  await assert.rejects(roles.savePermissions(row, admin), /role changed/)
})
test('role assignment preserves account metadata, protects self/last Admin and rejects stale selections', async () => {
  const { roles, users } = setup(), before = users.readUser('USER-008', admin)
  await roles.assignRole(before.id, before.revision, 'Researcher', admin)
  const after = users.readUser(before.id, admin)
  for (const key of ['username', 'email', 'group', 'status', 'createdAt', 'lastLogin']) assert.equal(after[key], before[key])
  await assert.rejects(roles.assignRole(before.id, before.revision, 'HR', admin), /account changed/)
  await assert.rejects(roles.assignRole('USER-001', 0, 'HR', admin), /own Admin role/)
  await assert.rejects(roles.assignRole('USER-008', after.revision, 'Reviewer', admin), /no longer available/)
  users.users[0].status = 'Inactive'
  await assert.rejects(roles.assignRole('USER-011', 0, 'HR', admin), /at least one/)
})
test('empty/load/save/assignment failures preserve data for retry', async () => {
  const { roles, users } = setup(), before = JSON.stringify(roles.roles), accounts = JSON.stringify(users.users)
  assert.deepEqual(await roles.loadRoles(admin, 'empty'), [])
  await assert.rejects(roles.loadRoles(admin, 'load-error'), /Unable to load roles/)
  await assert.rejects(roles.savePermissions(roles.readRole('HR', admin), admin, 'save-error'), /selection has been kept/)
  await assert.rejects(roles.assignRole('USER-008', 0, 'HR', admin, 'assign-error'), /Unable to save/)
  assert.equal(JSON.stringify(roles.roles), before); assert.equal(JSON.stringify(users.users), accounts)
})
test('permissions and assignments do not change login roles or case grants; logout cancels pending saves', async () => {
  const { roles, users } = setup(), auth = useAuthStore(); auth.demoUser = admin
  const grants = { caseId: 'PRIVATE', allowed: true }; users.users[2].grants = grants
  await roles.assignRole('USER-003', 0, 'Researcher', admin)
  assert.deepEqual(users.users[2].grants, grants); assert.equal(auth.demoUser.role, 'Admin')
  const pending = roles.savePermissions({ ...roles.readRole('HR', admin), permissions: [] }, admin)
  auth.logout(); await assert.rejects(pending, /role changed/)
  assert.ok(roles.roles.find(row => row.name === 'HR').permissions.includes('hr.jobs.view'))
  assert.equal(users.users[2].role, 'HR')
})
