import { demoMode } from '../config/runtime.js'
import { requireAdmin } from './admin-users.js'

export const auditActions = ['ACCOUNT_STATUS_CHANGED', 'ROLE_ASSIGNED', 'MODEL_APPROVED', 'MODEL_ACTIVATED', 'JOB_PUBLISHED', 'REVIEW_SUBMITTED', 'CLEANUP_RECORDED']
export const auditResourceTypes = ['Account', 'Model Version', 'Job Version', 'Review Task', 'Cleanup Receipt']
export const auditOutcomes = ['SUCCESS', 'FAILURE']
const summaries = {
  ACCOUNT_STATUS_CHANGED: 'Account status change recorded.', ROLE_ASSIGNED: 'Directory role assignment recorded.',
  MODEL_APPROVED: 'Model approval operation recorded.', MODEL_ACTIVATED: 'Model activation operation recorded.',
  JOB_PUBLISHED: 'Job publication operation recorded.', REVIEW_SUBMITTED: 'Review submission operation recorded; decision and reason are excluded.',
  CLEANUP_RECORDED: 'Cleanup operation outcome recorded; no case content is retained.',
}
const transitions = {
  ACCOUNT_STATUS_CHANGED: ['Active', 'Inactive'], ROLE_ASSIGNED: ['Candidate', 'HR'],
  MODEL_APPROVED: ['TESTED', 'APPROVED'], MODEL_ACTIVATED: ['APPROVED', 'ACTIVE'],
  JOB_PUBLISHED: ['DRAFT', 'PUBLISHED'], REVIEW_SUBMITTED: ['Pending', 'Submitted'], CLEANUP_RECORDED: ['Pending', 'Complete'],
}
let recorded = []
export function recordDemoAudit(actionCode, resourceType, resourceId, username) {
  try { recorded = JSON.parse(sessionStorage.getItem('simrecrut-audit-events-v1') || '[]') } catch { /* Keep memory events. */ }
  const event = auditProjection({ eventId: `DEMO-LOCAL-${Date.now()}-${recorded.length}`,
    occurredAt: new Date().toISOString(), actor: username, actionCode, resourceType, resourceId,
    requestId: null, outcome: 'SUCCESS', fromState: transitions[actionCode]?.[0], toState: transitions[actionCode]?.[1] })
  recorded = [event, ...recorded].slice(0, 100)
  try { sessionStorage.setItem('simrecrut-audit-events-v1', JSON.stringify(recorded)) } catch { /* Keep memory events. */ }
}
export function auditProjection(row) {
  const actionCode = auditActions.includes(row.actionCode) ? row.actionCode : 'UNAVAILABLE'
  const from = transitions[actionCode]?.includes(row.fromState) ? row.fromState : null
  const to = transitions[actionCode]?.includes(row.toState) ? row.toState : null
  return { eventId: row.eventId, occurredAt: row.occurredAt, actor: row.actor || null, actionCode,
    resourceType: auditResourceTypes.includes(row.resourceType) ? row.resourceType : 'Unavailable',
    resourceId: row.resourceId || null, requestId: row.requestId || null,
    outcome: auditOutcomes.includes(row.outcome) ? row.outcome : 'Unavailable',
    fromState: from, toState: to, summary: summaries[actionCode] || 'Safe event summary is unavailable.' }
}
export function listAuditEvents(user) {
  requireAdmin(user)
  const definitions = [
    ['MODEL_ACTIVATED', 'Model Version', 'DEMO-ACTOR-001', 'SUCCESS'],
    ['MODEL_APPROVED', 'Model Version', 'DEMO-ACTOR-001', 'SUCCESS'],
    ['ROLE_ASSIGNED', 'Account', 'DEMO-ACTOR-001', 'SUCCESS'],
    ['REVIEW_SUBMITTED', 'Review Task', 'DEMO-ACTOR-003', 'SUCCESS'],
    ['JOB_PUBLISHED', 'Job Version', 'DEMO-ACTOR-003', 'SUCCESS'],
    ['CLEANUP_RECORDED', 'Cleanup Receipt', null, 'FAILURE'],
    ['ACCOUNT_STATUS_CHANGED', 'Account', 'DEMO-ACTOR-001', 'SUCCESS'],
    ['MODEL_ACTIVATED', 'Model Version', 'DEMO-ACTOR-001', 'FAILURE'],
  ]
  try { recorded = JSON.parse(sessionStorage.getItem('simrecrut-audit-events-v1') || '[]') } catch { /* Keep memory events. */ }
  return [...recorded.map(auditProjection), ...definitions.map(([actionCode, resourceType, actor, outcome], i) => auditProjection({
    eventId: `DEMO-EVENT-${String(i + 1).padStart(3, '0')}`, actionCode, resourceType, actor, outcome,
    resourceId: resourceType === 'Cleanup Receipt' ? null : `DEMO-REF-${String(i + 1).padStart(3, '0')}`,
    requestId: `DEMO-REQUEST-${String(i + 1).padStart(3, '0')}`,
    occurredAt: `2026-10-${i < 5 ? '09' : '08'}T${String(14 - i).padStart(2, '0')}:00:00.000Z`,
    fromState: transitions[actionCode][0], toState: outcome === 'SUCCESS' ? transitions[actionCode][1] : transitions[actionCode][0],
  }))]
}
export function validateAuditDates({ from = '', to = '' }) {
  for (const value of [from, to]) {
    if (!value) continue
    const time = Date.parse(`${value}T00:00:00.000Z`)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(time) || new Date(time).toISOString().slice(0, 10) !== value) return 'Enter a valid date in YYYY-MM-DD format.'
  }
  if (from && to && from > to) return 'The start date must be on or before the end date.'
  return ''
}
export function queryAuditEvents(user, filters = {}, page = 1, pageSize = 5) {
  requireAdmin(user)
  const error = validateAuditDates(filters)
  if (error) throw new Error(error)
  if (!Number.isInteger(page) || page < 1 || ![5, 10].includes(pageSize)) throw new Error('Select a valid audit page and page size.')
  const actor = (filters.actor || '').trim().toLowerCase()
  const rows = listAuditEvents(user).filter(row => (!filters.actionCode || row.actionCode === filters.actionCode)
    && (!filters.resourceType || row.resourceType === filters.resourceType) && (!filters.outcome || row.outcome === filters.outcome)
    && (!actor || (row.actor || '').toLowerCase().includes(actor))
    && (!filters.from || row.occurredAt.slice(0, 10) >= filters.from) && (!filters.to || row.occurredAt.slice(0, 10) <= filters.to))
  const currentPage = Math.min(page, Math.max(1, Math.ceil(rows.length / pageSize)))
  return { items: rows.slice((currentPage - 1) * pageSize, currentPage * pageSize), total: rows.length, page: currentPage }
}
export async function loadAuditEvents(user, filters, page, pageSize, scenario = 'normal') {
  if (!demoMode) { return (await import('../api/administration.js')).audit(filters, page, pageSize) }
  const query = { ...filters }, result = queryAuditEvents(user, query, page, pageSize)
  await new Promise(resolve => setTimeout(resolve, 400))
  if (scenario === 'load-error') throw new Error('Audit logs could not be loaded. Refresh to try again.')
  return scenario === 'empty' ? { items: [], total: 0, page: 1 } : result
}
