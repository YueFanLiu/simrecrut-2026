import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createHrCases, filterHrCases, selectHrCaseSummaries } from '../src/mock/hr-cases.js'
import { createManagedJobs } from '../src/mock/hr-jobs.js'
import { useHrCasesStore } from '../src/store/modules/hr-cases.js'
import { useAuthStore } from '../src/store/modules/auth.js'

const hr = { username: 'hr', role: 'HR' }

test('HR case scope combines managed jobs and live explicit grants without granting access by role alone', () => {
  const now = Date.now()
  const cases = createHrCases(now)
  const rows = selectHrCaseSummaries(cases, hr, createManagedJobs(), now)
  assert.equal(rows.length, 8)
  assert.equal(rows.find((row) => row.caseId === 'hr-case-004').accessLabel, 'Explicit grant')
  assert.equal(rows.some((row) => row.caseId === 'ungranted-case'), false)
  assert.deepEqual(selectHrCaseSummaries(cases, { username: 'another-hr', role: 'HR' }, createManagedJobs(), now), [])
  assert.throws(() => selectHrCaseSummaries(cases, { username: 'demo', role: 'Candidate' }, createManagedJobs()), /HR demo session/)
})

test('expiry, expired grants and every inactive privacy state remove cases including for managed jobs', () => {
  const now = Date.now()
  const cases = createHrCases(now)
  assert.equal(selectHrCaseSummaries(cases, hr, createManagedJobs(), now).some((row) => !row.caseId.startsWith('hr-case-')), false)
  const grant = cases.find((row) => row.caseId === 'hr-case-004')
  grant.grants[0].expiresAt = new Date(now).toISOString()
  assert.equal(selectHrCaseSummaries(cases, hr, createManagedJobs(), now).some((row) => row.caseId === grant.caseId), false)
  const entry = cases[0]
  entry.purgeAt = new Date(now).toISOString()
  assert.equal(selectHrCaseSummaries(cases, hr, createManagedJobs(), now).some((row) => row.caseId === entry.caseId), false)
  for (const privacyState of ['DELETE_REQUESTED', 'PURGING', 'DELETED', 'UNKNOWN']) {
    entry.purgeAt = null
    entry.privacyState = privacyState
    assert.equal(selectHrCaseSummaries(cases, hr, createManagedJobs(), now).some((row) => row.caseId === entry.caseId), false)
  }
})

test('summary projection omits Candidate identifiers, content and scores and copies human status', () => {
  const cases = createHrCases()
  Object.assign(cases[0], { candidateName: 'Private Name', email: 'private@example.test', rawCv: 'Private file',
    professionalSnapshot: { private: true }, objective: 82, researchAttributes: { gender: 'Private' } })
  const row = selectHrCaseSummaries(cases, hr, createManagedJobs())[0]
  for (const field of ['candidateName', 'email', 'rawCv', 'professionalSnapshot', 'objective', 'researchAttributes', 'grants']) assert.equal(field in row, false)
  assert.deepEqual(row.humanStatus, { standard: 'Pending', instructedBias: 'Pending' })
  row.humanStatus.standard = 'Changed copy'
  assert.equal(cases[0].humanStatus.standard, 'Pending')
  const failed = selectHrCaseSummaries(cases, hr, createManagedJobs()).find((item) => item.caseId === 'hr-case-003')
  assert.equal(failed.processingLabel, 'Failed (retryable)')
  assert.equal('decision' in failed, false)
})

test('local keyword and job filters combine, sort deterministically and do not alter their source', () => {
  const rows = selectHrCaseSummaries(createHrCases(), hr, createManagedJobs())
  const before = JSON.stringify(rows)
  assert.equal(filterHrCases(rows, { keyword: ' software ', jobId: 'hr-job-004' }).length, 3)
  assert.equal(filterHrCases(rows, { keyword: 'hr-case-007' })[0].jobTitle, 'Data Analyst')
  assert.deepEqual(filterHrCases(rows, { jobId: 'unmanaged-and-ungranted' }), [])
  assert.equal(filterHrCases(rows, { sort: 'oldest' })[0].caseId, 'hr-case-001')
  assert.equal(filterHrCases(rows, { sort: 'latest' })[0].caseId, 'hr-case-008')
  assert.equal(JSON.stringify(rows), before)
})

test('mock loader rejects Candidates and exposes English error/empty states; logout restores fixtures', async () => {
  setActivePinia(createPinia())
  const store = useHrCasesStore()
  await assert.rejects(store.loadCases({ username: 'demo', role: 'Candidate' }, createManagedJobs()), /HR demo session/)
  assert.deepEqual(await store.loadCases(hr, createManagedJobs(), 'empty'), [])
  await assert.rejects(store.loadCases(hr, createManagedJobs(), 'error'), /Unable to load authorised cases/)
  assert.equal((await store.loadCases(hr, createManagedJobs())).length, 8)
  store.cases[0].humanStatus.standard = 'Submitted'
  useAuthStore().logout()
  assert.equal(store.cases[0].humanStatus.standard, 'Pending')
})
