// Account management uses the independent Java identity API.
import { pageItems, pageRequest, resourceId as id } from './page-request.js'
const accounts = '/api/v1/admin/users'
const user = value => `${accounts}/${id(value)}`
export const users = filters => pageItems(accounts, filters)
export const userDetail = value => pageRequest(user(value))
export const createUser = ({ username, email, roles }) => pageRequest(accounts, 'post', { username, email, roles })
export const saveUser = ({ userId, email, revision }) => pageRequest(user(userId), 'put', { email, revision })
export const changeUserStatus = (value, status, revision) => pageRequest(`${user(value)}/status`, 'put', { status, revision })
export const assignRole = (value, revision, roles) => pageRequest(`${user(value)}/roles`, 'put', { roles, revision })
export const roles = () => pageRequest('/api/v1/admin/roles')
const rolePath = value => `/api/v1/admin/roles/${id(value)}/permissions`
export const readRolePermissions = value => pageRequest(rolePath(value))
export const rolePermissions = ({ roleCode, permissions, revision }) =>
  pageRequest(rolePath(roleCode), 'put', { permissions, revision })
export const permissions = () => pageRequest('/api/v1/admin/permissions')
export const models = () => pageRequest('/api/v1/admin/models')
export const modelPackages = () => pageRequest('/api/v1/admin/model-packages')
export const registerModel = value => pageRequest('/api/v1/admin/models', 'post', { packageId: value })
export const releaseModel = (value, expectedRevision, action, replacementId) => pageRequest(`/api/v1/admin/models/${id(value)}/${id(action)}`, 'post', { expectedRevision, replacementId })
export const protocols = () => pageRequest('/api/v1/admin/protocols')
export const protocol = value => pageRequest(`/api/v1/admin/protocols/${id(value)}`)
export const health = () => pageRequest('/api/v1/admin/health')
export const failures = () => pageRequest('/api/v1/admin/failures')
export const failure = value => pageRequest(`/api/v1/admin/failures/${id(value)}`)
export const audit = async (filters, page, pageSize) => {
  const params = new URLSearchParams({ pageNum: page, pageSize })
  if (filters.actor) params.set('operName', filters.actor)
  if (filters.outcome) params.set('status', filters.outcome === 'SUCCESS' ? '0' : '1')
  if (filters.from) params.set('params[beginTime]', filters.from)
  if (filters.to) params.set('params[endTime]', filters.to)
  // RuoYi does not have SimRecrut resource/action codes; do not silently ignore those filters.
  if (filters.actionCode || filters.resourceType) throw new Error('SimRecrut action and resource filters require the business audit API.')
  const response = await pageRequest(`/monitor/operlog/list?${params}`, 'get', undefined, true)
  const items = response.rows.map(row => ({ eventId: String(row.operId), actionCode: 'UNAVAILABLE',
    resourceType: 'Operation', resourceId: null, actor: row.operName, requestId: null,
    outcome: Number(row.status) === 0 ? 'SUCCESS' : 'FAILURE', occurredAt: row.operTime,
    fromState: null, toState: null, summary: row.title }))
  return { items, total: response.total, page }
}
