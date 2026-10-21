// Reuse implemented RuoYi endpoints; SimRecrut-only actions need their own APIs.
import { pageRequest, resourceId as id } from './page-request.js'
export const users = async () => {
  const rows = []
  let page = 1, total = 0
  do {
    const response = await pageRequest(`/system/user/list?pageNum=${page++}&pageSize=100`, 'get', undefined, true)
    rows.push(...response.rows); total = response.total
    if (!response.rows.length) break
  } while (rows.length < total)
  return rows.map(row => ({ id: String(row.userId), username: row.userName, displayName: row.nickName,
    email: row.email || '', role: row.userId === 1 ? 'Admin' : row.roles?.[0]?.roleName || 'Unassigned',
    group: row.dept?.deptName || '', status: row.status === '0' ? 'Active' : 'Inactive',
    createdAt: row.createTime, lastLogin: row.loginDate, revision: row.updateTime || row.createTime }))
}
export const changeUserStatus = (value, status) => pageRequest('/system/user/changeStatus', 'put', { userId: Number(value), status: status === 'Active' ? '0' : '1' })
export const saveUser = form => pageRequest('/api/v1/admin/users', form.id ? 'put' : 'post', form)
export const roles = () => pageRequest('/api/v1/admin/roles')
export const rolePermissions = form => pageRequest(`/api/v1/admin/roles/${id(form.name)}/permissions`, 'put', form)
export const assignRole = (accountId, expectedRevision, role) => pageRequest(`/api/v1/admin/users/${id(accountId)}/role`, 'put', { role, expectedRevision })
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
