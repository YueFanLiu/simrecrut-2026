import test from 'node:test'
import assert from 'node:assert/strict'
import { loadJobDetail, selectPublicJobDetail } from '../src/mock/job-details.js'
import { loadPublishedJobs } from '../src/mock/jobs.js'

test('every listed job has public details for the same published version', async () => {
  const jobs = await loadPublishedJobs()
  const details = await Promise.all(jobs.map((job) => loadJobDetail(job.jobId)))
  details.forEach((detail, index) => {
    assert.equal(detail.jobId, jobs[index].jobId)
    assert.equal(detail.version, jobs[index].currentVersion)
    assert.equal(detail.title, jobs[index].title)
    assert.ok(detail.jobVersionId && detail.description)
    assert.ok(detail.requirements.responsibilities.length)
    assert.ok(detail.requirements.qualifications.length)
  })
  assert.equal(details[0].version, '1.2')
})

test('unknown, retired and unavailable jobs return no detail', async () => {
  for (const jobId of ['missing-position', 'retired-position', 'constructor']) {
    assert.equal(await loadJobDetail(jobId), null)
  }
  assert.equal(await loadJobDetail('software-engineer', 'unavailable'), null)
})

test('detail projection excludes internal fields and copies requirement arrays', () => {
  const job = { jobId: 'one', title: 'Engineer', currentVersion: '1.0', weights: [1], cutoff: 80 }
  const profile = {
    description: 'Public description', responsibilities: ['Public duty'],
    qualifications: ['Public skill'], experience: '1 year', education: 'Bachelor',
    language: 'English', weights: [2], cutoff: 90,
  }
  const detail = selectPublicJobDetail(job, profile)
  assert.equal(detail.jobVersionId, 'one-v1.0')
  for (const field of ['weights', 'cutoff', 'status', 'isCurrentPublished']) {
    assert.equal(Object.hasOwn(detail, field), false)
    assert.equal(Object.hasOwn(detail.requirements, field), false)
  }
  detail.requirements.responsibilities.push('Changed copy')
  assert.deepEqual(profile.responsibilities, ['Public duty'])
})

test('mock detail failure provides an English retry message', async () => {
  await assert.rejects(loadJobDetail('software-engineer', 'error'),
    /Unable to load job details\. Please try again\./)
})
