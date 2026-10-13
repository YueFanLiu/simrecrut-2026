// Verify the connected account transport without creating accounts in the shared database.
import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

test('account APIs use public RuoYi adapters and discard incomplete login state', async () => {
  const previousLocation = globalThis.location
  globalThis.location = { protocol: 'http:', pathname: '/login' }
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const api = await server.ssrLoadModule('/src/api/login.js')
    const { default: useUserStore } = await server.ssrLoadModule('/src/store/modules/user.js')
    const { createPinia } = await server.ssrLoadModule('pinia')
    const seen = []
    request.defaults.adapter = async config => {
      seen.push(config)
      let data = { code: 200 }
      if (config.url.endsWith('/options')) data.registrationEnabled = true
      if (config.url.endsWith('/login')) data.token = 'test-token'
      if (config.url === '/getInfo') {
        config.silent = true
        const failure = new Error('Identity unavailable'); failure.config = config; throw failure
      }
      return { config, status: 200, data }
    }
    assert.equal((await api.getRegistrationOptions()).registrationEnabled, true)
    await api.register({ username: 'candidate_test', email: 'test@example.org', password: 'ExamplePass7' })
    assert.equal(seen.at(-1).url, '/simrecrut/account/register')
    assert.equal(seen.at(-1).method, 'post')
    assert.equal(seen.at(-1).silent, true)
    assert.equal(seen.at(-1).headers.Authorization, undefined)
    await api.passwordResetOptions()
    assert.equal(seen.at(-1).url, '/simrecrut/account/password/options')
    await api.sendPasswordCode({ email: 'test@example.org', code: 'challenge', uuid: 'uuid' })
    assert.equal(seen.at(-1).url, '/simrecrut/account/password/code')
    assert.equal(seen.at(-1).timeout, 35000)
    assert.equal(seen.at(-1).headers.Authorization, undefined)
    await api.resetPassword({ email: 'test@example.org', requestId: 'request', emailCode: '123456', password: 'ExamplePass7', confirmation: 'ExamplePass7' })
    assert.equal(seen.at(-1).url, '/simrecrut/account/password/reset')
    assert.equal(seen.at(-1).headers.Authorization, undefined)
    const user = useUserStore(createPinia())
    await assert.rejects(user.Login({ username: 'test@example.org', password: 'ExamplePass7', code: 'challenge', uuid: 'uuid' }), /Identity unavailable/)
    assert.equal(user.token, '')
    assert.equal(user.loaded, false)
    assert.deepEqual(user.roles, [])
    const signIn = seen.find(config => config.url.endsWith('/login'))
    assert.equal(signIn.url, '/simrecrut/account/login')
    assert.equal(signIn.headers.Authorization, undefined)
    assert.equal(JSON.parse(signIn.data).username, 'test@example.org')
  } finally { await server.close(); if (previousLocation === undefined) delete globalThis.location; else globalThis.location = previousLocation }
})
