import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/store/modules/auth.js'
import { registerDemo } from '../src/mock/auth.js'
import { filterHrJobs, loadHrJobs, selectManagedJobSummaries } from '../src/mock/hr-jobs.js'

test('HR mock account signs in by username/email and registration cannot claim it', async () => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  await auth.login({ identifier: 'hr', password: 'SimRecrut123!' })
  assert.equal(auth.hasDemoRole('HR'), true)
  assert.equal(auth.hasDemoRole('Candidate'), false)
  auth.logout()
  await auth.login({ identifier: 'HR@simrecrut.test', password: 'SimRecrut123!' })
  assert.equal(auth.demoUser.username, 'hr')
  await assert.rejects(registerDemo({ username: 'hr', email: 'different@example.test', password: 'Password123!', agreedToTerms: true }), /already used/)
})

test('HR loader rejects Candidates and returns only jobs owned by the selected HR user', async () => {
  await assert.rejects(loadHrJobs({ username: 'demo', role: 'Candidate' }), /HR accounts only/)
  const jobs = await loadHrJobs({ username: 'hr', role: 'HR' })
  assert.equal(jobs.length, 8)
  assert.equal(jobs.some((job) => job.jobId === 'other-managed-job'), false)
  assert.equal(jobs.some((job) => job.status === 'Draft'), true)
  assert.equal(jobs.some((job) => job.status === 'Published'), true)
  const software = jobs.find((job) => job.jobId === 'hr-job-004')
  assert.equal(software.publishedVersion, '1.2')
  assert.equal(software.draftVersion, '1.3')
  assert.deepEqual(await loadHrJobs({ username: 'unknown-hr', role: 'HR' }), [])
})

test('HR summaries omit ownership/internal parameters and never alter published versions', () => {
  const input = [{ jobId: 'job', owner: 'hr', title: 'Example Job', department: 'Engineering',
    status: 'Published', createdAt: '2026-10-10', publishedVersion: '1.0', draftVersion: '1.1',
    summary: 'Example summary', weights: [0.5], cutoff: 0.6, candidateData: 'Excluded' }]
  const result = selectManagedJobSummaries(input, 'hr')
  for (const field of ['owner', 'weights', 'cutoff', 'candidateData']) assert.equal(field in result[0], false)
  result[0].publishedVersion = 'Changed copy'
  assert.equal(input[0].publishedVersion, '1.0')
})

test('title/department/keyword search combines with status and sorts without mutating source', async () => {
  const jobs = await loadHrJobs({ username: 'hr', role: 'HR' })
  const original = JSON.stringify(jobs)
  assert.equal(filterHrJobs(jobs, { keyword: ' Engineering ', status: 'Draft' }).length, 2)
  assert.equal(filterHrJobs(jobs, { keyword: 'Java' }).length, 2)
  assert.equal(filterHrJobs(jobs, { keyword: 'data analyst' }).length, 1)
  assert.deepEqual(filterHrJobs(jobs, { keyword: 'unmatched' }), [])
  assert.equal(filterHrJobs(jobs, { sort: 'oldest' })[0].jobId, 'hr-job-001')
  assert.equal(filterHrJobs(jobs, { sort: 'latest' })[0].jobId, 'hr-job-008')
  assert.equal(JSON.stringify(jobs), original)
})

test('empty/error mock scenarios return no content and English recovery text', async () => {
  const user = { username: 'hr', role: 'HR' }
  assert.deepEqual(await loadHrJobs(user, 'empty'), [])
  await assert.rejects(loadHrJobs(user, 'error'), /Unable to load managed jobs/)
})
