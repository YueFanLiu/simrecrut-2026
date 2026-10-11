import { requireAdmin } from './admin-users.js'

export const healthFreshnessLimit = 60000
const components = [
  ['backend', 'Java API'], ['mysql', 'MySQL'], ['redis', 'Redis'],
  ['mlService', 'ML inference'], ['tempWorkspace', 'Temporary workspace / cleanup'],
]
export function checkFreshness(checkedAt, now = Date.now()) {
  if (!checkedAt) return 'Unavailable'
  const time = Date.parse(checkedAt)
  if (!Number.isFinite(time) || time > now) return 'Unavailable'
  return now - time >= healthFreshnessLimit ? 'Stale' : 'Fresh'
}
export function createHealthSummary(user, scenario = 'normal') {
  requireAdmin(user)
  const checkedAt = scenario === 'stale' ? '2026-01-01T00:00:00.000Z' : null
  return {
    components: components.map(([id, name]) => ({ id, name, status: 'Unknown', checkedAt,
      details: id === 'tempWorkspace' ? 'Workspace writability and cleanup state are unavailable. No storage check is connected.' : 'No runtime health check is connected.' })),
    models: ['Neutral ML', 'Biased ML'].map(type => ({ type, version: null, status: 'Unknown', checkedAt: null })),
    checkedAt, workspaceWritable: null, cleanupStatus: null,
  }
}
export async function loadHealthSummary(user, scenario = 'normal') {
  const result = createHealthSummary(user, scenario)
  await new Promise(resolve => setTimeout(resolve, 400))
  if (scenario === 'load-error') throw new Error('Service health could not be loaded. Refresh to try again.')
  return scenario === 'empty' ? null : result
}
