import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createRevisionDraft, revisionPayload, exportRevisionDraft, restoreRevisionDraft, createRevisionSaver } from '../src/utils/cv-revisions.js'
import { makeSkill, makeSection } from '../src/utils/cv-supplements.js'

const sourceId = 'a'.repeat(32), newId = 'c'.repeat(32)
const source = () => ({ id: sourceId, status: 'CONFIRMED', confirmed: 1, revision: 2,
  filename: 'original.pdf', originalAvailable: false, original_text: 'do not copy raw CV text', cloud_revision: 2,
  draft: { professional: { personal: [{ fieldCode: 'EMAIL', value: 'candidate@example.test' },
    { fieldCode: 'PHOTO', value: 'YES', imageBase64: 'QUJD' }],
  skills: [{ skillCode: 'JAVA', evidence: { source: 'EXTRACTED', page: 1, text: 'Java' } }],
  experience: [], education: [], languages: [], projects: [], other: [makeSkill('\u7528\u6237\u8bbf\u8c08'), makeSection('Portfolio', 'A project\nSecond line')] }, warnings: [] } })
const edited = () => createRevisionDraft(source(), 'CV for a new role')

test('a revised copy preserves contacts, photo and supplements without mutating the confirmed source', () => {
  const original = source(), before = structuredClone(original)
  const revision = createRevisionDraft(original, 'Revised')
  revision.draft.professional.skills[0].skillCode = 'PYTHON'
  revision.draft.professional.personal[0].value = 'changed@example.test'
  assert.deepEqual(original, before)
  assert.equal(revision.draft.professional.personal[1].imageBase64, 'QUJD')
  assert.deepEqual(revision.draft.professional.other, original.draft.professional.other)
  const body = revisionPayload(revision)
  assert.deepEqual(Object.keys(body).sort(), ['displayName', 'draft', 'expectedRevision'])
  for (const field of ['confirmed', 'filename', 'originalAvailable', 'original_text', 'cloud_revision', 'sourceId']) assert.equal(Object.hasOwn(body, field), false)
  body.draft.professional.other[0].value = 'Changed transport copy'
  assert.notEqual(body.draft.professional.other[0].value, revision.draft.professional.other[0].value)
  assert.throws(() => createRevisionDraft({ ...original, confirmed: 0 }, 'Copy'), /confirmed CV/)
})

test('a local draft round-trips Unicode, photos and multi-line sections for the same source revision', () => {
  const value = edited(); value.displayName = '\u4ea7\u54c1\u7ecf\u7406\u7b80\u5386 — \u4fee\u8ba2'
  assert.deepEqual(restoreRevisionDraft(exportRevisionDraft(value), source()), value)
  assert.throws(() => restoreRevisionDraft(exportRevisionDraft(value), { ...source(), id: newId }), /different CV/)
  assert.throws(() => restoreRevisionDraft(exportRevisionDraft(value), { ...source(), revision: 3 }), /different CV/)
})

test('malformed or oversized restored drafts are rejected before reaching the editor', () => {
  assert.throws(() => restoreRevisionDraft('not JSON', source()), /valid revision/)
  assert.throws(() => restoreRevisionDraft('x'.repeat(2 * 1024 * 1024 + 1), source()), /2 MB/)
  const bad = JSON.parse(exportRevisionDraft(edited())); bad.draft.professional.other[0].value = { unexpected: 'object' }
  assert.throws(() => restoreRevisionDraft(JSON.stringify(bad), source()), /unsupported structure/)
  bad.draft.professional.other = []; bad.draft.professional.skills = [{}]
  assert.throws(() => restoreRevisionDraft(JSON.stringify(bad), source()), /unsupported structure/)
  const named = edited(); named.displayName = ' '
  assert.throws(() => revisionPayload(named), /1–120/)
  assert.throws(() => revisionPayload({ ...edited(), displayName: {} }), /1–120/)
})

test('a lost response freezes the pending payload and retries the same create operation key', async () => {
  const calls = []
  const saver = createRevisionSaver(async (...args) => { calls.push(args); if (calls.length === 1) throw new Error('Connection lost'); return { id: newId, status: 'REVIEW_REQUIRED', confirmed: false } })
  const value = edited()
  await assert.rejects(saver.save(value), /Connection lost/)
  assert.equal(saver.hasPending, true)
  await assert.rejects(saver.save({ ...value, displayName: 'Different copy' }), /previous save result/)
  assert.equal(calls.length, 1)
  const result = await saver.save(value)
  assert.deepEqual(calls[0], calls[1])
  assert.equal(result.id, newId)
  assert.equal(saver.hasPending, false)
})

test('returning the original record or a confirmed copy is never presented as a successful revision', async () => {
  for (const wrong of [{ id: sourceId, status: 'REVIEW_REQUIRED', confirmed: false }, { id: newId, status: 'CONFIRMED', confirmed: true }]) {
    const saver = createRevisionSaver(async () => wrong)
    await assert.rejects(saver.save(edited()), /new editable CV/)
    assert.equal(saver.hasPending, true)
  }
})

test('a definite validation failure allows corrected contents with a new operation key', async () => {
  const keys = []
  const saver = createRevisionSaver(async (_id, _body, key) => { keys.push(key); if (keys.length === 1) throw Object.assign(new Error('Rejected'), { status: 422 }); return { id: newId, status: 'REVIEW_REQUIRED', confirmed: 0 } })
  await assert.rejects(saver.save(edited()), /Rejected/)
  assert.equal(saver.hasPending, false)
  await saver.save({ ...edited(), displayName: 'Corrected copy' })
  assert.notEqual(keys[0], keys[1])
})
