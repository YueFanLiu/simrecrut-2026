import test from 'node:test'
import assert from 'node:assert/strict'
import { checkFreshness, createHealthSummary, healthFreshnessLimit, loadHealthSummary } from '../src/mock/admin-health.js'
const admin = { role: 'Admin' }
test('health metadata requires the demo Admin role', async () => {
  for (const role of ['Candidate', 'HR', 'Researcher', null]) {
    assert.throws(() => createHealthSummary({ role }), /Admin/)
    await assert.rejects(loadHealthSummary({ role }), /Admin/)
  }
})
test('unconnected runtime components never claim health, loaded models or check timestamps', () => {
  const summary = createHealthSummary(admin)
  assert.deepEqual(summary.components.map(row => row.id), ['backend', 'mysql', 'redis', 'mlService', 'tempWorkspace'])
  assert.ok(summary.components.every(row => row.status === 'Unknown' && row.checkedAt === null))
  assert.ok(summary.models.every(row => row.version === null && row.status === 'Unknown'))
  assert.equal(summary.workspaceWritable, null); assert.equal(summary.cleanupStatus, null)
  assert.equal(summary.checkedAt, null)
  for (const term of ['uptime', 'password', 'artifactPath', 'rawCv', 'humanReason', 'cpuUsage']) assert.equal(JSON.stringify(summary).includes(term), false)
})
test('freshness treats missing, invalid and future timestamps as unavailable and ages at the boundary', () => {
  const time = Date.parse('2026-10-10T10:00:00.000Z'), date = '2026-10-10T10:00:00.000Z'
  for (const input of [null, '', 'invalid', '2026-10-10T11:00:00.000Z']) assert.equal(checkFreshness(input, time), 'Unavailable')
  assert.equal(checkFreshness(date, time + healthFreshnessLimit - 1), 'Fresh')
  assert.equal(checkFreshness(date, time + healthFreshnessLimit), 'Stale')
})
test('refresh returns independent metadata without converting browser time to a runtime check', async () => {
  const first = await loadHealthSummary(admin); first.components[0].status = 'CHANGED'; first.models[0].version = 'CHANGED'
  const next = await loadHealthSummary(admin)
  assert.equal(next.components[0].status, 'Unknown'); assert.equal(next.models[0].version, null); assert.equal(next.checkedAt, null)
})
test('empty/error and explicit stale fixtures preserve unknown health', async () => {
  assert.equal(await loadHealthSummary(admin, 'empty'), null)
  await assert.rejects(loadHealthSummary(admin, 'load-error'), /could not be loaded/)
  const stale = createHealthSummary(admin, 'stale')
  assert.equal(checkFreshness(stale.checkedAt, Date.parse('2026-10-10T00:00:00.000Z')), 'Stale')
  assert.ok(stale.components.every(row => row.status === 'Unknown'))
})
