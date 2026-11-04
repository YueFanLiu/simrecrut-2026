import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/store/modules/auth.js'
import { useCasesStore } from '../src/store/modules/cases.js'
import { loadJobDetail } from '../src/mock/job-details.js'
import { createSimulationResults, selectCandidateResults } from '../src/mock/simulation-results.js'

async function completedCase() {
  setActivePinia(createPinia())
  useAuthStore().demoUser = { username: 'demo', role: 'Candidate' }
  const cases = useCasesStore()
  const caseId = await cases.createCase(await loadJobDetail('software-engineer'))
  const entry = cases.getOwnedCase(caseId)
  // C05 timing/state transitions are covered by simulation.test.js.
  entry.confirmedAt = new Date().toISOString()
  entry.processingStatus = 'AUTOMATED_COMPLETE'
  entry.assessment = { methods: ['Objective', 'Neutral ML', 'Biased ML'].map((name) => ({ name, status: 'Complete' })) }
  entry.results = createSimulationResults(entry)
  return { cases, caseId, entry }
}

test('public result projection copies visible fields and excludes private data on every method', async () => {
  const { entry } = await completedCase()
  entry.jobSummary.weights = { skills: 0.5 }
  entry.results.objective.cutoff = 0.6
  entry.results.objective.contributions = [0.4]
  entry.results.neutral.logit = 1.2
  entry.results.neutral.attributeSummary = { gender: 'Excluded' }
  entry.results.biased.modelPath = '/private/model.bin'
  entry.results.humans.standard.reviewerName = 'Hidden reviewer'
  const data = selectCandidateResults(entry)
  for (const field of ['weights', 'cutoff', 'contributions', 'logit', 'modelPath', 'reviewerName', 'attributeSummary']) {
    assert.equal(JSON.stringify(data).includes(field), false)
  }
  assert.equal(data.job.jobVersionId, entry.jobVersionId)
  assert.equal(data.objective.displayScore, 78)
  assert.equal(data.neutral.acceptanceScore, 0.74)
  data.objective.strengths.push('Changed copy')
  data.job.title = 'Changed copy'
  assert.equal(entry.results.objective.strengths.includes('Changed copy'), false)
  assert.equal(entry.jobSummary.title, 'Software Engineer')
})

test('missing, nonfinite or out-of-range automated outputs never become a zero or Reject fallback', async () => {
  const { entry } = await completedCase()
  const original = JSON.parse(JSON.stringify(entry.results))
  for (const value of [null, NaN, Infinity, -0.1, 1.1]) {
    entry.results = structuredClone(original)
    entry.results.neutral.acceptanceScore = value
    assert.throws(() => selectCandidateResults(entry), /incomplete/)
  }
  entry.results = structuredClone(original)
  entry.results.objective.displayScore = 101
  assert.throws(() => selectCandidateResults(entry), /incomplete/)
  entry.results = structuredClone(original)
  delete entry.results.biased
  assert.throws(() => selectCandidateResults(entry), /incomplete/)
})

test('pending/unassigned humans have no decision; submitted primary reviews have a reason', async () => {
  const { cases, caseId, entry } = await completedCase()
  const initial = await cases.loadResults(caseId, 'unassigned')
  assert.deepEqual(initial.humans.standard, { status: 'Not assigned', decision: null, reason: null })
  entry.results.humans.standard = { status: 'Pending', decision: 'Reject', reason: 'Must remain hidden' }
  assert.deepEqual(selectCandidateResults(entry).humans.standard, { status: 'Pending', decision: null, reason: null })
  entry.results.humanInitialized = false
  const submitted = await cases.loadResults(caseId, 'submitted')
  assert.equal(submitted.humans.standard.decision, 'Accept')
  assert.equal(submitted.humans.instructedBias.decision, 'Reject')
  assert.ok(submitted.humans.standard.reason)
  entry.results.humans.standard.reason = ''
  assert.throws(() => selectCandidateResults(entry), /missing its decision or reason/)
})

test('refresh updates only the permitted human slot, while a failed refresh preserves prior outputs', async () => {
  const { cases, caseId, entry } = await completedCase()
  const initial = await cases.loadResults(caseId, 'human-update')
  assert.equal(initial.humans.standard.status, 'Pending')
  const refreshed = await cases.loadResults(caseId, 'human-update', true)
  assert.equal(refreshed.humans.standard.status, 'Submitted')
  assert.equal(refreshed.humans.instructedBias.status, 'Pending')
  assert.deepEqual(refreshed.objective, initial.objective)
  const snapshot = JSON.stringify(entry.results)
  await assert.rejects(cases.loadResults(caseId, 'refresh-error', true), /Unable to refresh/)
  assert.equal(JSON.stringify(entry.results), snapshot)
})

test('research summary is permitted only for Biased and contains explicit allowed keys', async () => {
  const { entry } = await completedCase()
  entry.researchAllowed = true
  entry.researchAttributes = { schoolPrestige: 'UNKNOWN', referral: 'UNKNOWN', gender: 'UNKNOWN', ethnicity: 'UNKNOWN', rawText: 'Excluded' }
  entry.results = createSimulationResults(entry)
  const data = selectCandidateResults(entry)
  assert.equal('attributeSummary' in data.neutral, false)
  assert.deepEqual(Object.keys(data.biased.attributeSummary), ['schoolPrestige', 'referral', 'gender', 'ethnicity'])
  assert.equal(data.biased.attributeSummary.gender, 'UNKNOWN')
  entry.researchAllowed = false
  assert.equal('attributeSummary' in selectCandidateResults(entry).biased, false)
})

test('unfinished, foreign and expired cases cannot load results', async () => {
  const { cases, caseId, entry } = await completedCase()
  entry.assessment.methods[2].status = 'Waiting'
  await assert.rejects(cases.loadResults(caseId), /not ready/)
  entry.assessment.methods[2].status = 'Complete'
  entry.processingStatus = 'FAILED_RETRYABLE'
  await assert.rejects(cases.loadResults(caseId), /not ready/)
  entry.processingStatus = 'AUTOMATED_COMPLETE'
  useAuthStore().demoUser.username = 'other'
  await assert.rejects(cases.loadResults(caseId), /not ready/)
  useAuthStore().demoUser.username = 'demo'
  entry.expiresAt = new Date(0).toISOString()
  await assert.rejects(cases.loadResults(caseId), /not ready/)
})

test('deletion revokes reads immediately, prevents pending reloads and removes all case data', async () => {
  const { cases, caseId } = await completedCase()
  await assert.rejects(cases.deleteSimulation(caseId, 'delete-error'), /Nothing was deleted/)
  assert.ok(cases.getOwnedCase(caseId))
  const reading = cases.loadResults(caseId)
  const deleted = cases.deleteSimulation(caseId)
  assert.equal(cases.getOwnedCase(caseId), null)
  await assert.rejects(reading, /no longer available/)
  await deleted
  assert.equal(cases.cases[caseId], undefined)
  await assert.rejects(cases.loadResults(caseId), /not ready/)
})
