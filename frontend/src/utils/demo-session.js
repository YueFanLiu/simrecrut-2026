// Tab-scoped navigation state only. This is not an authentication token.
export const demoSessionKey = 'simrecrut-demo-session-v1'
const accounts = { demo: ['Candidate', 'Demo User'], hr: ['HR', 'Demo HR User'],
  researcher: ['Researcher', 'Demo Researcher User'], admin: ['Admin', 'Demo Admin User'] }

export function readDemoSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(demoSessionKey) || 'null')
    const account = accounts[saved?.username]
    if (account && saved.expiresAt > Date.now()) return { username: saved.username, role: account[0], displayName: account[1] }
  } catch { /* A missing or blocked store starts a fresh demo session. */ }
  return null
}

export function saveDemoSession(user) {
  try {
    if (!accounts[user?.username]) sessionStorage.removeItem(demoSessionKey)
    else sessionStorage.setItem(demoSessionKey, JSON.stringify({ username: user.username, expiresAt: Date.now() + 6 * 3600000 }))
  } catch { /* Login remains available without browser storage. */ }
}

export function projectCaseCheckpoints(state) {
  const cases = {}
  for (const entry of Object.values(state.cases)) {
    if (!entry.confirmedAt || Date.parse(entry.expiresAt) <= Date.now() || ['PURGING', 'DELETED'].includes(entry.processingStatus)) continue
    const { caseId, owner, jobId, jobVersionId, jobTitle, version, createdAt, expiresAt, confirmedAt, jobSummary, processingStatus } = entry
    cases[caseId] = { caseId, owner, jobId, jobVersionId, jobTitle, version, createdAt, expiresAt, confirmedAt,
      jobSummary: { jobId: jobSummary.jobId, jobVersionId: jobSummary.jobVersionId, title: jobSummary.title,
        company: jobSummary.company, location: jobSummary.location, employmentType: jobSummary.employmentType, version: jobSummary.version },
      processingStatus, assessment: entry.assessment ? {
        methods: entry.assessment.methods.map(({ name, status }) => ({ name, status })),
        retryCount: entry.assessment.retryCount, retryable: entry.assessment.retryable,
        updatedAt: entry.assessment.updatedAt, events: [] } : null }
  }
  return { cases, nextCaseNumber: state.nextCaseNumber }
}
