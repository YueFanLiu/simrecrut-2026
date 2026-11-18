import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useHrJobsStore } from '../src/store/modules/hr-jobs.js'
import { validateJob } from '../src/mock/hr-job-wizard.js'
import { waitForVersionDemo } from '../src/mock/hr-job-versions.js'

const hr = { username: 'hr', role: 'HR', displayName: 'Demo HR User' }
function freshStore() { setActivePinia(createPinia()); return useHrJobsStore() }

test('history lists one draft and immutable publications with the current published marker', () => {
  const store = freshStore()
  const history = store.readVersions(hr, 'hr-job-004')
  assert.deepEqual(history.versions.map((entry) => entry.version), ['1.3', '1.2', '1.1', '1.0'])
  assert.equal(history.versions.filter((entry) => entry.status === 'Draft').length, 1)
  assert.equal(history.versions.filter((entry) => entry.current).length, 1)
  assert.equal(history.versions.find((entry) => entry.current).version, '1.2')
  assert.equal('requirements' in history.versions[1], false)
  assert.equal(store.readVersions(hr, 'hr-job-007').versions[0].status, 'Draft')
  history.versions[1].version = 'Mutated copy'
  assert.equal(store.published['hr-job-004'][2].version, '1.2')
})

test('version reads require HR ownership and the version must belong to that job', () => {
  const store = freshStore()
  assert.throws(() => store.readVersions({ username: 'demo', role: 'Candidate' }, 'hr-job-004'), /HR demo session/)
  assert.throws(() => store.readVersion(hr, 'other-managed-job', 'other-managed-job-draft'), /unavailable/)
  assert.throws(() => store.readVersion(hr, 'hr-job-008', 'hr-job-004-v1.2'), /unavailable/)
  assert.throws(() => store.cloneVersion(hr, 'unknown', 'unknown-v1.0'), /unavailable/)
})

test('published details contain valid fixed configurations and copies cannot change their source', () => {
  const store = freshStore()
  const source = store.readVersion(hr, 'hr-job-004', 'hr-job-004-v1.0')
  assert.equal(source.status, 'Published')
  assert.equal(validateJob(source).valid, true)
  assert.deepEqual(source.weights.map((row) => row.value), [0.4, 0.3, 0.1, 0.1, 0.1])
  source.requirements[0].text = 'Changed return value'
  source.percentages[0] = '0'
  assert.notEqual(store.readVersion(hr, 'hr-job-004', source.versionId).requirements[0].text, source.requirements[0].text)
  assert.equal(store.readVersion(hr, 'hr-job-004', source.versionId).percentages[0], '40')
})

test('cloning a selected historical version creates one independent draft without changing current publication', () => {
  const store = freshStore()
  const source = store.readVersion(hr, 'hr-job-006', 'hr-job-006-v1.0')
  const before = JSON.stringify(store.published['hr-job-006'])
  const result = store.cloneVersion(hr, 'hr-job-006', source.versionId)
  assert.equal(result.version, '1.2')
  const draft = store.readDraft(hr, 'hr-job-006')
  assert.deepEqual(draft.form.requirements, source.requirements)
  assert.deepEqual(draft.form.percentages, source.percentages)
  assert.equal(draft.form.versionId, undefined)
  assert.equal(draft.sourceVersionId, source.versionId)
  assert.equal(draft.revision, 1)
  assert.equal(store.readVersions(hr, 'hr-job-006').publishedVersion, '1.1')
  draft.form.title = 'Updated draft title'
  store.saveDraft(hr, 'hr-job-006', draft.form, 1)
  assert.equal(store.readDraft(hr, 'hr-job-006').sourceVersionId, source.versionId)
  assert.equal(JSON.stringify(store.published['hr-job-006']), before)
  assert.throws(() => store.cloneVersion(hr, 'hr-job-006', source.versionId), (error) => error.code === 'DRAFT_EXISTS')
  assert.throws(() => store.cloneVersion(hr, 'hr-job-004', 'hr-job-004-v1.2'), /already exists/)
})

test('publishing a cloned draft appends history and leaves prior configurations unchanged', () => {
  const store = freshStore()
  const before = JSON.stringify(store.published['hr-job-008'][0])
  store.cloneVersion(hr, 'hr-job-008', 'hr-job-008-v1.0')
  store.publishDraft(hr, 'hr-job-008', 1)
  assert.equal(store.published['hr-job-008'].length, 2)
  assert.equal(JSON.stringify(store.published['hr-job-008'][0]), before)
  const history = store.readVersions(hr, 'hr-job-008')
  assert.equal(history.publishedVersion, '1.1')
  assert.equal(history.draftVersion, null)
  assert.equal(history.versions.filter((entry) => entry.current).length, 1)
  assert.equal(history.versions.some((entry) => entry.status === 'Draft'), false)
})

test('incomplete draft details stay explicit and failure boundaries return English recovery messages', async () => {
  const store = freshStore()
  const draft = store.readDraft(hr, 'hr-job-007')
  draft.form.percentages[0] = 'invalid'
  store.saveDraft(hr, 'hr-job-007', draft.form, draft.revision)
  const detail = store.readVersion(hr, 'hr-job-007', 'hr-job-007-draft')
  assert.equal(detail.percentages[0], 'invalid')
  assert.equal(detail.weights[0].value, null)
  for (const operation of ['load', 'detail', 'clone']) {
    await assert.rejects(waitForVersionDemo(`${operation}-error`, operation), /Unable to/)
  }
})
