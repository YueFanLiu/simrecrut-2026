import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

test('password recovery uses public independent endpoints and the code-zero envelope', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const api = await server.ssrLoadModule('/src/api/login.js')
    const seen = []
    request.defaults.adapter = async config => {
      seen.push(config)
      return { config, status: 200, data: { code: 0, message: 'Completed.', requestId: 'transport-id',
        data: { emailEnabled: true, challengeId: 'challenge-id', expiresInSeconds: 600, resendAfterSeconds: 60 } } }
    }
    assert.equal((await api.passwordResetOptions()).data.emailEnabled, true)
    assert.equal((await api.sendPasswordCode({ email: 'one@example.org' })).data.challengeId, 'challenge-id')
    await api.resetPassword({ email: 'one@example.org', challengeId: 'challenge-id', emailCode: '123456',
      password: 'a sufficiently long password', passwordConfirmation: 'a sufficiently long password' })
    assert.deepEqual(seen.map(item => item.url), ['/api/v1/auth/password-reset/options',
      '/api/v1/auth/password-reset/code', '/api/v1/auth/password-reset'])
    assert.ok(seen.every(item => item.publicRequest === true))
    assert.ok(seen.every(item => !item.headers.Authorization))
    const payload = JSON.parse(seen[2].data)
    assert.equal(payload.challengeId, 'challenge-id')
    assert.equal(payload.uuid, undefined)
    assert.equal(payload.requestId, undefined)
    assert.equal(seen[1].timeout, 35000)
  } finally { await server.close() }
})
