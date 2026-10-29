// Page contracts for published jobs and the HR version workflow.
import { pageRequest, resourceId as id } from './page-request.js'
export const publishedJobs = () => pageRequest('/api/v1/jobs')
export const jobDetail = job => pageRequest(`/api/v1/jobs/${id(job)}`)
export const managedJobs = () => pageRequest('/api/v1/hr/jobs')
export const jobDraft = job => pageRequest(`/api/v1/hr/jobs/${id(job)}/draft`)
export const createJob = form => pageRequest('/api/v1/hr/jobs', 'post', form)
export const saveJob = (job, form, expectedRevision) => pageRequest(`/api/v1/hr/jobs/${id(job)}/draft`, 'put', { form, expectedRevision })
export const extractJob = job => pageRequest(`/api/v1/hr/jobs/${id(job)}/extract`, 'post', {})
export const validateJob = job => pageRequest(`/api/v1/hr/jobs/${id(job)}/validate`, 'post', {})
export const publishJob = (job, expectedRevision) => pageRequest(`/api/v1/hr/jobs/${id(job)}/publish`, 'post', { expectedRevision })
export const jobVersions = job => pageRequest(`/api/v1/hr/jobs/${id(job)}/versions`)
export const jobVersion = (job, version) => pageRequest(`/api/v1/hr/jobs/${id(job)}/versions/${id(version)}`)
export const cloneJob = (job, version) => pageRequest(`/api/v1/hr/jobs/${id(job)}/draft`, 'post', { sourceVersionId: version })
