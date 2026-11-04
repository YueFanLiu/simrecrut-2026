import test from 'node:test'
import assert from 'node:assert/strict'
import { auditProjection, listAuditEvents, queryAuditEvents, loadAuditEvents, validateAuditDates } from '../src/mock/admin-audit.js'
import { allowedPermissions } from '../src/mock/admin-roles.js'
const admin = { role: 'Admin' }
test('audit reads require Admin and expose only a protected View permission', async () => {
  for (const role of ['Candidate', 'HR', 'Researcher', null]) {
    assert.throws(() => listAuditEvents({ role }), /Admin/)
    await assert.rejects(loadAuditEvents({ role }, {}, 1, 5), /Admin/)
  }
  assert.deepEqual(allowedPermissions('Admin').filter(code => code.startsWith('admin.audit.')), ['admin.audit.view'])
})
test('audit projection rejects raw summaries, content, arbitrary state metadata and unsupported codes', () => {
  const source = { ...listAuditEvents(admin)[0], summary: 'PRIVATE', rawJson: 'PRIVATE', cv: 'PRIVATE', reason: 'PRIVATE', ip: 'PRIVATE', sessionId: 'PRIVATE', details: { text: 'PRIVATE' }, fromState: 'PRIVATE', toState: 'PRIVATE' }
  const projected = auditProjection(source)
  assert.equal(JSON.stringify(projected).includes('PRIVATE'), false)
  assert.equal(projected.fromState, null); assert.equal(projected.toState, null)
  const unknown = auditProjection({ ...source, actionCode: 'PRIVATE', resourceType: 'PRIVATE', outcome: 'PRIVATE' })
  assert.equal(unknown.actionCode, 'UNAVAILABLE'); assert.equal(unknown.outcome, 'Unavailable')
  assert.equal(JSON.stringify(unknown).includes('PRIVATE'), false)
})
test('action, actor, resource, outcome and inclusive UTC date filters combine before pagination', () => {
  const result = queryAuditEvents(admin, { actionCode: 'MODEL_ACTIVATED', actor: ' actor-001 ', resourceType: 'Model Version', outcome: 'SUCCESS', from: '2026-10-09', to: '2026-10-09' }, 1, 5)
  assert.equal(result.total, 1); assert.equal(result.items[0].eventId, 'DEMO-EVENT-001')
  assert.equal(queryAuditEvents(admin, { from: '2026-10-08', to: '2026-10-08' }, 1, 5).total, 3)
  assert.equal(queryAuditEvents(admin, { actor: 'unknown' }, 1, 5).total, 0)
  assert.equal(queryAuditEvents(admin, {}, 2, 5).items.length, 3)
  assert.equal(queryAuditEvents(admin, {}, 9, 5).page, 2)
  assert.throws(() => queryAuditEvents(admin, {}, 0, 5), /valid audit page/)
})
test('date validation rejects invalid dates and inverted ranges; leap days remain valid', () => {
  for (const from of ['2026-02-30', '2026-13-01', 'invalid', '2026-1-1']) assert.match(validateAuditDates({ from }), /valid date/)
  assert.equal(validateAuditDates({ from: '2024-02-29' }), '')
  assert.throws(() => queryAuditEvents(admin, { from: '2026-10-10', to: '2026-10-09' }, 1, 5), /start date/)
})
test('reads preserve removed actors and absent resource IDs; returned objects cannot mutate the fixture', () => {
  const records = listAuditEvents(admin), before = JSON.stringify(records)
  const cleanup = records.find(row => row.actionCode === 'CLEANUP_RECORDED')
  assert.equal(cleanup.actor, null); assert.equal(cleanup.resourceId, null); assert.equal(cleanup.toState, 'Pending')
  records[0].outcome = 'CHANGED'; records[0].requestId = 'CHANGED'
  assert.equal(JSON.stringify(listAuditEvents(admin)), before)
})
test('async query snapshots filters and error/empty scenarios cannot rewrite events', async () => {
  const filters = { outcome: 'SUCCESS' }, pending = loadAuditEvents(admin, filters, 1, 10)
  filters.outcome = 'FAILURE'
  const result = await pending; assert.equal(result.total, 6)
  assert.ok(result.items.every(row => row.outcome === 'SUCCESS'))
  assert.equal((await loadAuditEvents(admin, {}, 1, 5, 'empty')).total, 0)
  await assert.rejects(loadAuditEvents(admin, {}, 1, 5, 'load-error'), /could not be loaded/)
  assert.equal(listAuditEvents(admin).length, 8)
})
