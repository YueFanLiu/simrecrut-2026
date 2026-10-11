import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useResearchModuleStore } from '../src/store/modules/research-module.js'
import { useAuthStore } from '../src/store/modules/auth.js'

import { loginDemo, registerDemo } from '../src/mock/auth.js'

const user = { username: 'researcher', role: 'Researcher' }
const form = { name: 'Study', type: 'Single-factor paired', job: 'Software Engineer v3', weight: 'Balanced professional v1', protocol: 'P-2026.01', neutral: 'N-2026.01 / 1.0.0', biased: 'B-2026.01 / 1.0.0' }
function setup() { setActivePinia(createPinia()); return useResearchModuleStore() }

test('imported module projects authorised records and excludes expired, revoked and deleting subjects', () => {
  const store = setup()
  assert.equal(store.listExperiments(user).length, 4)
  assert.deepEqual(store.listExperiments({ ...user, username: 'unrelated' }), [])
  assert.deepEqual(store.listExperiments({ ...user, role: 'HR' }), [])
  assert.equal(store.listSubjects(user).length, 3)
  const row = store.subjects.find(row => row.id === 'SUB-003')
  row.grants[0].expiresAt = new Date().toISOString()
  assert.equal(store.listSubjects(user).length, 2)
  row.grants[0].expiresAt = null; row.privacy = 'PURGING'
  assert.equal(store.listSubjects(user).length, 2)
  Object.assign(store.experiments[0], { rawCv: 'private', candidateName: 'private', modelPath: '/private' })
  const projection = store.listExperiments(user)[0]
  assert.equal('owner' in projection, false); assert.equal('rawCv' in projection, false)
  projection.subjectIds.push('copy'); assert.equal(store.experiments[0].subjectIds.length, 0)
  assert.throws(() => store.readExperiment('missing', user), /unavailable/)
})

test('seven-field creation validates compatibility and freezes copied configuration without execution', () => {
  const store = setup()
  for (const key of Object.keys(form)) assert.throws(() => store.createExperiment({ ...form, [key]: '' }, user), /seven required/)
  assert.throws(() => store.createExperiment({ ...form, protocol: 'unknown' }, user), /compatible/)
  assert.throws(() => store.createExperiment({ ...form, neutral: 'N-2026.02 / 1.1.0' }, user), /compatible active/)
  const input = { ...form }, id = store.createExperiment(input, user); input.name = 'mutated'
  assert.equal(store.readExperiment(id, user).name, 'Study')
  assert.equal(store.readExperiment(id, user).status, 'Draft')
  assert.equal(store.readExperiment(id, user).subjects, 0)
  assert.throws(() => store.createExperiment(form, { ...user, role: 'Candidate' }), /Researcher/)
})

test('subject saves are deduplicated and recheck current grants rather than trusting checkboxes', () => {
  const store = setup(), id = store.createExperiment(form, user)
  store.saveSubjects(id, ['SUB-001', 'SUB-001'], user)
  assert.equal(store.readExperiment(id, user).subjects, 1)
  assert.throws(() => store.saveSubjects(id, ['SUB-004'], user), /eligible/)
  assert.throws(() => store.saveSubjects('EXP-001', ['SUB-001'], user), /draft/)
  store.subjects[0].privacy = 'DELETE_REQUESTED'
  assert.throws(() => store.saveSubjects(id, ['SUB-001'], user), /eligible/)
})

test('professional projection excludes extra private fields and linked pairs disappear after revocation', () => {
  const store = setup(), id = store.createExperiment(form, user)
  store.subjects[0].professional.candidateName = 'Private name'
  assert.equal('candidateName' in store.listSubjects(user)[0].professional, false)
  store.saveSubjects(id, ['SUB-001'], user); store.createVariants(id, 'Gender', user)
  store.assignReviews(id, 'reviewer-hr', 'Standard', user)
  assert.equal(store.listExperiments(user).find(row => row.id === id).pairs.length, 1)
  store.subjects[0].privacy = 'PURGING'
  const projection = store.listExperiments(user).find(row => row.id === id)
  assert.equal(projection.pairs.length, 0); assert.equal(projection.reviews.length, 0)
  assert.equal(projection.subjectIds.length, 0)
})

test('single-factor variants preserve professional blocks and reject unsupported factors and invalid pairs', async () => {
  const store = setup(), id = store.createExperiment(form, user)
  assert.throws(() => store.createVariants(id, 'Gender', user), /Save eligible/)
  store.saveSubjects(id, ['SUB-001'], user)
  assert.throws(() => store.createVariants(id, 'Age', user), /supported/)
  store.createVariants(id, 'Gender', user)
  const row = store.readExperiment(id, user), pair = row.pairs[0]
  assert.deepEqual(pair.professional, pair.variantProfessional)
  pair.variantProfessional.skills = 'Changed'
  await assert.rejects(store.runExperiment(id, user), /identical professional/)
  assert.equal(row.status, 'Draft')
})

test('demo execution prevents duplicate runs, produces synthetic outputs and locks finished configuration', async () => {
  const store = setup(), id = store.createExperiment(form, user)
  store.saveSubjects(id, ['SUB-001'], user); store.createVariants(id, 'Referral', user)
  const running = store.runExperiment(id, user)
  assert.equal(store.readExperiment(id, user).status, 'Running')
  await assert.rejects(store.runExperiment(id, user), /draft/)
  await running
  const row = store.readExperiment(id, user)
  assert.equal(row.status, 'Completed'); assert.equal('objectiveScore' in row, false)
  assert.match(row.execution, /illustrative fixtures/)
  assert.equal(row.outputs.length, 2)
  assert.throws(() => store.saveSubjects(id, ['SUB-001'], user), /draft/)
})

test('revocation during execution fails and logout cannot resurrect a pending experiment', async () => {
  const store = setup(), id = store.createExperiment({ ...form, type: 'Baseline' }, user)
  store.saveSubjects(id, ['SUB-003'], user)
  const running = store.runExperiment(id, user)
  store.subjects.find(row => row.id === 'SUB-003').grants = []
  await running
  assert.equal(store.readExperiment(id, user).status, 'Failed')
  const second = store.createExperiment({ ...form, type: 'Baseline' }, user)
  store.saveSubjects(second, ['SUB-001'], user)
  const pending = store.runExperiment(second, user)
  const auth = useAuthStore(); auth.demoUser = user; auth.logout()
  await pending
  assert.equal(store.experiments.some(row => row.id === second), false)
})

test('review assignments recheck scope, are idempotent and keep Research separate from Primary', () => {
  const store = setup(), id = store.createExperiment(form, user)
  store.saveSubjects(id, ['SUB-001'], user); store.createVariants(id, 'Gender', user)
  store.assignReviews(id, 'reviewer-hr', 'Standard', user)
  store.assignReviews(id, 'reviewer-hr', 'Standard', user)
  assert.equal(store.readExperiment(id, user).reviews.length, 2)
  for (const row of store.readExperiment(id, user).reviews) { assert.equal(row.purpose, 'Research'); assert.equal(row.status, 'Pending'); assert.equal('decision' in row, false) }
  assert.throws(() => store.assignReviews(id, 'unknown', 'Standard', user), /eligible/)
  assert.equal(store.assignPrimaryReview('SUB-003', 'reviewer-hr', 'Standard', user), 1)
  assert.equal(store.assignPrimaryReview('SUB-003', 'reviewer-hr', 'Standard', user), 1)
  assert.throws(() => store.assignPrimaryReview('SUB-001', 'reviewer-hr', 'Standard', user), /Candidate case/)
})

test('Researcher login and reserved registration remain compatible with imported pages', async () => {
  for (const identifier of ['researcher', 'RESEARCHER@simrecrut.test']) assert.equal((await loginDemo({ identifier, password: 'SimRecrut123!' })).role, 'Researcher')
  await assert.rejects(registerDemo({ username: 'researcher', email: 'unrelated@example.test', password: 'DemoPassword1!', agreedToTerms: true }), /username is already used/)
  await assert.rejects(registerDemo({ username: 'unrelated', email: 'researcher@simrecrut.test', password: 'DemoPassword1!', agreedToTerms: true }), /email is already used/)
})
