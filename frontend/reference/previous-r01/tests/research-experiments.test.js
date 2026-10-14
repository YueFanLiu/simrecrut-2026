import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createResearchExperiments, filterResearchExperiments, selectResearchExperiments } from '../src/mock/research-experiments.js'
import { useResearchExperimentsStore } from '../src/store/modules/research-experiments.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { loginDemo, registerDemo } from '../src/mock/auth.js'

const user = { username: 'researcher', role: 'Researcher' }

test('Researcher scope requires ownership or a live grant rather than a role alone', () => {
  const now = Date.now(), fixtures = createResearchExperiments(now)
  const rows = selectResearchExperiments(fixtures, user, now)
  assert.equal(rows.length, 7)
  assert.equal(rows.some(row => row.experimentId === 'experiment-003'), true)
  assert.equal(rows.some(row => ['ungranted-experiment', 'expired-experiment'].includes(row.experimentId)), false)
  assert.deepEqual(selectResearchExperiments(fixtures, { username: 'unrelated', role: 'Researcher' }, now), [])
  for (const role of ['Candidate', 'HR', 'Admin']) assert.throws(() => selectResearchExperiments(fixtures, { ...user, role }), /Researcher demo session/)
})

test('grant expiration, malformed deadlines and revocation remove a summary', () => {
  const now = Date.now(), fixtures = createResearchExperiments(now)
  const granted = fixtures.find(row => row.experimentId === 'experiment-003')
  for (const expiresAt of [new Date(now).toISOString(), 'invalid']) {
    granted.grants[0].expiresAt = expiresAt
    assert.equal(selectResearchExperiments(fixtures, user, now).some(row => row.experimentId === granted.experimentId), false)
  }
  granted.grants[0].expiresAt = null
  assert.equal(selectResearchExperiments(fixtures, user, now).length, 7)
  granted.grants = []
  assert.equal(selectResearchExperiments(fixtures, user, now).length, 6)
})

test('summary copies fixed versions while excluding creator, subjects, attributes, files and results', () => {
  const fixtures = createResearchExperiments()
  Object.assign(fixtures[0], { rawCv: 'Private content', subjects: [{ private: true }], researchAttributes: { private: true }, results: [82], modelPath: '/private/model' })
  const row = selectResearchExperiments(fixtures, user)[0]
  assert.deepEqual(Object.keys(row).sort(), ['experimentId', 'name', 'description', 'jobId', 'jobTitle', 'subjectCount', 'status', 'createdAt', 'versions'].sort())
  assert.deepEqual(Object.keys(row.versions).sort(), ['job', 'weights', 'protocol', 'neutral', 'biased'].sort())
  row.versions.job = 'Changed copy'
  assert.equal(fixtures[0].versions.job, '1.2')
  assert.equal(selectResearchExperiments(fixtures, user).find(row => row.status === 'Draft').subjectCount, 0)
})

test('combined local search/status/job filters and date sorting preserve source order', () => {
  const rows = selectResearchExperiments(createResearchExperiments(), user), before = JSON.stringify(rows)
  assert.equal(filterResearchExperiments(rows, { keyword: ' SCHOOL ', status: 'Completed', jobId: 'research-job-001' }).length, 1)
  assert.equal(filterResearchExperiments(rows, { keyword: 'experiment-006' })[0].name, 'Referral Comparison')
  assert.equal(filterResearchExperiments(rows, { keyword: 'fixed example' }).length, 7)
  assert.equal(filterResearchExperiments(rows, { status: 'Draft' }).length, 2)
  assert.deepEqual(filterResearchExperiments(rows, { jobId: 'restricted-job' }), [])
  assert.equal(filterResearchExperiments(rows, { sort: 'oldest' })[0].experimentId, 'experiment-001')
  assert.equal(filterResearchExperiments(rows)[0].experimentId, 'experiment-007')
  assert.equal(JSON.stringify(rows), before)
})

test('load scenarios are read-only and logout resets session experiment fixtures', async () => {
  setActivePinia(createPinia())
  const store = useResearchExperimentsStore(), auth = useAuthStore(), before = JSON.stringify(store.experiments)
  assert.equal((await store.loadExperiments(user)).length, 7)
  assert.deepEqual(await store.loadExperiments(user, 'empty'), [])
  await assert.rejects(store.loadExperiments(user, 'error'), /Unable to load experiments/)
  await assert.rejects(store.loadExperiments({ ...user, role: 'HR' }), /Researcher demo session/)
  assert.equal(JSON.stringify(store.experiments), before)
  store.experiments[0].name = 'Session change'
  auth.demoUser = user; auth.logout()
  assert.equal(auth.demoUser, null)
  assert.equal(store.experiments[0].name, 'School Prestige Comparison')
})

test('Researcher demo login accepts username/email and registration cannot shadow the reserved account', async () => {
  for (const identifier of ['researcher', 'RESEARCHER@simrecrut.test']) assert.equal((await loginDemo({ identifier, password: 'SimRecrut123!' })).role, 'Researcher')
  await assert.rejects(loginDemo({ identifier: 'researcher', password: 'wrong' }), /Invalid username or password/)
  await assert.rejects(registerDemo({ username: 'Researcher', email: 'unrelated@example.test', password: 'DemoPassword1!', agreedToTerms: true }), /username is already used/)
  await assert.rejects(registerDemo({ username: 'unrelated', email: 'researcher@simrecrut.test', password: 'DemoPassword1!', agreedToTerms: true }), /email is already used/)
})
