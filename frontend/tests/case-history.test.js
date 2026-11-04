import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/store/modules/auth.js'
import { useCasesStore } from '../src/store/modules/cases.js'
import { getCaseDestination, selectCaseSummary, sortCaseSummaries } from '../src/utils/case-history.js'
import { createSimulationResults } from '../src/mock/simulation-results.js'

function fixture(status = 'AWAITING_UPLOAD', confirmed = false) {
  const entry = {
    caseId: 'demo-case-1', owner: 'demo', jobTitle: 'Software Engineer', version: '1.2',
    processingStatus: status, createdAt: '2026-10-10T08:00:00Z', uploadedAt: null,
    expiresAt: new Date(Date.now() + 100000).toISOString(), confirmedAt: confirmed ? new Date().toISOString() : null,
    assessment: { methods: ['Objective', 'Neutral ML', 'Biased ML'].map((name) => ({ name, status: 'Complete' })) },
  }
  entry.results = createSimulationResults(entry)
  return entry
}

function store() {
  setActivePinia(createPinia())
  useAuthStore().demoUser = { username: 'demo', role: 'Candidate' }
  return useCasesStore()
}

test('continue uses each retained state and distinguishes extraction from assessment failures', () => {
  const routes = {
    AWAITING_UPLOAD: 'CvUpload', UPLOADED: 'CvUpload', EXTRACTING: 'CvUpload',
    REVIEW_REQUIRED: 'CvReview', CONFIRMED: 'SimulationProgress',
    AUTOMATED_RUNNING: 'SimulationProgress', AUTOMATED_COMPLETE: 'SimulationResults',
    FAILED_RETRYABLE: 'CvUpload', FAILED_FINAL: 'CvUpload',
  }
  for (const [status, name] of Object.entries(routes)) {
    assert.equal(getCaseDestination(fixture(status)).name, name)
  }
  assert.equal(getCaseDestination(fixture('FAILED_RETRYABLE', true)).name, 'SimulationProgress')
  assert.equal(getCaseDestination(fixture('FAILED_FINAL', true)).name, 'SimulationProgress')
  assert.equal(getCaseDestination(fixture('PURGING')), null)
  assert.equal(getCaseDestination(fixture('UNKNOWN')), null)
})

test('history excludes other owners, expired and deleting cases; sign out clears the list', async () => {
  const cases = store()
  cases.cases = {
    visible: { ...fixture(), caseId: 'visible' },
    other: { ...fixture(), caseId: 'other', owner: 'other' },
    expired: { ...fixture(), caseId: 'expired', expiresAt: new Date(0).toISOString() },
    deleting: { ...fixture('PURGING'), caseId: 'deleting' },
    deleted: { ...fixture('DELETED'), caseId: 'deleted' },
  }
  assert.deepEqual((await cases.loadHistory()).map((entry) => entry.caseId), ['visible'])
  useAuthStore().logout()
  assert.deepEqual(cases.listOwnedCases(), [])
  await assert.rejects(cases.loadHistory(), /Sign in/)
})

test('unfinished or malformed results stay unavailable and no private fields enter history', () => {
  const entry = fixture('AUTOMATED_RUNNING', true)
  entry.results.humans.standard = { status: 'Submitted', decision: 'Reject', reason: 'Hidden until complete', reviewerName: 'Private' }
  entry.file = { name: 'private.pdf' }
  entry.reviewDraft = { private: true }
  entry.weights = { private: true }
  const summary = selectCaseSummary(entry)
  for (const key of ['objective', 'neutral', 'biased', 'standardHuman', 'instructedBiasHuman']) {
    assert.equal(summary[key], null)
  }
  for (const key of ['file', 'reviewDraft', 'weights', 'results', 'owner', 'confirmedAt']) {
    assert.equal(key in summary, false)
  }
  assert.equal(JSON.stringify(summary).includes('reviewerName'), false)
  entry.processingStatus = 'AUTOMATED_COMPLETE'
  entry.results.biased.acceptanceScore = NaN
  assert.equal(selectCaseSummary(entry).objective, null)
})

test('completed summaries preserve separate scales and nullable human decisions', () => {
  const entry = fixture('AUTOMATED_COMPLETE', true)
  const summary = selectCaseSummary(entry)
  assert.equal(summary.objective, 78)
  assert.equal(summary.neutral, 0.74)
  assert.equal(summary.biased, 0.41)
  assert.equal(summary.standardHuman, 'Pending')
  entry.results.humans.standard = { status: 'Not assigned', decision: 'Reject' }
  assert.equal(selectCaseSummary(entry).standardHuman, 'Not assigned')
  entry.results.humans.standard = { status: 'Submitted', decision: 'Accept', reason: 'Mock review' }
  assert.equal(selectCaseSummary(entry).standardHuman, 'Accept')
  assert.equal('reason' in selectCaseSummary(entry), false)
})

test('newest upload sorting falls back to creation time without changing source order', () => {
  const rows = [
    { caseId: 'demo-case-1', createdAt: '2026-10-10T08:00:00Z', uploadedAt: '2026-10-10T10:00:00Z' },
    { caseId: 'demo-case-2', createdAt: '2026-10-10T11:00:00Z', uploadedAt: null },
    { caseId: 'demo-case-3', createdAt: '2026-10-09T08:00:00Z', uploadedAt: '2026-10-10T09:00:00Z' },
  ]
  assert.deepEqual(sortCaseSummaries(rows).map((row) => row.caseId), ['demo-case-2', 'demo-case-1', 'demo-case-3'])
  assert.equal(rows[0].caseId, 'demo-case-1')
})

test('loading failures preserve cases and pending reads cannot restore a deleted case or signed-out list', async () => {
  const cases = store()
  cases.cases['demo-case-1'] = fixture()
  await assert.rejects(cases.loadHistory('error'), /Unable to load/)
  assert.ok(cases.getOwnedCase('demo-case-1'))
  const pending = cases.loadHistory()
  const deletion = cases.deleteSimulation('demo-case-1')
  assert.deepEqual(await pending, [])
  await deletion
  cases.cases['demo-case-1'] = fixture()
  const signedOut = cases.loadHistory()
  useAuthStore().logout()
  await assert.rejects(signedOut, /session has ended/)
  assert.deepEqual(cases.listOwnedCases(), [])
})
