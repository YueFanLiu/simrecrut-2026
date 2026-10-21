import test from 'node:test'
import assert from 'node:assert/strict'
import { failureProjection, filterFailures, listFailures, loadFailures, readFailure, validateFailureDates } from '../src/mock/admin-failures.js'
import { allowedPermissions } from '../src/mock/admin-roles.js'
const admin = { role: 'Admin' }
test('failure summaries and details require Admin and exact task identity', async () => {
  for (const role of ['Candidate', 'HR', 'Researcher', null]) {
    assert.throws(() => listFailures({ role }), /Admin/)
    await assert.rejects(readFailure('DEMO-TASK-001', { role }), /Admin/)
  }
  await assert.rejects(readFailure('DEMO-RESOURCE-001', admin), /not found/)
  assert.equal((await readFailure('DEMO-TASK-001', admin)).taskId, 'DEMO-TASK-001')
})
test('explicit safe projections discard raw messages, content, stack traces and extra fields', () => {
  const row = listFailures(admin)[0]
  const projected = failureProjection({ ...row, message: 'SECRET CV', rawCv: 'SECRET CV', humanReason: 'SECRET', stack: 'SECRET', sql: 'SECRET', password: 'SECRET' })
  assert.equal(JSON.stringify(projected).includes('SECRET'), false)
  assert.match(projected.message, /did not respond/)
  assert.equal(failureProjection({ ...row, errorCode: 'SECRET CV' }).errorCode, 'UNAVAILABLE')
  assert.equal(failureProjection({ ...row, errorCode: 'SECRET CV' }).message, 'Safe error information is unavailable.')
})
test('keyword, stage, status and inclusive UTC date filters combine without altering fixtures', () => {
  const rows = listFailures(admin), before = JSON.stringify(rows)
  assert.equal(filterFailures(rows, { keyword: ' demo-task-001 ', stage: 'Extraction', status: 'Failed (retryable)', from: '2026-10-09', to: '2026-10-09' }).length, 1)
  assert.equal(filterFailures(rows, { keyword: 'DEMO-RESOURCE-001' }).length, 1)
  assert.equal(filterFailures(rows, { from: '2026-10-08', to: '2026-10-08' }).length, 3)
  assert.equal(filterFailures(rows, { status: 'Queued' }).length, 0)
  assert.equal(filterFailures(rows, { from: '2026-10-10', to: '2026-10-09' }).length, 0)
  assert.match(validateFailureDates({ from: '2026-10-10', to: '2026-10-09' }), /start date/)
  for (const value of ['2026-02-30', 'invalid', '2026-1-1', '2026-13-01']) assert.match(validateFailureDates({ from: value }), /valid date/)
  assert.equal(validateFailureDates({ from: '2024-02-29' }), '')
  assert.equal(JSON.stringify(rows), before)
})
test('caller changes and refresh cannot mutate tasks or claim successful completion', async () => {
  const rows = listFailures(admin); rows[0].status = 'Complete'; rows[0].cleanupStatus = 'Complete'
  const refreshed = await loadFailures(admin)
  assert.equal(refreshed[0].status, 'Failed (retryable)'); assert.equal(refreshed[0].cleanupStatus, 'Pending')
  assert.ok(refreshed.every(row => row.status.startsWith('Failed')))
  assert.deepEqual(allowedPermissions('Admin').filter(code => code.startsWith('admin.failures.')), ['admin.failures.view'])
})
test('empty, list-error and detail-error fixtures do not alter subsequent reads', async () => {
  assert.equal((await loadFailures(admin, 'empty')).length, 0)
  await assert.rejects(loadFailures(admin, 'load-error'), /could not be loaded/)
  await assert.rejects(readFailure('DEMO-TASK-001', admin, 'detail-error'), /could not be loaded/)
  assert.equal(listFailures(admin).length, 8)
})
