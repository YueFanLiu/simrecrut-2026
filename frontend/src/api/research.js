// Research screens consume server-authorized projections, not browser fixtures.
import { pageRequest, resourceId as id } from './page-request.js'
export const experiments = () => pageRequest('/api/v1/research/experiments')
export const subjects = () => pageRequest('/api/v1/research/subjects')
export const models = () => pageRequest('/api/v1/research/models')
export const jobs = () => pageRequest('/api/v1/research/jobs')
export const createExperiment = form => pageRequest('/api/v1/research/experiments', 'post', form)
export const experimentDetail = value => pageRequest(`/api/v1/research/experiments/${id(value)}`)
export const experimentAction = (value, action, data = {}) => pageRequest(`/api/v1/research/experiments/${id(value)}/${action}`, 'post', data)
export const primaryReview = (value, data) => pageRequest(`/api/v1/research/subjects/${id(value)}/reviews`, 'post', data)
