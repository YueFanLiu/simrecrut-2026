// Review responses and submissions stay scoped to the current server identity.
import { pageRequest, resourceId as id } from './page-request.js'
export const reviewTasks = () => pageRequest('/api/v1/reviews')
export const reviewTask = value => pageRequest(`/api/v1/reviews/${id(value)}`)
export const submitReview = (value, form) => pageRequest(`/api/v1/reviews/${id(value)}/submit`, 'post', { decision: form.decision, reason: form.reason })
