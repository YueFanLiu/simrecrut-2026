import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useHrJobsStore } from '../src/store/modules/hr-jobs.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { createJobForm, extractMockRequirements, validateJob, validateWeights, weightUnits } from '../src/mock/hr-job-wizard.js'

const hr = { username: 'hr', role: 'HR' }
function validForm() {
  const form = createJobForm({ title: 'Software Engineer', department: 'Engineering', summary: 'Use Java. Two years of experience. A degree is required. Speak English. Deliver projects.' })
  form.requirements = extractMockRequirements(form.description).map((row) => ({ ...row, reviewed: true }))
  form.extractedDescription = form.description
  return form
}
function freshStore() { setActivePinia(createPinia()); return useHrJobsStore() }

test('draft creation and ownership prevent published-only or foreign editing', () => {
  const store = freshStore()
  assert.throws(() => store.createDraft({ username: 'demo', role: 'Candidate' }, validForm()), /HR demo session/)
  assert.throws(() => store.readDraft(hr, 'other-managed-job'), /unavailable/)
  assert.throws(() => store.readDraft(hr, 'hr-job-008'), /read-only/)
  const id = store.createDraft(hr, validForm())
  const copy = store.readDraft(hr, id)
  copy.form.title = 'Mutated copy'
  assert.equal(store.readDraft(hr, id).form.title, 'Software Engineer')
  assert.equal(store.jobs.filter((job) => job.jobId === id).length, 1)
  assert.throws(() => store.createDraft(hr, createJobForm()), /job title/)
})

test('extraction preserves exact source text and requires explicit review', () => {
  const form = validForm()
  assert.deepEqual(form.requirements.map((row) => row.category), ['Skills', 'Experience', 'Education', 'Languages', 'Projects'])
  assert.ok(form.requirements.every((row) => form.description.includes(row.evidenceExcerpt)))
  assert.equal(validateJob(form).valid, true)
  form.requirements[0].reviewed = false
  assert.equal(validateJob(form).valid, false)
  form.requirements[0].reviewed = true
  form.description += ' Changed description.'
  assert.ok(validateJob(form).errors.some((error) => error.includes('changed after extraction')))
})

test('exact percentage validation rejects invalid totals, ranges and precision without normalization', () => {
  const form = validForm()
  for (const value of ['-1', '100.00001', '20.000001', 'NaN', '1e2', '']) assert.equal(weightUnits(value), null)
  assert.equal(weightUnits('33.33333'), 3333333)
  form.percentages = ['33.33333', '33.33333', '33.33334', '0', '0']
  assert.deepEqual(validateWeights(form), [])
  for (const values of [['0', '0', '0', '0', '0'], ['19', '20', '20', '20', '20']]) {
    form.percentages = values
    assert.ok(validateWeights(form).length)
    assert.deepEqual(form.percentages, values)
  }
})

test('active criteria, mandatory gates, duplicate codes and missing reviews block publication', () => {
  const form = validForm()
  form.activeCriteria = []
  assert.equal(validateJob(form).valid, false)
  form.activeCriteria = ['Skills']
  form.percentages = ['100', '0', '0', '0', '0']
  form.requirements.forEach((row) => { row.assessmentIncluded = row.category === 'Skills' })
  assert.equal(validateJob(form).valid, true)
  form.requirements[1].mandatory = true
  assert.ok(validateJob(form).errors.some((error) => error.includes('mandatory conditions')))
  form.requirements[1].mandatory = false
  form.requirements[0].publicVisible = false
  assert.equal(validateJob(form).valid, true)
  assert.equal(validateJob(form).warnings.length, 1)
  form.requirements.push({ ...form.requirements[0] })
  assert.ok(validateJob(form).errors.some((error) => error.includes('unique code')))
})

test('all draft edits use one revision and publication revalidates with immutable fractional snapshots', () => {
  const store = freshStore()
  const form = validForm()
  const id = store.createDraft(hr, form)
  form.title = 'Updated title'
  const revision = store.saveDraft(hr, id, form, 1)
  assert.equal(revision, 2)
  assert.throws(() => store.saveDraft(hr, id, validForm(), 1), /changed elsewhere/)
  assert.throws(() => store.publishDraft(hr, id, 1), /changed elsewhere/)
  form.percentages[0] = '0'
  store.saveDraft(hr, id, form, 2)
  assert.throws(() => store.publishDraft(hr, id, 3), /exactly 100/)
  form.percentages[0] = '20'
  store.saveDraft(hr, id, form, 3)
  const snapshot = store.publishDraft(hr, id, 4)
  assert.deepEqual(snapshot.weights.map((weight) => weight.value), [0.2, 0.2, 0.2, 0.2, 0.2])
  snapshot.title = 'Mutated return value'
  assert.equal(store.published[id][0].title, 'Updated title')
  assert.throws(() => store.saveDraft(hr, id, form, 4), /read-only/)
  assert.equal(store.jobs.find((job) => job.jobId === id).status, 'Published')
})

test('saving a newer draft preserves the prior published label; logout clears HR drafts', () => {
  const store = freshStore()
  const draft = store.readDraft(hr, 'hr-job-004')
  store.saveDraft(hr, 'hr-job-004', validForm(), draft.revision)
  assert.equal(store.jobs.find((job) => job.jobId === 'hr-job-004').publishedVersion, '1.2')
  store.createDraft(hr, validForm())
  useAuthStore().logout()
  assert.equal(store.jobs.length, 9)
  assert.deepEqual(store.drafts, {})
  assert.equal(Object.keys(store.published).length, 4)
  assert.equal(Object.keys(store.published).some((id) => id.startsWith('demo-job-')), false)
})
