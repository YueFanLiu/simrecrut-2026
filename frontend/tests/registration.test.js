import test from 'node:test'
import assert from 'node:assert/strict'
import { registerDemo, loginDemo } from '../src/mock/auth.js'
import {
  validateUsername, validateEmail, validateRegistrationPassword,
  validatePasswordConfirmation, validateAgreement,
} from '../src/utils/registration-validation.js'

function errorMessage(validator, value) {
  let message
  validator({}, value, (error) => { message = error?.message })
  return message
}

test('registration rules reject missing fields and malformed values', () => {
  assert.match(errorMessage(validateUsername, ''), /enter a username/)
  for (const value of ['ab', 'a'.repeat(21), 'user name', 'user@name']) {
    assert.match(errorMessage(validateUsername, value), /3-20/)
  }
  assert.equal(errorMessage(validateUsername, 'Student_01'), undefined)
  assert.match(errorMessage(validateEmail, ''), /enter your email/)
  assert.match(errorMessage(validateEmail, 'user@example'), /valid email/)
  assert.equal(errorMessage(validateEmail, ' student@example.test '), undefined)
  assert.match(errorMessage(validateRegistrationPassword, ''), /create a password/)
  for (const value of ['abc123', 'abcdefgh', '12345678']) {
    assert.match(errorMessage(validateRegistrationPassword, value), /8 characters/)
  }
  assert.equal(errorMessage(validateRegistrationPassword, 'Student123!'), undefined)
  assert.match(validatePasswordConfirmation('Student123!', ''), /confirm/)
  assert.match(validatePasswordConfirmation('Student123!', 'Student124!'), /do not match/)
  assert.equal(validatePasswordConfirmation('Student123!', 'Student123!'), '')
  assert.match(errorMessage(validateAgreement, false), /agree/)
  assert.equal(errorMessage(validateAgreement, true), undefined)
})

test('registration creates only a Candidate demo account without signing it in', async () => {
  const details = {
    username: ' Student_01 ',
    email: 'STUDENT@example.test',
    password: 'Student123!',
    agreedToTerms: true,
    role: 'Admin',
  }
  const account = await registerDemo(details)
  assert.deepEqual(account, {
    username: 'Student_01', displayName: 'Student_01', role: 'Candidate',
  })
  assert.equal('password' in account, false)
  const signedIn = await loginDemo({ identifier: 'student_01', password: details.password })
  assert.equal(signedIn.role, 'Candidate')
  const signedInByEmail = await loginDemo({
    identifier: ' STUDENT@EXAMPLE.TEST ', password: details.password,
  })
  assert.equal(signedInByEmail.username, 'Student_01')
  await assert.rejects(loginDemo({ identifier: 'student_01', password: 'wrong' }), /Invalid username/)
})

test('duplicate username and email errors are case insensitive and allow correction', async () => {
  const details = { username: 'Unique_01', email: 'unique@example.test', password: 'Student123!', agreedToTerms: true }
  await assert.rejects(registerDemo({ ...details, username: 'DEMO' }), /username is already used/)
  await assert.rejects(registerDemo({ ...details, email: 'DEMO@SIMRECRUT.TEST' }), /email is already used/)
  await assert.rejects(registerDemo({ ...details, agreedToTerms: false }), /agree/)
  await registerDemo(details)
  await assert.rejects(registerDemo({ ...details, username: 'UNIQUE_01', email: 'other@example.test' }), /username is already used/)
  await assert.rejects(registerDemo({ ...details, username: 'Other_01', email: 'UNIQUE@example.test' }), /email is already used/)
})
