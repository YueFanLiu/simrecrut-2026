import test from 'node:test'
import assert from 'node:assert/strict'
import { listFrozenProtocols, readFrozenProtocol, filterFrozenProtocols, protocolProjection } from '../src/mock/admin-protocols.js'
import { createAdminModels } from '../src/mock/admin-models.js'
import { allowedPermissions } from '../src/mock/admin-roles.js'
const admin = { role: 'Admin', username: 'admin' }

test('frozen protocol reads enforce the demo Admin role', () => {
  for (const role of ['Candidate', 'HR', 'Researcher', 'Reviewer', null]) {
    assert.throws(() => listFrozenProtocols({ role }), /Admin/)
    assert.throws(() => readFrozenProtocol('P-2026.01', { role }), /Admin/)
  }
  assert.throws(() => readFrozenProtocol('unknown', admin), /not found/)
})
test('projection omits unrelated private fields and returns independent model type arrays', () => {
  const original = listFrozenProtocols(admin)[0]
  const projected = protocolProjection({ ...original, weights: 'PRIVATE', cv: 'PRIVATE', humanReason: 'PRIVATE' })
  assert.equal(JSON.stringify(projected).includes('PRIVATE'), false)
  projected.modelTypes.length = 0
  assert.equal(original.modelTypes.length, 2)
})
test('reads and caller modifications never change the frozen fixture', () => {
  const before = JSON.stringify(listFrozenProtocols(admin))
  const detail = readFrozenProtocol('P-2026.01', admin)
  detail.cutoff = .99; detail.version = 'CHANGED'; detail.modelTypes.push('CHANGED')
  assert.equal(JSON.stringify(listFrozenProtocols(admin)), before)
  assert.equal(readFrozenProtocol('P-2026.01', admin).cutoff, .5)
})
test('combined filters support both model types without changing the catalog', () => {
  const rows = listFrozenProtocols(admin), before = JSON.stringify(rows)
  for (const type of ['Neutral ML', 'Biased ML']) assert.equal(filterFrozenProtocols(rows, { keyword: ' FS-32 ', type, status: 'Frozen' }).length, 1)
  assert.equal(filterFrozenProtocols(rows, { keyword: 'unknown' }).length, 0)
  assert.equal(filterFrozenProtocols(rows, { status: 'Active' }).length, 0)
  assert.equal(JSON.stringify(rows), before)
})
test('metadata agrees with model references and never fabricates missing configuration', () => {
  const detail = readFrozenProtocol('P-2026.01', admin)
  for (const row of createAdminModels().models) {
    assert.equal(detail.version, row.protocol); assert.equal(detail.schema, row.schema)
    assert.equal(detail.preprocessing, row.preprocessing); assert.equal(detail.cutoff, row.cutoff)
  }
  assert.match(detail.labels, /Unavailable/); assert.match(detail.encoding, /Unavailable/)
  assert.equal(detail.frozenAt, null)
  assert.deepEqual(allowedPermissions('Admin').filter(id => id.startsWith('admin.protocols')), ['admin.protocols.view'])
  assert.equal(allowedPermissions('Researcher').includes('admin.protocols.view'), false)
})
