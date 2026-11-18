import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createReviewDraft } from '../src/mock/review-draft.js'
import { copyProfessionalDraft, emptyResearchAttributes, validateReviewDraft } from '../src/utils/review-validation.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { useCasesStore } from '../src/store/modules/cases.js'
import { loadJobDetail } from '../src/mock/job-details.js'

async function reviewCase(researchAllowed = false) {
  setActivePinia(createPinia())
  useAuthStore().demoUser = { username: 'demo', role: 'Candidate' }
  const cases = useCasesStore()
  const caseId = await cases.createCase(await loadJobDetail('software-engineer'))
  // Set the completed upstream stage; extraction timing is covered by C03 tests.
  cases.cases[caseId].processingStatus = 'REVIEW_REQUIRED'
  const draft = await cases.loadReview(caseId, 'normal', researchAllowed)
  return { cases, caseId, draft }
}

test('required missing and conflicting facts block confirmation but can be saved incomplete', () => {
  const draft = createReviewDraft('missing')
  assert.deepEqual(validateReviewDraft(draft), {})
  assert.equal(Object.keys(validateReviewDraft(draft, true)).length, 2)
  draft.professional.education.items[0].subject.value = 'Computer Science'
  draft.professional.education.items[0].subject.status = 'USER_SUPPLIED'
  draft.professional.languages.items[0].level.value = 'B2'
  assert.match(Object.values(validateReviewDraft(draft, true))[0], /Resolve language level/)
  draft.professional.languages.items[0].level.status = 'USER_SUPPLIED'
  assert.deepEqual(validateReviewDraft(draft, true), {})
})

test('known absence differs from unknown, optional empty groups do not block, invalid durations do', () => {
  const draft = createReviewDraft('empty')
  assert.equal(Object.keys(validateReviewDraft(draft, true)).length, 4)
  draft.requiredGroups.forEach((key) => { draft.professional[key].confirmedAbsent = true })
  assert.deepEqual(validateReviewDraft(draft, true), {})
  const normal = createReviewDraft()
  normal.professional.experience.items[0].months.value = '1.5'
  assert.match(Object.values(validateReviewDraft(normal))[0], /whole number/)
})

test('professional projection omits identifiers/research and preserves original evidence on corrections', () => {
  const draft = createReviewDraft()
  draft.professional.name = 'Excluded identifier'
  draft.professional.gender = 'Excluded research field'
  const field = draft.professional.skills.items[0].name
  const excerpt = field.evidenceExcerpt
  field.value = 'Python'
  field.status = 'USER_SUPPLIED'
  const copy = copyProfessionalDraft(draft)
  assert.equal(Object.hasOwn(copy.professional, 'name'), false)
  assert.equal(Object.hasOwn(copy.professional, 'gender'), false)
  assert.equal(copy.professional.skills.items[0].name.evidenceExcerpt, excerpt)
  copy.professional.skills.items[0].name.value = 'Changed copy'
  assert.equal(field.value, 'Python')
})

test('failed draft save preserves prior saved data and successful save resumes edited values', async () => {
  const { cases, caseId, draft } = await reviewCase()
  draft.professional.skills.items[0].name.value = 'Python'
  draft.professional.skills.items[0].name.status = 'USER_SUPPLIED'
  await assert.rejects(cases.saveReview(caseId, draft, 'save-error'), /Unable to save/)
  assert.equal(cases.getOwnedCase(caseId).reviewDraft.professional.skills.items[0].name.value, 'Java')
  await cases.saveReview(caseId, draft)
  assert.equal((await cases.loadReview(caseId)).professional.skills.items[0].name.value, 'Python')
})

test('research stays separate, defaults UNKNOWN and requires the demo capability and explicit acknowledgement', async () => {
  const { cases, caseId } = await reviewCase()
  await assert.rejects(cases.saveResearch(caseId, emptyResearchAttributes(), false), /not available/)
  cases.cases[caseId].researchAllowed = true
  const attributes = { ...emptyResearchAttributes(), referral: 'Self-reported referral', gender: '', name: 'Excluded' }
  await assert.rejects(cases.saveResearch(caseId, attributes, false), /Confirm the optional details/)
  await cases.saveResearch(caseId, attributes, true)
  assert.equal(cases.cases[caseId].researchAttributes.gender, 'UNKNOWN')
  assert.equal(Object.hasOwn(cases.cases[caseId].researchAttributes, 'name'), false)
  assert.equal(Object.hasOwn(cases.cases[caseId].reviewDraft.professional, 'referral'), false)
})

test('confirmation failure is non-destructive; success is idempotent, clears temporary data and locks editing', async () => {
  const { cases, caseId, draft } = await reviewCase()
  const entry = cases.cases[caseId]
  entry.file = { name: 'sample.pdf', pageCount: 1, size: 1024 }
  entry.preview = { skills: ['Java'] }
  await assert.rejects(cases.confirmReview(caseId, draft, true, 'confirm-error'), /Nothing was confirmed/)
  assert.equal(entry.processingStatus, 'REVIEW_REQUIRED')
  assert.ok(entry.reviewDraft && entry.file)
  await Promise.all([cases.confirmReview(caseId, draft, true), cases.confirmReview(caseId, draft, true)])
  assert.equal(entry.processingStatus, 'CONFIRMED')
  assert.ok(entry.confirmedAt)
  assert.equal(entry.confirmedProfessional, null)
  assert.equal(entry.file, null)
  assert.equal(entry.reviewDraft, null)
  assert.equal(entry.preview, null)
  assert.ok(Date.parse(entry.expiresAt) > Date.now() + 29 * 24 * 60 * 60 * 1000)
  await assert.rejects(cases.saveReview(caseId, draft), /no longer be edited/)
})

test('deletion during a pending save never restores the professional draft', async () => {
  const { cases, caseId, draft } = await reviewCase()
  const pending = cases.saveReview(caseId, draft)
  cases.deleteCase(caseId)
  await assert.rejects(pending, /case changed/)
  assert.equal(cases.getOwnedCase(caseId), null)
})
