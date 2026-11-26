import { test } from 'node:test'
import assert from 'node:assert/strict'
import { cvContext, cvReviewRoute, cvSelectionRoute, jobsForResume, resumeQuery, reusableCv, createCvAttachment } from '../src/utils/cv-workflow.js'

const resumeId = 'a'.repeat(32)
test('library/job/review links retain only bounded resource IDs, never arbitrary return paths or CV contents', () => {
  const query = { resumeId, caseId: '01ABCDEFGHIJKLMNOPQRSTUVWX', redirect: 'https://outside.test/', content: 'private PDF' }
  assert.deepEqual(cvContext(query), { resumeId, caseId: query.caseId })
  assert.deepEqual(cvReviewRoute(resumeId, query.caseId).query, { caseId: query.caseId })
  assert.deepEqual(cvSelectionRoute(query.caseId, resumeId).query, { resumeId })
  assert.deepEqual(jobsForResume(resumeId), { name: 'PublishedJobs', query: { resumeId } })
  for (const value of ['//outside.test', 'https://outside.test/', ['a'], '', 'a'.repeat(101)]) {
    assert.equal(cvContext({ caseId: value }).caseId, '')
    assert.deepEqual(resumeQuery(value), {})
  }
})

test('only confirmed, retained library records can be selected for reuse', () => {
  const row = { status: 'CONFIRMED', confirmed: 1, expires_at: '2027-01-01T00:00:00Z' }
  const now = Date.parse('2026-11-25T00:00:00Z')
  assert.equal(reusableCv(row, now), true)
  for (const wrong of [{ confirmed: 0 }, { status: 'REVIEW_REQUIRED' }, { status: 'PURGING' },
    { expires_at: '2026-01-01T00:00:00Z' }, { expires_at: 'invalid' }]) assert.equal(reusableCv({ ...row, ...wrong }, now), false)
})

test('an ambiguous attachment retries the same CV with the same key and no PDF upload', async () => {
  const seen = []
  const use = createCvAttachment({ readCase: async () => ({ caseId: 'CASE', processingStatus: 'AWAITING_UPLOAD' }),
    attach: async (...args) => { seen.push(args); if (seen.length === 1) throw new Error('Connection lost'); return { processingStatus: 'REVIEW_REQUIRED' } } })
  await assert.rejects(use('CASE', resumeId), /Connection lost/)
  const result = await use('CASE', resumeId)
  assert.equal(result.row.processingStatus, 'REVIEW_REQUIRED')
  assert.deepEqual(seen[0], seen[1])
  assert.equal(seen[0][1], resumeId)
})

test('a completed attachment is resumed after a lost response; selecting a different CV cannot overwrite it', async () => {
  let status = 'AWAITING_UPLOAD', count = 0
  const use = createCvAttachment({ readCase: async () => ({ processingStatus: status }),
    attach: async () => { count++; status = 'REVIEW_REQUIRED'; throw new Error('Connection lost') } })
  await assert.rejects(use('CASE', resumeId))
  const result = await use('CASE', 'b'.repeat(32))
  assert.equal(result.alreadyStarted, true)
  assert.equal(count, 1)
})

test('a conflict rechecks the saved step, while an unavailable CV remains an actionable error', async () => {
  let reads = 0
  const use = createCvAttachment({ readCase: async () => ({ processingStatus: ++reads > 1 ? 'REVIEW_REQUIRED' : 'AWAITING_UPLOAD' }),
    attach: async () => { throw Object.assign(new Error('Changed on server'), { status: 409 }) } })
  assert.equal((await use('CASE', resumeId)).alreadyStarted, true)
  const unavailable = createCvAttachment({ readCase: async () => ({ processingStatus: 'AWAITING_UPLOAD' }),
    attach: async () => { throw Object.assign(new Error('CV expired'), { status: 409 }) } })
  await assert.rejects(unavailable('CASE', resumeId), /CV expired/)
})
