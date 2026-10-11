import { requireAdmin } from './admin-users.js'

export const failureStages = ['Extraction', 'Matching', 'Neutral ML', 'Biased ML', 'Cleanup']
export const failureStatuses = ['Failed (retryable)', 'Failed (final)']
const errors = {
  EXTRACTION_TIMEOUT: 'The extraction service did not respond in time.',
  EXTRACTION_INVALID: 'The extraction response did not pass schema validation.',
  MATCHING_CONFIG_INVALID: 'The frozen matching configuration could not be validated.',
  ML_SERVICE_TIMEOUT: 'The inference service did not respond in time.',
  MODEL_INCOMPATIBLE: 'The recorded model configuration is incompatible.',
  CLEANUP_FAILED: 'Required temporary-storage cleanup did not complete.',
}
const definitions = [
  ['Extraction', 'EXTRACTION_TIMEOUT', true, 'Pending'],
  ['Biased ML', 'ML_SERVICE_TIMEOUT', true, 'Unavailable'],
  ['Cleanup', 'CLEANUP_FAILED', true, 'Pending'],
  ['Extraction', 'EXTRACTION_INVALID', false, 'Pending'],
  ['Neutral ML', 'MODEL_INCOMPATIBLE', false, 'Unavailable'],
  ['Matching', 'MATCHING_CONFIG_INVALID', false, 'Unavailable'],
  ['Neutral ML', 'ML_SERVICE_TIMEOUT', true, 'Unavailable'],
  ['Cleanup', 'CLEANUP_FAILED', true, 'Pending'],
]
export function failureProjection(row) {
  const { taskId, resourceId, resourceType, stage, retryable, occurredAt, cleanupStatus } = row
  const errorCode = Object.hasOwn(errors, row.errorCode) ? row.errorCode : 'UNAVAILABLE'
  return { taskId, resourceId, resourceType, stage, retryable: retryable === true,
    status: retryable === true ? 'Failed (retryable)' : 'Failed (final)', occurredAt, cleanupStatus,
    errorCode, message: errors[errorCode] || 'Safe error information is unavailable.' }
}
export function listFailures(user) {
  requireAdmin(user)
  return definitions.map(([stage, errorCode, retryable, cleanupStatus], index) => failureProjection({
    taskId: `DEMO-TASK-${String(index + 1).padStart(3, '0')}`,
    resourceId: `DEMO-RESOURCE-${String(index + 1).padStart(3, '0')}`, resourceType: 'Assessment Case',
    stage, errorCode, retryable, cleanupStatus, occurredAt: `2026-10-${index < 5 ? '09' : '08'}T${String(14 - index).padStart(2, '0')}:00:00.000Z`,
  }))
}
export async function loadFailures(user, scenario = 'normal') {
  const rows = listFailures(user)
  await new Promise(resolve => setTimeout(resolve, 400))
  if (scenario === 'load-error') throw new Error('Failed tasks could not be loaded. Refresh to try again.')
  return scenario === 'empty' ? [] : rows
}
export async function readFailure(taskId, user, scenario = 'normal') {
  const row = listFailures(user).find(row => row.taskId === taskId)
  if (!row) throw new Error('The failed task was not found.')
  await new Promise(resolve => setTimeout(resolve, 400))
  if (scenario === 'detail-error') throw new Error('Technical details could not be loaded. Refresh the details to try again.')
  return row
}
export function validateFailureDates({ from = '', to = '' }) {
  for (const value of [from, to]) {
    if (!value) continue
    const time = Date.parse(`${value}T00:00:00.000Z`)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(time) || new Date(time).toISOString().slice(0, 10) !== value) return 'Enter a valid date in YYYY-MM-DD format.'
  }
  if (from && to && from > to) return 'The start date must be on or before the end date.'
  return ''
}
export function filterFailures(rows, { keyword = '', stage = '', status = '', from = '', to = '' } = {}) {
  if (validateFailureDates({ from, to })) return []
  const query = keyword.trim().toLowerCase()
  return rows.filter(row => (!query || [row.taskId, row.resourceId, row.errorCode, row.message].some(value => value.toLowerCase().includes(query)))
    && (!stage || row.stage === stage) && (!status || row.status === status)
    && (!from || row.occurredAt.slice(0, 10) >= from) && (!to || row.occurredAt.slice(0, 10) <= to))
}
