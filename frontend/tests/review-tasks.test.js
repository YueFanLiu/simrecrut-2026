import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createReviewTasks, filterReviewTasks, selectReviewTaskSummaries } from '../src/mock/review-tasks.js'
import { useReviewTasksStore } from '../src/store/modules/review-tasks.js'
import { useResearchModuleStore } from '../src/store/modules/research-module.js'
import { useAuthStore } from '../src/store/modules/auth.js'
const hr = { username: 'hr', role: 'HR' }, researcher = { username: 'researcher', role: 'Researcher' }

test('Reviewer is an HR/Researcher responsibility and assignment plus resource access is required', () => {
  const entries = createReviewTasks()
  assert.equal(selectReviewTaskSummaries(entries, hr).length, 3)
  assert.equal(selectReviewTaskSummaries(entries, researcher).length, 3)
  assert.deepEqual(selectReviewTaskSummaries(entries, { username: 'unrelated', role: 'HR' }), [])
  for (const role of ['Candidate', 'Reviewer', 'Admin']) assert.throws(() => selectReviewTaskSummaries(entries, { ...hr, role }), /HR or Researcher/)
})
test('revoked grants, retention, deleted privacy and invalid states cannot expose task summaries', () => {
  const now = Date.now(), entries = createReviewTasks(now), row = entries[0]
  for (const expiresAt of [new Date(now).toISOString(), 'invalid']) {
    row.access.expiresAt = expiresAt
    assert.equal(selectReviewTaskSummaries(entries, hr, now).some(task => task.reviewId === row.reviewId), false)
  }
  row.access.expiresAt = null
  for (const privacy of ['DELETE_REQUESTED', 'PURGING', 'DELETED', 'UNKNOWN']) {
    row.privacy = privacy
    assert.equal(selectReviewTaskSummaries(entries, hr, now).some(task => task.reviewId === row.reviewId), false)
  }
  row.privacy = 'ACTIVE'; row.status = 'Invalid'
  assert.equal(selectReviewTaskSummaries(entries, hr, now).some(task => task.reviewId === row.reviewId), false)
})
test('blind summary projection excludes decisions, reasons, scores, identities and professional data even after submission', () => {
  const entries = createReviewTasks(), row = entries[2]
  Object.assign(row, { score: .8, candidateName: 'Private', rawCv: 'Private', professional: { skills: 'Private' }, otherDecision: 'Reject' })
  const summary = selectReviewTaskSummaries(entries, hr).find(task => task.reviewId === row.reviewId)
  assert.deepEqual(Object.keys(summary).sort(), ['reviewId', 'job', 'condition', 'purpose', 'status', 'assignedAt', 'experimentId'].sort())
  assert.equal(summary.status, 'Submitted')
  summary.job = 'Changed copy'; assert.equal(row.job, 'Data Analyst v2')
})
test('local filters combine, include pending without decisions and sort without source mutation', () => {
  const rows = selectReviewTaskSummaries(createReviewTasks(), hr), before = JSON.stringify(rows)
  assert.equal(filterReviewTasks(rows, { keyword: ' backend ', status: 'Pending', condition: 'Instructed-Bias', purpose: 'Research' }).length, 1)
  assert.equal(filterReviewTasks(rows, { keyword: 'EXP-003' }).length, 1)
  assert.equal(filterReviewTasks(rows, { status: 'Submitted' }).length, 1)
  assert.equal(filterReviewTasks(rows, { sort: 'oldest' })[0].reviewId, 'REV-H-003')
  assert.equal(JSON.stringify(rows), before)
})
test('R assignments enter only the target reviewer list, remain purpose-separated and respect resource cleanup', () => {
  setActivePinia(createPinia())
  const store = useReviewTasksStore(), research = useResearchModuleStore()
  research.saveSubjects('EXP-002', ['SUB-001'], researcher); research.createVariants('EXP-002', 'Gender', researcher)
  research.assignReviews('EXP-002', 'reviewer-hr', 'Standard', researcher)
  assert.equal(store.listTasks(hr, research).length, 5)
  assert.equal(store.listTasks(researcher, research).length, 3)
  research.assignPrimaryReview('SUB-003', 'reviewer-researcher', 'Instructed-Bias', researcher)
  assert.equal(store.listTasks(researcher, research).length, 4)
  research.subjects[0].privacy = 'PURGING'
  assert.equal(store.listTasks(hr, research).length, 3)
  research.subjects.find(row => row.id === 'SUB-003').purgeAt = new Date().toISOString()
  assert.equal(store.listTasks(researcher, research).length, 3)
})
test('mock empty/error loads preserve data and sign out restores task and Research fixtures', async () => {
  setActivePinia(createPinia())
  const store = useReviewTasksStore(), research = useResearchModuleStore(), auth = useAuthStore()
  const before = JSON.stringify(store.tasks)
  assert.deepEqual(await store.loadTasks(hr, research, 'empty'), [])
  await assert.rejects(store.loadTasks(hr, research, 'error'), /Unable to load review tasks/)
  await assert.rejects(store.loadTasks({ ...hr, role: 'Candidate' }, research), /HR or Researcher/)
  assert.equal(JSON.stringify(store.tasks), before)
  store.tasks[0].job = 'Changed'; auth.demoUser = hr; auth.logout()
  assert.equal(store.tasks[0].job, 'Software Engineer v3')
  assert.equal(auth.demoUser, null)
})
