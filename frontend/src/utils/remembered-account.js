const storageKey = 'simrecrut.remembered-identifier'

export function readRememberedIdentifier() {
  try {
    return localStorage.getItem(storageKey) || ''
  } catch {
    return ''
  }
}

/** Store only the account identifier. Passwords and demo sessions are never persisted. */
export function saveRememberedIdentifier(identifier, rememberMe) {
  try {
    if (rememberMe) {
      localStorage.setItem(storageKey, identifier.trim())
    } else {
      localStorage.removeItem(storageKey)
    }
    return true
  } catch {
    return false
  }
}
