import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAdminModelsStore } from '../src/store/modules/admin-models.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { useResearchModuleStore } from '../src/store/modules/research-module.js'
import { checkModelPackage, checkRegistry, filterAdminModels, offlinePackages } from '../src/mock/admin-models.js'
const admin = { username: 'admin', role: 'Admin' }
function setup() { setActivePinia(createPinia()); return useAdminModelsStore() }
test('model metadata is Admin-only, explicitly projected and never fabricates evaluation values', async () => {
  const store = setup()
  for (const role of ['Candidate', 'HR', 'Researcher', 'Reviewer']) {
    const user = { username: 'other', role }
    assert.throws(() => store.listModels(user), /Admin/)
    await assert.rejects(store.registerPackage('N-2026.04', user), /Admin/)
    await assert.rejects(store.release('N-2026.03', 0, 'approve', null, user), /Admin/)
  }
  Object.assign(store.models[0], { artifactPath: 'PRIVATE', weights: 'PRIVATE', cv: 'PRIVATE', humanReason: 'PRIVATE', accuracy: .99 })
  const row = store.readModel('N-2026.01', admin)
  assert.equal(JSON.stringify(row).includes('PRIVATE'), false); assert.equal('accuracy' in row, false)
  assert.match(row.evaluation, /Unavailable/); assert.equal(row.architecture, '32 → 16 → 8 → 1'); assert.equal(row.parameters, 673)
  row.checks.integrity = false; assert.equal(store.models[0].evidence.integrity, true)
})
test('combined model filters preserve registry ordering and exact lifecycle states', () => {
  const store = setup(), before = JSON.stringify(store.models)
  assert.equal(filterAdminModels(store.listModels(admin), { keyword: ' 1.1.0 ', type: 'Biased ML', status: 'APPROVED' }).length, 1)
  assert.equal(filterAdminModels(store.listModels(admin), { status: 'Released' }).length, 0)
  assert.equal(JSON.stringify(store.models), before)
  checkRegistry(store.models, store.slots)
})
test('registration uses checked offline fixture metadata and concurrent duplicate calls create one TESTED record', async () => {
  const store = setup()
  await assert.rejects(store.registerPackage('arbitrary-file', admin), /predefined/)
  const results = await Promise.allSettled([store.registerPackage('N-2026.04', admin), store.registerPackage('N-2026.04', admin)])
  assert.equal(results.filter(row => row.status === 'fulfilled').length, 1)
  assert.equal(store.models.filter(row => row.id === 'N-2026.04').length, 1)
  assert.equal(store.readModel('N-2026.04', admin).status, 'TESTED'); assert.equal(store.slots['Neutral ML'], 'N-2026.01')
})
test('approval requires complete recorded evidence; activation separately validates compatibility and readiness', async () => {
  const store = setup(), row = store.models.find(row => row.id === 'N-2026.03')
  row.evidence.finalTest = false
  await assert.rejects(store.release(row.id, row.revision, 'approve', null, admin), /incomplete/)
  row.evidence.finalTest = true
  await store.release(row.id, row.revision, 'approve', null, admin)
  assert.equal(row.status, 'APPROVED'); assert.equal(store.slots['Neutral ML'], 'N-2026.01')
  row.demoReady = false
  await assert.rejects(store.release(row.id, row.revision, 'activate', null, admin), /readiness/)
  row.demoReady = true; row.schema = 'invalid'
  await assert.rejects(store.release(row.id, row.revision, 'activate', null, admin), /incompatible/)
  assert.equal(store.slots['Neutral ML'], 'N-2026.01')
  const pkg = offlinePackages()[0]; pkg.checksum = 'bad'; assert.match(checkModelPackage(pkg), /incomplete/)
})
test('activation replaces one same-type slot atomically while retaining historical versions and Research fixtures', async () => {
  const store = setup(), research = useResearchModuleStore(), before = JSON.stringify(research.$state)
  const original = store.readModel('N-2026.01', admin)
  await store.release('N-2026.02', 0, 'activate', null, admin)
  assert.equal(store.slots['Neutral ML'], 'N-2026.02'); assert.equal(store.readModel(original.id, admin).status, 'RETIRED')
  assert.equal(store.readModel(original.id, admin).version, original.version); assert.equal(store.models.length, 7)
  assert.equal(store.slots['Biased ML'], 'B-2026.01'); assert.equal(JSON.stringify(research.$state), before)
  checkRegistry(store.models, store.slots)
  await assert.rejects(store.release('N-2026.03', 0, 'activate', null, admin), /APPROVED/)
})
test('retirement requires an APPROVED compatible replacement and cannot empty an active core slot', async () => {
  const store = setup()
  for (const replacement of [null, 'N-2026.03', 'B-2026.02']) await assert.rejects(store.release('N-2026.01', 0, 'retire', replacement, admin), /replacement/)
  await store.release('N-2026.01', 0, 'retire', 'N-2026.02', admin)
  assert.equal(store.readModel('N-2026.01', admin).status, 'RETIRED'); assert.equal(store.slots['Neutral ML'], 'N-2026.02')
  checkRegistry(store.models, store.slots)
  await assert.rejects(store.release('N-2025.12', 0, 'retire', 'N-2026.03', admin), /current ACTIVE/)
})
test('concurrent releases, changing readiness and inconsistent slots cannot corrupt registry state', async () => {
  const store = setup()
  await store.release('N-2026.03', 0, 'approve', null, admin)
  const results = await Promise.allSettled([store.release('N-2026.02', 0, 'activate', null, admin), store.release('N-2026.03', 1, 'activate', null, admin)])
  assert.equal(results.filter(row => row.status === 'fulfilled').length, 1); checkRegistry(store.models, store.slots)
  const pending = store.release('B-2026.02', 0, 'activate', null, admin)
  store.models.find(row => row.id === 'B-2026.02').demoReady = false
  await assert.rejects(pending, /readiness/); assert.equal(store.slots['Biased ML'], 'B-2026.01')
  store.slots['Biased ML'] = 'missing'
  await assert.rejects(store.release('B-2026.03', 0, 'approve', null, admin), /inconsistent/)
})
test('load/register/release failures and logout preserve or restore fixtures without late writes', async () => {
  const store = setup(), before = JSON.stringify(store.$state)
  assert.deepEqual(await store.loadModels(admin, 'empty'), [])
  await assert.rejects(store.loadModels(admin, 'load-error'), /Unable to load/)
  await assert.rejects(store.registerPackage('N-2026.04', admin, 'register-error'), /Unable to register/)
  await assert.rejects(store.release('N-2026.02', 0, 'activate', null, admin, 'release-error'), /kept unchanged/)
  assert.equal(JSON.stringify(store.$state), before)
  const pending = store.release('N-2026.02', 0, 'activate', null, admin), auth = useAuthStore(); auth.demoUser = admin; auth.logout()
  await assert.rejects(pending, /release state changed/)
  assert.equal(store.slots['Neutral ML'], 'N-2026.01')
})
