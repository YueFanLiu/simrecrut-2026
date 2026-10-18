export function validateUsername(_rule, value, callback) {
  const username = value.trim()
  if (!username) {
    callback(new Error('Please enter a username.'))
  } else if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
    callback(new Error('Use 3-20 characters: letters, numbers or underscores.'))
  } else {
    callback()
  }
}

export function validateEmail(_rule, value, callback) {
  const email = value.trim()
  if (!email) {
    callback(new Error('Please enter your email address.'))
  } else if (email.length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    callback(new Error('Please enter a valid email address of up to 100 characters.'))
  } else {
    callback()
  }
}

export function validateRegistrationPassword(_rule, value, callback) {
  if (!value) {
    callback(new Error('Please create a password.'))
  } else if (value.length < 8 || value.length > 20 || !/[A-Za-z]/.test(value) || !/[0-9]/.test(value)) {
    callback(new Error('Use 8-20 characters, including letters and numbers.'))
  } else {
    callback()
  }
}

export function validatePasswordConfirmation(password, confirmation) {
  if (!confirmation) return 'Please confirm your password.'
  if (confirmation !== password) return 'Passwords do not match.'
  return ''
}

export function validateAgreement(_rule, value, callback) {
  callback(value ? undefined : new Error('Please read the registration and privacy notices.'))
}
