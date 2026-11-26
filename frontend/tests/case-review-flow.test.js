import test from 'node:test'
import assert from 'node:assert/strict'
import { createCaseReviewFlow } from '../src/utils/case-review-flow.js'

function fixture(overrides = {}) {
  const calls = []
  let revision = 3, keys = 0
  const facts = { professional: { skills: [{ skillCode: 'JAVA' }] }, confirmedAbsent: ['PROJECTS'] }
  const api = Object.fromEntries(['saveDraft', 'saveAttributes', 'saveModel', 'confirm'].map(action => [action, async (body, key) => {
    calls.push({ action, body: structuredClone(body), key })
    assert.equal(body.revision, revision)
    revision++
    return { revision }
  }]))
  api.readDraft = async () => {
    calls.push({ action: 'readDraft' })
    return { ...structuredClone(facts), revision, unresolvedRequiredFields: [] }
  }
  api.readModel = async () => {
    calls.push({ action: 'readModel' })
    return { revision, biography: 'Regenerated text for corrected facts' }
  }
  Object.assign(api, overrides)
  const input = { revision, facts, factsChanged: true,
    attributes: [{ type: 'GENDER', value: 'UNKNOWN' }], attributesChanged: true,
    model: { useText: true, usePortrait: true, name: 'Candidate', biography: 'Manually reviewed text', portraitIndex: -1, portraitBase64: 'QUJD' }, modelTextEdited: true, confirm: true }
  return { calls, api, input, flow: createCaseReviewFlow(api, () => `operation-${++keys}`) }
}

test('one confirmation saves facts, attributes and reviewed model inputs in revision order', async () => {
  const { flow, input, calls } = fixture()
  const before = structuredClone(input), updates = []
  const result = await flow.run(input, (state, action) => updates.push([action, state.revision]))
  assert.deepEqual(calls.map(call => call.action), ['saveDraft', 'readDraft', 'saveAttributes', 'saveModel', 'confirm'])
  assert.deepEqual(calls.filter(call => call.body).map(call => call.body.revision), [3, 4, 5, 6])
  assert.equal(calls[3].body.biography, 'Manually reviewed text')
  assert.equal(calls[3].body.portraitBase64, 'QUJD')
  assert.deepEqual(input, before)
  assert.equal(result.revision, 7)
  assert.equal(updates.length, 5)
  assert.equal(flow.hasPending, false)
})

test('unchanged optional defaults are omitted without a separate model-save click', async () => {
  const { flow, input, calls } = fixture()
  input.factsChanged = false; input.attributesChanged = false
  input.model = { useText: false, usePortrait: false, name: 'Unread extracted name', biography: 'hidden@example.test', portraitIndex: 0, portraitBase64: 'DORMANT' }
  await flow.run(input)
  assert.deepEqual(calls.map(call => call.action), ['saveModel', 'confirm'])
  assert.equal(calls[0].body.useText, false)
  assert.equal(calls[0].body.usePortrait, false)
  assert.equal(calls[0].body.name, '')
  assert.equal(calls[0].body.biography, '')
  assert.equal(calls[0].body.portraitIndex, -1)
  assert.equal(Object.hasOwn(calls[0].body, 'portraitBase64'), false)
})

test('saving an incomplete draft never confirms; older protocols need no model inputs', async () => {
  const { flow, input, calls } = fixture()
  input.confirm = false; input.model = null
  await flow.run(input)
  assert.deepEqual(calls.map(call => call.action), ['saveDraft', 'readDraft', 'saveAttributes'])
})

test('a lost model response freezes its original request and resumes without replaying completed steps', async () => {
  const { flow, input, calls, api } = fixture()
  const save = api.saveModel, attempts = []
  api.saveModel = async (body, key) => {
    attempts.push({ body: structuredClone(body), key })
    if (attempts.length === 1) throw new Error('Connection lost')
    return save(body, key)
  }
  await assert.rejects(flow.run(input), /Connection lost/)
  assert.equal(flow.hasPending, true)
  input.model.biography = 'Must not replace pending request'; input.confirm = false
  const result = await flow.run(input)
  assert.deepEqual(attempts[0], attempts[1])
  assert.equal(result.confirm, true)
  assert.equal(calls.filter(call => call.action === 'saveDraft').length, 1)
  assert.equal(calls.at(-1).action, 'confirm')
})

test('a failed refresh retries the read without saving facts twice', async () => {
  const { flow, input, calls, api } = fixture()
  const read = api.readDraft
  let attempts = 0
  api.readDraft = async () => { if (++attempts === 1) throw new Error('Read unavailable'); return read() }
  await assert.rejects(flow.run(input), /Read unavailable/)
  await flow.run(input)
  assert.equal(calls.filter(call => call.action === 'saveDraft').length, 1)
  assert.equal(attempts, 2)
})

test('a lost confirmation response can retry after the case has already been locked', async () => {
  const { flow, input, api, calls } = fixture()
  const confirm = api.confirm, attempts = []
  let receipt
  api.confirm = async (body, key) => {
    attempts.push({ body: structuredClone(body), key })
    if (!receipt) { receipt = await confirm(body, key); throw new Error('Response lost after confirmation') }
    return receipt
  }
  await assert.rejects(flow.run(input), /Response lost/)
  const result = await flow.run(input)
  assert.deepEqual(attempts[0], attempts[1])
  assert.equal(result.confirm, true)
  assert.equal(calls.filter(call => call.action === 'confirm').length, 1)
})

test('definitive validation failure allows correction and prevents confirmation', async () => {
  const { flow, input, api, calls } = fixture()
  api.saveModel = async () => { throw Object.assign(new Error('Remove contact details'), { status: 400 }) }
  await assert.rejects(flow.run(input), /Remove contact details/)
  assert.equal(flow.hasPending, false)
  assert.equal(calls.some(call => call.action === 'confirm'), false)
})

test('a concurrent revision change blocks later writes and confirmation', async () => {
  const { flow, input, api, calls } = fixture()
  api.readDraft = async () => ({ ...input.facts, revision: 100 })
  await assert.rejects(flow.run(input), error => error.status === 409)
  assert.equal(flow.hasPending, false)
  assert.deepEqual(calls.map(call => call.action), ['saveDraft'])
})

test('changed automatic model text pauses confirmation until the user reviews it, keeping prior text recoverable', async () => {
  const { flow, input, calls } = fixture()
  input.modelTextEdited = false
  const result = await flow.run(input)
  assert.deepEqual(calls.map(call => call.action), ['saveDraft', 'readDraft', 'readModel'])
  assert.equal(result.requiresModelReview, true)
  assert.equal(result.model.biography, 'Regenerated text for corrected facts')
  assert.equal(result.previousModelText, 'Manually reviewed text')
  assert.equal(flow.hasPending, false)
  // The page requires a new acknowledgement, then starts a fresh operation.
  await flow.run({ ...input, revision: result.revision, factsChanged: false, model: result.model })
  assert.deepEqual(calls.slice(3).map(call => call.action), ['saveAttributes', 'saveModel', 'confirm'])
  assert.equal(calls.find(call => call.action === 'saveModel').body.biography, result.model.biography)
})

test('manually edited text is preserved without reading and overwriting it with automatic text', async () => {
  const { flow, input, calls } = fixture()
  input.model.biography = 'Custom description checked against the new CV'
  await flow.run(input)
  assert.equal(calls.some(call => call.action === 'readModel'), false)
  assert.equal(calls.find(call => call.action === 'saveModel').body.biography, input.model.biography)
  assert.equal(calls.at(-1).action, 'confirm')
})

test('omitted text can refresh without an extra review or accidentally enabling a modality', async () => {
  const { flow, input, calls } = fixture()
  input.modelTextEdited = false; input.model.useText = false; input.model.usePortrait = false
  const result = await flow.run(input)
  assert.equal(result.requiresModelReview, false)
  assert.equal(calls.at(-1).action, 'confirm')
  const model = calls.find(call => call.action === 'saveModel').body
  assert.equal(model.biography, '')
  assert.equal(model.useText, false)
  assert.equal(model.usePortrait, false)
})

test('a failed automatic-text refresh retries only that read and still pauses before confirmation', async () => {
  const { flow, input, api, calls } = fixture()
  input.modelTextEdited = false
  const read = api.readModel
  let attempts = 0
  api.readModel = async () => { if (++attempts === 1) throw new Error('Temporary read failure'); return read() }
  await assert.rejects(flow.run(input), /Temporary read failure/)
  assert.equal(flow.hasPending, true)
  const result = await flow.run(input)
  assert.equal(result.requiresModelReview, true)
  assert.equal(calls.filter(call => call.action === 'saveDraft').length, 1)
  assert.equal(calls.some(call => call.action === 'confirm'), false)
})
