import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/store/modules/auth.js'
import { useCasesStore } from '../src/store/modules/cases.js'
import { loadJobDetail } from '../src/mock/job-details.js'
import { createReviewDraft } from '../src/mock/review-draft.js'

async function confirmedCase() {
  setActivePinia(createPinia())
  useAuthStore().demoUser = { username: 'demo', role: 'Candidate' }
  const cases = useCasesStore()
  const caseId = await cases.createCase(await loadJobDetail('software-engineer'))
  cases.cases[caseId].processingStatus = 'REVIEW_REQUIRED'
  await cases.confirmReview(caseId, createReviewDraft(), true)
  return { cases, caseId, entry: cases.getOwnedCase(caseId) }
}

test('unconfirmed and expired cases cannot run', async () => {
  const { cases, caseId, entry } = await confirmedCase()
  entry.processingStatus = 'REVIEW_REQUIRED'
  entry.confirmedAt = null
  await cases.runSimulation(caseId)
  assert.equal(entry.assessment, undefined)
  entry.confirmedAt = new Date().toISOString()
  entry.processingStatus = 'CONFIRMED'
  entry.expiresAt = new Date(0).toISOString()
  await cases.runSimulation(caseId)
  assert.equal(entry.assessment, undefined)
})

test('a duplicate run is ignored and all three methods must complete without scores', async () => {
  const { cases, caseId, entry } = await confirmedCase()
  const facts = JSON.stringify(entry.confirmedProfessional)
  await Promise.all([cases.runSimulation(caseId), cases.runSimulation(caseId)])
  assert.equal(entry.processingStatus, 'AUTOMATED_COMPLETE')
  assert.deepEqual(entry.assessment.methods.map((method) => method.status), ['Complete', 'Complete', 'Complete'])
  assert.equal(entry.assessment.events.filter((event) => event.message.startsWith('Demo run accepted')).length, 1)
  assert.equal(JSON.stringify(entry.confirmedProfessional), facts)
  assert.equal(entry.assessment.methods.some((method) => 'score' in method || 'decision' in method), false)
  assert.equal(entry.results.objective.displayScore, 78)
  assert.equal(entry.results.humans.standard.decision, null)
  assert.ok(Date.parse(entry.expiresAt) > Date.now() + 29 * 24 * 60 * 60 * 1000)
})

test('retry preserves completed methods and resumes the failed stage only once', async () => {
  const { cases, caseId, entry } = await confirmedCase()
  await cases.runSimulation(caseId, 'retryable')
  assert.equal(entry.processingStatus, 'FAILED_RETRYABLE')
  assert.deepEqual(entry.assessment.methods.map((method) => method.status), ['Complete', 'Failed', 'Waiting'])
  await cases.uploadCv(caseId, { name: 'replacement.pdf' })
  await cases.retryCase(caseId)
  assert.equal(entry.processingStatus, 'FAILED_RETRYABLE')
  assert.equal(entry.file, null)
  await Promise.all([cases.runSimulation(caseId, 'retryable', true), cases.runSimulation(caseId, 'retryable', true)])
  assert.equal(entry.processingStatus, 'AUTOMATED_COMPLETE')
  assert.equal(entry.assessment.retryCount, 1)
  assert.equal(entry.assessment.events.filter((event) => event.message === 'Objective demo stage started.').length, 1)
})

test('final failure cannot retry and never becomes a completed assessment', async () => {
  const { cases, caseId, entry } = await confirmedCase()
  await cases.runSimulation(caseId, 'final')
  const events = entry.assessment.events.length
  await cases.runSimulation(caseId, 'normal', true)
  assert.equal(entry.processingStatus, 'FAILED_FINAL')
  assert.equal(entry.assessment.retryable, false)
  assert.equal(entry.assessment.events.length, events)
})

test('sign out discards a running case without later restoring data', async () => {
  const { cases, caseId, entry } = await confirmedCase()
  const pending = cases.runSimulation(caseId)
  useAuthStore().logout()
  await pending
  assert.equal(cases.getOwnedCase(caseId), null)
  assert.deepEqual(cases.cases, {})
  assert.equal(entry.processingStatus, 'AUTOMATED_RUNNING')
  assert.equal(entry.assessment.methods[0].status, 'Running')
})
