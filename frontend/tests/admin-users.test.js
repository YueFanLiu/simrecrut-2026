import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { loginDemo, registerDemo } from '../src/mock/auth.js'
import { accountProjection, filterAdminUsers, validateAccount } from '../src/mock/admin-users.js'
import { useAdminUsersStore } from '../src/store/modules/admin-users.js'
import { useAuthStore } from '../src/store/modules/auth.js'
const admin = { username: 'admin', role: 'Admin' }
const form = { username: 'new_user', displayName: 'New User', email: 'new_user@simrecrut.test', role: 'HR', group: 'Company A' }
function setup() { setActivePinia(createPinia()); return useAdminUsersStore() }
test('Admin login is reserved; the directory never creates login credentials', async () => {
  const store = setup()
  assert.equal((await loginDemo({ identifier: ' ADMIN@simrecrut.test ', password: 'SimRecrut123!' })).role, 'Admin')
  await assert.rejects(registerDemo({ username: 'admin', email: 'other@simrecrut.test', password: 'SimRecrut123!', agreedToTerms: true }), /already used/)
  await assert.rejects(registerDemo({ username: 'another_admin', email: 'admin@simrecrut.test', password: 'SimRecrut123!', agreedToTerms: true }), /already used/)
  await store.saveUser(form, admin)
  await assert.rejects(loginDemo({ identifier: form.username, password: 'SimRecrut123!' }), /Invalid username/)
})
test('account reads and mutations require Admin and project necessary metadata only', async () => {
  const store = setup()
  for (const role of ['Candidate', 'HR', 'Researcher', 'Reviewer']) {
    const user = { ...admin, role }
    assert.throws(() => store.listUsers(user), /Admin/)
    assert.throws(() => store.readUser('USER-001', user), /Admin/)
    await assert.rejects(store.saveUser(form, user), /Admin/)
    await assert.rejects(store.changeStatus('USER-003', 0, 'Inactive', user), /Admin/)
  }
  Object.assign(store.users[0], { password: 'PRIVATE', cv: 'PRIVATE', grants: 'PRIVATE', humanReason: 'PRIVATE' })
  const row = store.listUsers(admin)[0]
  assert.equal(JSON.stringify(row).includes('PRIVATE'), false)
  assert.deepEqual(Object.keys(row).sort(), ['id', 'username', 'displayName', 'email', 'role', 'group', 'status', 'createdAt', 'lastLogin', 'revision'].sort())
  assert.deepEqual(accountProjection(store.users[0]), row)
  row.displayName = 'Changed copy'; assert.equal(store.users[0].displayName, 'Demo Admin User')
  assert.throws(() => store.readUser('missing', admin), /no longer available/)
})
test('combined filters are case insensitive and leave source ordering unchanged', () => {
  const store = setup(), before = JSON.stringify(store.users)
  assert.equal(filterAdminUsers(store.listUsers(admin), { keyword: ' TAYLOR ', role: 'HR', group: 'Company B', status: 'Inactive' }).length, 1)
  assert.equal(filterAdminUsers(store.listUsers(admin), { keyword: '@simrecrut.test', role: 'Candidate' }).length, 3)
  assert.equal(filterAdminUsers(store.listUsers(admin), { role: 'Reviewer' }).length, 0)
  assert.equal(JSON.stringify(store.users), before)
})
test('validation rejects missing/invalid fields, unsupported roles/groups and duplicate identity', () => {
  const store = setup()
  assert.deepEqual(validateAccount(form, store.users), {})
  assert.equal(Object.keys(validateAccount({ username: '', displayName: '', email: '', role: 'Reviewer', group: 'Unknown' }, store.users)).length, 5)
  assert.ok(validateAccount({ ...form, username: 'HR', email: ' HR@SIMRECRUT.TEST ' }, store.users).username)
  assert.ok(validateAccount({ ...form, email: 'HR@SIMRECRUT.TEST' }, store.users).email)
  assert.ok(validateAccount({ ...form, username: 'a'.repeat(21), displayName: 'a'.repeat(61), email: 'bad' }, store.users).email)
  assert.deepEqual(validateAccount(store.users[0], store.users, store.users[0].id), {})
  assert.deepEqual(validateAccount(store.users[2], store.users, store.users[2].id), {})
  assert.ok(validateAccount({ ...form, username: 'ab' }, store.users).username)
})
test('create and edit use normalized copies; existing usernames stay immutable and stale edits fail', async () => {
  const store = setup(), input = { ...form, displayName: ' New User ', email: ' NEW_USER@SIMRECRUT.TEST ' }
  await store.saveUser(input, admin)
  const created = store.listUsers(admin)[0]
  assert.equal(created.displayName, 'New User'); assert.equal(created.email, 'new_user@simrecrut.test'); assert.equal(created.lastLogin, null)
  input.displayName = 'Mutated source'; assert.equal(created.displayName, 'New User')
  await store.saveUser({ ...created, displayName: 'Edited User', group: 'Company B' }, admin)
  await assert.rejects(store.saveUser({ ...created, displayName: 'Stale' }, admin), /account changed/)
  const fresh = store.readUser(created.id, admin)
  await assert.rejects(store.saveUser({ ...fresh, username: 'renamed_user' }, admin), /username cannot be changed/)
  assert.equal(store.readUser(created.id, admin).displayName, 'Edited User')
})
test('status changes preserve metadata; self and last-active Admin protections apply to both operations', async () => {
  const store = setup(), before = store.readUser('USER-003', admin)
  await store.changeStatus(before.id, before.revision, 'Inactive', admin)
  const after = store.readUser(before.id, admin)
  assert.equal(after.status, 'Inactive'); assert.equal(after.email, before.email); assert.equal(after.createdAt, before.createdAt)
  await store.changeStatus(after.id, after.revision, 'Active', admin)
  await assert.rejects(store.changeStatus('USER-001', 0, 'Inactive', admin), /own account/)
  await assert.rejects(store.saveUser({ ...store.readUser('USER-001', admin), role: 'HR' }, admin), /own Admin role/)
  const otherAdmin = { username: 'admin', role: 'Admin' }
  store.users[0].status = 'Inactive'
  const last = store.readUser('USER-011', admin)
  await assert.rejects(store.changeStatus(last.id, last.revision, 'Inactive', otherAdmin), /at least one/)
  await assert.rejects(store.saveUser({ ...last, role: 'HR' }, otherAdmin), /at least one/)
})
test('duplicate in-flight creation and status changes cannot overwrite existing rows', async () => {
  const store = setup()
  const results = await Promise.allSettled([store.saveUser(form, admin), store.saveUser({ ...form, displayName: 'Different' }, admin)])
  assert.equal(results.filter(row => row.status === 'fulfilled').length, 1)
  assert.equal(store.users.filter(row => row.username === form.username).length, 1)
  const original = store.readUser('USER-003', admin)
  const statuses = await Promise.allSettled([store.changeStatus(original.id, original.revision, 'Inactive', admin), store.changeStatus(original.id, original.revision, 'Active', admin)])
  assert.equal(statuses.filter(row => row.status === 'fulfilled').length, 1)
  assert.equal(store.readUser(original.id, admin).status, 'Inactive')
})
test('load/save/status errors preserve data and reset blocks pending writes', async () => {
  const store = setup(), before = JSON.stringify(store.users)
  assert.deepEqual(await store.loadUsers(admin, 'empty'), [])
  await assert.rejects(store.loadUsers(admin, 'load-error'), /Unable to load/)
  await assert.rejects(store.saveUser(form, admin, 'save-error'), /input has been kept/)
  await assert.rejects(store.changeStatus('USER-003', 0, 'Inactive', admin, 'status-error'), /Unable to change/)
  assert.equal(JSON.stringify(store.users), before)
  const pending = store.saveUser(form, admin), auth = useAuthStore(); auth.demoUser = admin; auth.logout()
  await assert.rejects(pending, /session changed/)
  assert.equal(store.users.length, 12); assert.equal(auth.demoUser, null)
})
