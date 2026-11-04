export function validateIdentifier(_rule, value, callback) {
  const identifier = value.trim()
  if (!identifier) {
    callback(new Error('Please enter your username or email.'))
  } else if (identifier.length > 254) {
    callback(new Error('Use no more than 254 characters.'))
  } else if (identifier.includes('@') && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identifier)) {
    callback(new Error('Please enter a valid email address.'))
  } else if (/\s/.test(identifier)) {
    callback(new Error('Username cannot contain spaces.'))
  } else {
    callback()
  }
}

export function validatePassword(_rule, value, callback) {
  callback(value ? undefined : new Error('Please enter your password.'))
}
