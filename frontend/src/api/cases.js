// Case pages use case APIs; a standalone resume is not a job assessment case.
import { pageRequest, resourceId as id } from './page-request.js'
export const caseList = () => pageRequest('/api/v1/cases')
export const caseDetail = value => pageRequest(`/api/v1/cases/${id(value)}`)
export const caseReview = value => pageRequest(`/api/v1/cases/${id(value)}/review`)
export const caseResults = value => pageRequest(`/api/v1/cases/${id(value)}/results`)
export const createCase = jobVersionId => pageRequest('/api/v1/cases', 'post', { jobVersionId })
export const caseAction = (value, action, data = {}) => pageRequest(`/api/v1/cases/${id(value)}/${action}`, 'post', data)
export const deleteCase = value => pageRequest(`/api/v1/cases/${id(value)}`, 'delete')
export const hrCases = () => pageRequest('/api/v1/hr/cases')
export const hrCase = value => pageRequest(`/api/v1/hr/cases/${id(value)}`)
