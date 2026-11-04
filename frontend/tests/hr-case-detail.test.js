import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useHrCasesStore } from '../src/store/modules/hr-cases.js'
import { createManagedJobs } from '../src/mock/hr-jobs.js'

const hr = { username: 'hr', role: 'HR' }
const jobs = createManagedJobs()
function freshStore() { setActivePinia(createPinia()); return useHrCasesStore() }

test('detail rechecks role, managed/granted scope, retention and privacy before projecting data', () => {
  const store = freshStore()
  assert.throws(() => store.readDetail({ username: 'demo', role: 'Candidate' }, jobs, 'hr-case-008'), /HR demo session/)
  for (const id of ['unknown', 'ungranted-case', 'expired-grant-case', 'expired-case', 'private-purging', 'private-deleted']) assert.throws(() => store.readDetail(hr, jobs, id), /unavailable/)
  assert.equal(store.readDetail(hr, jobs, 'hr-case-004').accessLabel, 'Explicit grant')
  const entry = store.cases.find((item) => item.caseId === 'hr-case-004')
  entry.grants[0].expiresAt = new Date(Date.now() - 1).toISOString()
  assert.throws(() => store.readDetail(hr, jobs, entry.caseId), /unavailable/)
})

test('professional and version projections omit identifiers, raw documents, research and model paths', () => {
  const store = freshStore()
  const entry = store.cases[0]
  Object.assign(entry.detail.professional, { name: 'Excluded', email: 'Excluded', researchAttributes: 'Excluded' })
  Object.assign(entry.detail.versions, { modelPath: '/private/model', cvPath: '/private/cv' })
  entry.rawCv = 'Excluded'
  const detail = store.readDetail(hr, jobs, entry.caseId)
  assert.equal(detail.professional.length, 5)
  assert.equal(JSON.stringify(detail).includes('Excluded'), false)
  assert.equal(JSON.stringify(detail).includes('/private'), false)
  detail.professional[0].value = 'Changed copy'
  detail.matching.rows[0].weight = 0
  assert.equal(store.readDetail(hr, jobs, entry.caseId).matching.rows[0].weight, 0.3)
  assert.equal(entry.detail.professional.Skills, 'Java, Python, SQL')
})

test('stored contributions and score scales stay separate and pending humans have no decisions', () => {
  const detail = freshStore().readDetail(hr, jobs, 'hr-case-008')
  assert.equal(detail.objective.score, 71.5)
  assert.deepEqual(detail.matching.rows.map((row) => row.contribution), [0.24, 0.25, 0.09, 0.075, 0.06])
  assert.equal(detail.neutral.score, 0.82)
  assert.equal(detail.biased.score, 0.46)
  assert.equal(detail.standard.status, 'Pending')
  assert.equal(detail.standard.decision, null)
})

test('unfinished and failed stages never fabricate scores, professional confirmation or rejection', () => {
  const store = freshStore()
  const review = store.readDetail(hr, jobs, 'hr-case-005')
  assert.equal(review.professional, null)
  assert.equal(review.matching, null)
  assert.equal(review.objective.score, null)
  const running = store.readDetail(hr, jobs, 'hr-case-006')
  assert.equal(running.objective.status, 'Complete')
  assert.equal(running.neutral.status, 'Running')
  assert.equal(running.neutral.decision, null)
  const failed = store.readDetail(hr, jobs, 'hr-case-003')
  assert.equal(failed.neutral.status, 'Failed')
  assert.equal(failed.neutral.score, null)
  assert.equal(failed.neutral.decision, null)
})

test('blind visibility removes result content at the data boundary, not merely with hidden UI', () => {
  const store = freshStore()
  const detail = store.readDetail(hr, jobs, 'hr-case-008', 'blind')
  assert.equal(detail.resultsVisible, false)
  for (const key of ['matching', 'objective', 'neutral', 'biased', 'standard', 'instructedBias']) assert.equal(key in detail, false)
  store.cases[0].detail.resultsVisible = false
  assert.equal('objective' in store.readDetail(hr, jobs, 'hr-case-008'), false)
})

test('invalid results stay unavailable and a mandatory disagreement does not rewrite an ML decision', () => {
  const store = freshStore()
  const entry = store.cases[0]
  entry.detail.neutral.score = NaN
  assert.equal(store.readDetail(hr, jobs, entry.caseId).neutral.status, 'Unavailable')
  entry.detail.neutral.score = 0.82
  entry.detail.matching.mandatory = 'Failed'
  entry.detail.objective.decision = 'Reject'
  const data = store.readDetail(hr, jobs, entry.caseId)
  assert.equal(data.objective.decision, 'Reject')
  assert.equal(data.neutral.decision, 'Accept')
  entry.detail.standard = { status: 'Submitted', decision: 'Reject', reason: '' }
  assert.equal(store.readDetail(hr, jobs, entry.caseId).standard.decision, null)
})
