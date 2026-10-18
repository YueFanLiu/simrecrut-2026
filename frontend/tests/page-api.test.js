// Exercise the real transport adapter without writing to a shared database.
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

test('connected page APIs paginate RuoYi data and surface missing endpoints', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const admin = await server.ssrLoadModule('/src/api/administration.js')
    const jobs = await server.ssrLoadModule('/src/api/jobs.js')
    const seen = []
    request.defaults.adapter = async config => {
      seen.push(config)
      if (config.url.startsWith('/system/user/list')) {
        const second = config.url.includes('pageNum=2')
        return { config, status: 200, data: { code: 200, total: 2, rows: [{
          userId: second ? 2 : 1, userName: second ? 'candidate' : 'admin', nickName: 'Account', status: '0'
        }] } }
      }
      if (config.url.startsWith('/monitor/operlog/list')) return { config, status: 200,
        data: { code: 200, total: 1, rows: [{ operId: 7, operName: 'admin', status: 0,
          title: 'Users', operTime: '2026-10-12 12:00:00', operIp: 'private', operParam: 'private' }] } }
      const error = new Error('Missing resource')
      error.config = config; error.response = { status: 404, data: {} }
      throw error
    }
    const users = await admin.users()
    assert.equal(users.length, 2)
    assert.equal(users[0].role, 'Admin')
    assert.equal(users[1].role, 'Unassigned')
    const events = await admin.audit({ actor: 'admin', outcome: 'SUCCESS' }, 2, 5)
    assert.equal(events.total, 1)
    assert.equal(events.items[0].outcome, 'SUCCESS')
    assert.equal(events.items[0].operIp, undefined)
    assert.equal(events.items[0].operParam, undefined)
    const auditUrl = new URL(seen.at(-1).url, 'http://local')
    assert.equal(auditUrl.searchParams.get('pageNum'), '2')
    assert.equal(auditUrl.searchParams.get('operName'), 'admin')
    await assert.rejects(admin.audit({ actionCode: 'JOB_PUBLISHED' }, 1, 5), /business audit API/)
    await assert.rejects(jobs.publishedJobs(), /has not implemented/)
    assert.ok(seen.every(config => config.silent === true))
    request.defaults.adapter = async config => ({ config, status: 200,
      data: { code: 500, msg: 'No static resource api/v1/hr/jobs.' } })
    await assert.rejects(jobs.managedJobs(), /has not implemented/)
  } finally { await server.close() }
})
