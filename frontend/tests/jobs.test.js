import test from 'node:test'
import assert from 'node:assert/strict'
import { loadPublishedJobs, selectPublishedJobSummaries } from '../src/mock/jobs.js'
import { filterPublishedJobs, formatPublishedDate } from '../src/utils/job-list.js'

const defaults = { keyword: '', category: '', location: '', employmentType: '', sort: 'latest' }

test('Candidate summaries keep only the current Published version and public fields', () => {
  const versions = [
    { jobId: 'one', title: 'Current', status: 'Published', isCurrentPublished: true, weights: [1], cutoff: 75 },
    { jobId: 'one', title: 'Historical', status: 'Published', isCurrentPublished: false },
    { jobId: 'two', title: 'Draft', status: 'Draft', isCurrentPublished: true },
    { jobId: 'three', title: 'Retired', status: 'Retired', isCurrentPublished: false },
  ]
  const summaries = selectPublishedJobSummaries(versions)
  assert.deepEqual(summaries.map((job) => job.title), ['Current'])
  for (const field of ['weights', 'cutoff', 'status', 'isCurrentPublished']) {
    assert.equal(Object.hasOwn(summaries[0], field), false)
  }
})

test('normal mock data has eight distinct current jobs and retains the published version over a newer draft', async () => {
  const jobs = await loadPublishedJobs()
  assert.equal(jobs.length, 8)
  assert.equal(new Set(jobs.map((job) => job.jobId)).size, 8)
  assert.equal(jobs.find((job) => job.jobId === 'software-engineer').currentVersion, '1.2')
  assert.equal(jobs.some((job) => /Draft|Retired|Historical/.test(job.title)), false)
})

test('search matches title, company and summary with trimmed case-insensitive input', async () => {
  const jobs = await loadPublishedJobs()
  for (const keyword of [' SOFTWARE ENGINEER ', ' techcorp ', ' MODERN DEVELOPMENT ']) {
    assert.deepEqual(filterPublishedJobs(jobs, { ...defaults, keyword }).map((job) => job.jobId), ['software-engineer'])
  }
})

test('combined filters intersect and leave the source list unchanged', async () => {
  const jobs = await loadPublishedJobs()
  const original = structuredClone(jobs)
  const filters = { ...defaults, category: 'Tech', location: 'Remote', employmentType: 'Internship' }
  assert.deepEqual(filterPublishedJobs(jobs, filters).map((job) => job.jobId), ['frontend-intern'])
  assert.deepEqual(filterPublishedJobs(jobs, { ...filters, keyword: 'missing-position' }), [])
  filterPublishedJobs(jobs, { ...defaults, sort: 'oldest' })
  assert.deepEqual(jobs, original)
})

test('date and title sorts have deterministic English ordering', async () => {
  const jobs = await loadPublishedJobs()
  assert.equal(filterPublishedJobs(jobs, defaults)[0].jobId, 'software-engineer')
  assert.equal(filterPublishedJobs(jobs, { ...defaults, sort: 'oldest' })[0].jobId, 'frontend-intern')
  assert.equal(filterPublishedJobs(jobs, { ...defaults, sort: 'title' })[0].jobId, 'ai-research-intern')
  assert.equal(formatPublishedDate('2026-10-08'), '8 Oct 2026')
})

test('mock loading supports an empty list and an English failure', async () => {
  assert.deepEqual(await loadPublishedJobs('empty'), [])
  await assert.rejects(loadPublishedJobs('error'), /Unable to load published jobs\. Please try again\./)
})
