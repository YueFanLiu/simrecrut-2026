export const demoAccount = Object.freeze({
  username: 'demo',
  email: 'demo@simrecrut.test',
  password: 'SimRecrut123!',
  displayName: 'Demo User',
})

export const hrDemoAccount = Object.freeze({
  username: 'hr', email: 'hr@simrecrut.test', password: 'SimRecrut123!',
  displayName: 'Demo HR User', role: 'HR',
})

export const researcherDemoAccount = Object.freeze({
  username: 'researcher', email: 'researcher@simrecrut.test', password: 'SimRecrut123!',
  displayName: 'Demo Researcher User', role: 'Researcher',
})

export const adminDemoAccount = Object.freeze({
  username: 'admin', email: 'admin@simrecrut.test', password: 'SimRecrut123!',
  displayName: 'Demo Admin User', role: 'Admin',
})

// Registered credentials exist only in this module's memory and disappear on refresh.
const registeredAccounts = []

export async function registerDemo({ username, email, password, agreedToTerms }) {
  await new Promise((resolve) => setTimeout(resolve, 800))
  const normalizedUsername = username.trim().toLowerCase()
  const normalizedEmail = email.trim().toLowerCase()
  const accounts = [demoAccount, hrDemoAccount, researcherDemoAccount, adminDemoAccount, ...registeredAccounts]
  if (!agreedToTerms) throw new Error('Please agree to the demo terms and privacy notice.')
  if (accounts.some((account) => account.username.toLowerCase() === normalizedUsername)) {
    throw new Error('This username is already used in the demo. Please choose another.')
  }
  if (accounts.some((account) => account.email.toLowerCase() === normalizedEmail)) {
    throw new Error('This email is already used in the demo. Please choose another.')
  }
  const account = {
    username: username.trim(),
    email: normalizedEmail,
    password,
    displayName: username.trim(),
    role: 'Candidate',
  }
  registeredAccounts.push(account)
  return { username: account.username, displayName: account.displayName, role: account.role }
}

/** Simulate a local response; replace this boundary when a real API is introduced. */
export async function loginDemo({ identifier, password }) {
  await new Promise((resolve) => setTimeout(resolve, 800))
  const normalizedIdentifier = identifier.trim().toLowerCase()
  const account = [demoAccount, hrDemoAccount, researcherDemoAccount, adminDemoAccount, ...registeredAccounts].find((candidate) =>
    [candidate.username.toLowerCase(), candidate.email].includes(normalizedIdentifier))

  if (!account || password !== account.password) {
    throw new Error('Invalid username or password. Please try again.')
  }

  return { username: account.username, displayName: account.displayName, role: account.role || 'Candidate' }
}
