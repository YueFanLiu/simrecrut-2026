// Exercise the connected contract without creating real accounts or sending email.
import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

test('JWT identity uses the new envelope, public requests, and clears incomplete sessions', async () => {
  const previousLocation = globalThis.location
  let redirects = 0
  globalThis.location = { protocol: 'http:', pathname: '/register', assign: () => redirects++ }
  const server = await createServer({ mode: 'production',
    root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const api = await server.ssrLoadModule('/src/api/login.js')
    const { default: useUserStore } = await server.ssrLoadModule('/src/store/modules/user.js')
    const { createPinia } = await server.ssrLoadModule('pinia')
    const seen = []
    let failIdentity = false
    request.defaults.adapter = async config => {
      seen.push(config)
      let data = {}
      if (config.url.endsWith('/registration/code')) {
        data = { challengeId: 'challenge-id', expiresInSeconds: 600, resendAfterSeconds: 60 }
      }
      if (config.url.endsWith('/login')) data = { accessToken: 'test-token', tokenType: 'Bearer' }
      if (config.url.endsWith('/me')) {
        if (failIdentity) {
          config.silent = true
          throw Object.assign(new Error('Identity unavailable'), { config })
        }
        data = { username: 'candidate_test', roles: ['CANDIDATE'],
          permissions: ['simrecrut:resume:upload'] }
      }
      return { config, status: 200, data: { code: 0, data } }
    }
    const code = await api.sendRegistrationCode({ email: 'test@example.org' })
    assert.equal(code.data.resendAfterSeconds, 60)
    assert.equal(seen.at(-1).url, '/api/v1/auth/registration/code')
    assert.equal(seen.at(-1).headers.Authorization, undefined)
    assert.equal(seen.at(-1).timeout, 35000)
    const registration = { username: 'candidate_test', email: 'test@example.org',
      password: 'A long passphrase', passwordConfirmation: 'A long passphrase',
      accountType: 'CANDIDATE', challengeId: 'challenge-id', emailCode: '123456',
      agreedToTerms: true }
    await api.register(registration)
    assert.equal(seen.at(-1).url, '/api/v1/auth/register')
    assert.deepEqual(JSON.parse(seen.at(-1).data), registration)
    assert.equal(seen.at(-1).headers.Authorization, undefined)
    const user = useUserStore(createPinia())
    await user.Login({ identifier: 'test@example.org', password: 'A long passphrase' })
    assert.equal(user.token, 'test-token')
    assert.deepEqual(user.roles, ['candidate'])
    assert.deepEqual(user.permissions, ['simrecrut:resume:upload'])
    assert.equal(user.username, 'candidate_test')
    const signIn = seen.find(config => config.url.endsWith('/login'))
    assert.equal(signIn.url, '/api/v1/auth/login')
    assert.deepEqual(JSON.parse(signIn.data), {
      identifier: 'test@example.org', password: 'A long passphrase' })
    await user.LogOut()
    assert.equal(seen.at(-1).url, '/api/v1/auth/logout')
    assert.equal(user.loaded, false)
    assert.equal(user.token, '')
    failIdentity = true
    await assert.rejects(user.Login({ identifier: 'test@example.org', password: 'pass' }),
      /Identity unavailable/)
    assert.equal(user.token, '')
    assert.equal(user.loaded, false)
    assert.deepEqual(user.roles, [])
    request.defaults.adapter = async config => {
      throw Object.assign(new Error('Unauthorized'), { config,
        response: { status: 401, data: { message: 'Invalid username/email or password.' } } })
    }
    await assert.rejects(api.login({ identifier: 'wrong', password: 'wrong' }), /Invalid username/)
    assert.equal(redirects, 0, 'Public login failures must stay on the form')
    await assert.rejects(request({ url: '/api/v1/auth/me', silent: true }))
    assert.equal(redirects, 1, 'Protected requests must redirect an expired session')
  } finally {
    await server.close()
    if (previousLocation === undefined) delete globalThis.location
    else globalThis.location = previousLocation
  }
})
