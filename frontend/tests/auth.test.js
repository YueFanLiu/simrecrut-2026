import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/store/modules/auth.js'
import { validateIdentifier, validatePassword } from '../src/utils/login-validation.js'
import { readRememberedIdentifier, saveRememberedIdentifier } from '../src/utils/remembered-account.js'

function validationError(validator, value) {
  let result
  validator({}, value, (error) => { result = error?.message })
  return result
}

test('login input validation handles empty fields and malformed emails', () => {
  assert.match(validationError(validateIdentifier, '  '), /enter your username/)
  assert.match(validationError(validateIdentifier, 'user@example'), /valid email/)
  assert.match(validationError(validateIdentifier, 'demo user'), /spaces/)
  assert.match(validationError(validateIdentifier, 'a'.repeat(255)), /254 characters/)
  assert.equal(validationError(validateIdentifier, 'demo'), undefined)
  assert.equal(validationError(validateIdentifier, ' demo@simrecrut.test '), undefined)
  assert.match(validationError(validatePassword, ''), /enter your password/)
})

test('demo store accepts username and email, rejects invalid credentials, and signs out', async () => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  assert.equal(auth.isDemoSignedIn, false)
  await assert.rejects(auth.login({ identifier: 'demo', password: 'wrong' }), /Invalid username/)
  assert.equal(auth.isDemoSignedIn, false)
  await auth.login({ identifier: ' DEMO ', password: 'SimRecrut123!' })
  assert.equal(auth.demoUser.displayName, 'Demo User')
  assert.equal('password' in auth.demoUser, false)
  auth.logout()
  assert.equal(auth.isDemoSignedIn, false)
  await auth.login({ identifier: 'demo@simrecrut.test', password: 'SimRecrut123!' })
  assert.equal(auth.isDemoSignedIn, true)
})

test('Remember Me saves only an identifier and handles unavailable storage', () => {
  const entries = new Map()
  globalThis.localStorage = {
    getItem: (key) => entries.get(key) ?? null,
    setItem: (key, value) => entries.set(key, value),
    removeItem: (key) => entries.delete(key),
  }
  try {
    assert.equal(saveRememberedIdentifier(' demo ', true), true)
    assert.equal(readRememberedIdentifier(), 'demo')
    assert.deepEqual([...entries.values()], ['demo'])
    assert.equal(saveRememberedIdentifier('demo', false), true)
    assert.equal(readRememberedIdentifier(), '')
    globalThis.localStorage = {
      getItem: () => { throw new Error('Storage unavailable') },
      setItem: () => { throw new Error('Storage unavailable') },
    }
    assert.equal(readRememberedIdentifier(), '')
    assert.equal(saveRememberedIdentifier('demo', true), false)
  } finally {
    delete globalThis.localStorage
  }
})
