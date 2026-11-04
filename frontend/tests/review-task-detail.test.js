import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useReviewTasksStore } from '../src/store/modules/review-tasks.js'
import { useResearchModuleStore } from '../src/store/modules/research-module.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { validateReviewSubmission } from '../src/mock/review-task-detail.js'
const hr = { username: 'hr', role: 'HR' }, researcher = { username: 'researcher', role: 'Researcher' }
function setup() { setActivePinia(createPinia()); return { tasks: useReviewTasksStore(), research: useResearchModuleStore() } }
const form = { decision: 'Accept', reason: 'Relevant Java and SQL experience matches the public requirements.' }
test('review validates only Accept/Reject and 1-2000 trimmed reason characters', () => {
  for (const decision of ['', 'Uncertain', 'accept']) assert.match(validateReviewSubmission({ ...form, decision }), /Select/)
  for (const reason of ['', '  ', 'a'.repeat(2001), null]) assert.match(validateReviewSubmission({ ...form, reason }), /1 to 2000/)
  assert.equal(validateReviewSubmission({ decision: 'Reject', reason: ' a ' }), '')
  assert.equal(validateReviewSubmission({ ...form, reason: 'a'.repeat(2000) }), '')
})
test('Standard detail excludes identity, controlled attributes, scores and pending decisions', () => {
  const { tasks, research } = setup()
  Object.assign(tasks.tasks[0], { candidateName: 'SECRET', score: 100, otherReason: 'SECRET', decision: 'Reject', reason: 'SECRET', controlled: 'SECRET' })
  const detail = tasks.readDetail('REV-H-001', hr, research)
  assert.equal(JSON.stringify(detail).includes('SECRET'), false)
  assert.equal('controlled' in detail, false); assert.equal('submission' in detail, false)
  assert.deepEqual(Object.keys(detail.professional).sort(), ['experience', 'education', 'skills', 'projects', 'languages'].sort())
  detail.requirements.push('Changed'); assert.equal(tasks.readDetail('REV-H-001', hr, research).requirements.includes('Changed'), false)
  assert.equal(tasks.readDetail('REV-H-002', hr, research).controlled.instructionAvailable, true)
})
test('unassigned, revoked, expired, purged and wrong-role details are unavailable', () => {
  const { tasks, research } = setup()
  for (const id of ['UNKNOWN', 'UNASSIGNED', 'REVOKED', 'EXPIRED', 'PURGING', 'REV-R-001']) assert.throws(() => tasks.readDetail(id, hr, research), /unavailable/)
  assert.throws(() => tasks.readDetail('REV-H-001', { ...hr, role: 'Candidate' }, research), /HR or Researcher/)
})
test('confirmed submission locks own result, trims reason and keeps list blind', async () => {
  const { tasks, research } = setup()
  await tasks.submitReview('REV-H-001', { ...form, reason: `  ${form.reason}  ` }, hr, research)
  assert.equal(tasks.readDetail('REV-H-001', hr, research).submission.reason, form.reason)
  assert.equal(tasks.listTasks(hr, research)[0].status, 'Submitted')
  assert.equal('decision' in tasks.listTasks(hr, research)[0], false)
  await assert.rejects(tasks.submitReview('REV-H-001', { decision: 'Reject', reason: 'Different' }, hr, research), /already been submitted/)
  assert.equal(tasks.tasks[0].decision, 'Accept')
})
test('submission rechecks access after delay and reset cannot resurrect a review', async () => {
  const { tasks, research } = setup()
  let pending = tasks.submitReview('REV-H-001', form, hr, research)
  tasks.tasks[0].access.allowed = false
  await assert.rejects(pending, /unavailable/)
  tasks.clearTasks(); pending = tasks.submitReview('REV-H-001', form, hr, research)
  const auth = useAuthStore(); auth.demoUser = hr; auth.logout()
  await assert.rejects(pending, /task changed/)
  assert.equal(tasks.tasks[0].status, 'Pending')
})
test('duplicate in-flight submissions have one winner without overwriting', async () => {
  const { tasks, research } = setup()
  const results = await Promise.allSettled([tasks.submitReview('REV-H-001', form, hr, research), tasks.submitReview('REV-H-001', { decision: 'Reject', reason: 'Different' }, hr, research)])
  assert.equal(results.filter(row => row.status === 'fulfilled').length, 1)
  assert.equal(tasks.tasks[0].reason, form.reason)
})
test('load and submit errors preserve pending data for retry', async () => {
  const { tasks, research } = setup()
  await assert.rejects(tasks.loadDetail('REV-H-001', hr, research, 'load-error'), /Unable to load/)
  await assert.rejects(tasks.submitReview('REV-H-001', form, hr, research, 'submit-error'), /input has been kept/)
  assert.equal(tasks.tasks[0].status, 'Pending'); assert.equal(tasks.tasks[0].decision, undefined)
  await tasks.submitReview('REV-H-001', form, hr, research)
  assert.equal(tasks.tasks[0].status, 'Submitted')
})
test('R assigned pair details show only assigned professional snapshot and submit original record', async () => {
  const { tasks, research } = setup()
  research.saveSubjects('EXP-002', ['SUB-001'], researcher); research.createVariants('EXP-002', 'Gender', researcher)
  research.assignReviews('EXP-002', 'reviewer-hr', 'Standard', researcher)
  const experiment = research.experiments.find(row => row.id === 'EXP-002'), review = experiment.reviews[0]
  experiment.pairs[0].professional.skills = 'Assigned snapshot'; experiment.pairs[0].variantProfessional.skills = 'Other snapshot'
  const detail = tasks.readDetail(review.id, hr, research)
  assert.equal(detail.professional.skills, 'Assigned snapshot'); assert.equal(JSON.stringify(detail).includes('Other snapshot'), false)
  await tasks.submitReview(review.id, form, hr, research)
  assert.equal(review.status, 'Submitted'); assert.equal(review.reason, form.reason)
  assert.equal('reason' in research.listExperiments(researcher).find(row => row.id === 'EXP-002').reviews[0], false)
})
test('missing instructed-bias instructions cannot be fabricated or submitted; Primary source updates in place', async () => {
  const { tasks, research } = setup()
  research.assignPrimaryReview('SUB-003', 'reviewer-hr', 'Instructed-Bias', researcher)
  const id = 'SUB-003-PRIMARY-Instructed-Bias'
  assert.equal(tasks.readDetail(id, hr, research).controlled.instructionAvailable, false)
  await assert.rejects(tasks.submitReview(id, form, hr, research), /instructions are unavailable/)
  research.assignPrimaryReview('SUB-003', 'reviewer-hr', 'Standard', researcher)
  await tasks.submitReview('SUB-003-PRIMARY-Standard', form, hr, research)
  assert.equal(research.subjects.find(row => row.id === 'SUB-003').primaryReviews[1].status, 'Submitted')
})
