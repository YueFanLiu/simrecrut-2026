import { pageItems, pageRequest, resourceId as id, utcTimestamp } from './page-request.js'
import { professionalSummary } from './cases.js'
import { publicRequirementLabel } from './jobs.js'

const tasks = '/api/v1/reviewer/tasks'
const label = value => value[0] + value.slice(1).toLowerCase()
const condition = value => value === 'STANDARD' ? 'Standard' : 'Instructed-Bias'
export const reviewTasks = async () => (await pageItems(tasks)).map(row => ({
  ...row, status: label(row.status), purpose: label(row.purpose), condition: condition(row.condition),
  job: row.jobTitle, assignedAt: utcTimestamp(row.createdAt),
}))

export async function reviewTask(value) {
  const row = await pageRequest(`${tasks}/${id(value)}`)
  return {
    reviewId: row.reviewId, caseId: row.caseId, versions: row.versions,
    status: label(row.status), condition: condition(row.condition), purpose: label(row.purpose),
    job: row.job.title, professional: professionalSummary(row.professional),
    requirements: row.job.requirements.map(publicRequirementLabel),
    rubric: Object.entries(row.rubric).map(([name, guidance]) =>
      `${name}: ${typeof guidance === 'string' ? guidance : JSON.stringify(guidance)}`),
    controlled: row.condition === 'INSTRUCTED_BIAS' ? {
      attribute: 'Research scenario', value: Object.entries(row.scenario || {}).map(([key, item]) => `${key}: ${item}`).join(' · '),
      instruction: row.instruction, instructionAvailable: Boolean(row.instruction),
    } : null,
    submission: row.submissionVisible ? { decision: row.decision, reason: row.reason, submittedAt: utcTimestamp(row.submittedAt) } : null,
  }
}
export const submitReview = (value, form, key) => pageRequest(`${tasks}/${id(value)}/submit`, 'post', {
  decision: form.decision, reason: form.reason.trim(),
}, false, key)

export const eligibleReviewers = (caseId, query) =>
  pageRequest(`/api/v1/research/cases/${id(caseId)}/eligible-reviewers?${new URLSearchParams({ query })}`)
export const assignPrimaryReviews = (caseId, assignments, key) =>
  pageRequest(`/api/v1/research/cases/${id(caseId)}/reviews`, 'post', { assignments }, false, key)
