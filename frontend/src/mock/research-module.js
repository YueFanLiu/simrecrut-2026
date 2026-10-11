import { fixtures } from './research-fixtures.js'

export const researchJobs = [
  { id: 'Software Engineer v3', weights: 'Balanced professional v1', protocol: 'P-2026.01' },
  { id: 'Data Analyst v2', weights: 'Balanced professional v1', protocol: 'P-2026.01' },
  { id: 'Backend Developer v1', weights: 'Balanced professional v1', protocol: 'P-2026.01' },
]
export const researchFactors = ['Gender', 'Ethnicity', 'School prestige', 'Referral']
export function createResearchModule(now = Date.now()) {
  const copy = structuredClone(fixtures)
  return {
    experiments: copy.experiments.map((row, i) => ({ ...row,
      name: i === 1 ? 'School prestige analysis' : i === 2 ? 'Referral comparison' : row.name,
      owner: 'researcher', grants: [], weight: 'Balanced professional v1',
      neutral: 'N-2026.01 / 1.0.0', biased: 'B-2026.01 / 1.0.0',
      subjectIds: [], pairs: [], reviews: [], factor: 'Gender',
      // Imported counts lack retained subject records, so never fabricate comparison outputs.
      execution: row.status === 'Completed' ? 'Stored example; result data unavailable.' : 'Not started',
    })),
    subjects: copy.subjects.map((row) => ({ ...row, owner: row.source === 'Reviewed Profiles' ? 'researcher' : 'another-user',
      grants: row.status === 'Eligible' && row.source === 'Authorized Candidate Cases'
        ? [{ username: 'researcher', expiresAt: new Date(now + 86400000).toISOString() }] : [],
      privacy: 'ACTIVE', purgeAt: row.status === 'Expired' ? new Date(now).toISOString() : null,
      professional: { skills: row.skills, experience: '3 relevant years', education: row.education || 'Not available', languages: 'English B2', projects: '2 relevant projects' },
    })),
    models: copy.models.map(row => ({ ...row, cutoff: 0.5, evaluation: 'Unavailable; no evaluated sample records or provenance.' })),
  }
}
export function hasResearchAccess(row, user, now = Date.now()) {
  return user?.role === 'Researcher' && (row.owner === user.username || row.grants?.some(grant =>
    grant.username === user.username && (grant.expiresAt === null || Date.parse(grant.expiresAt) > now)))
}
export function eligibleResearchSubject(row, user, now = Date.now()) {
  return row.status === 'Eligible' && row.privacy === 'ACTIVE' &&
    (row.purgeAt === null || Date.parse(row.purgeAt) > now) && hasResearchAccess(row, user, now)
}
export function researchSubjectProjection(row) {
  return { id: row.id, source: row.source, name: row.name, status: row.status,
    skills: row.professional.skills, education: row.professional.education,
    professional: professionalProjection(row.professional) }
}
function professionalProjection(row) {
  return { skills: row.skills, experience: row.experience, education: row.education, languages: row.languages, projects: row.projects }
}
export function researchExperimentProjection(row) {
  const { id, name, type, job, weight, protocol, neutral, biased, status, date, subjects, subjectIds, factor, pairs, reviews, execution } = row
  return JSON.parse(JSON.stringify({ id, name, type, job, weight, protocol, neutral, biased, status, date, subjects, subjectIds, factor,
    pairs: pairs.map(pair => ({ id: pair.id, subjectId: pair.subjectId, factor: pair.factor, baseline: pair.baseline, variant: pair.variant,
      professional: professionalProjection(pair.professional), variantProfessional: professionalProjection(pair.variantProfessional) })),
    reviews: reviews.map(({ id, target, condition, reviewer, purpose, status }) => ({ id, target, condition, reviewer, purpose, status })),
    outputs: row.outputs?.map(({ subjectId, variant, label, objective, neutral, biased }) => ({ subjectId, variant, label, objective, neutral, biased })), execution }))
}
export function validateResearchConfiguration(form, models) {
  const fields = ['name', 'type', 'job', 'weight', 'protocol', 'neutral', 'biased']
  if (fields.some(key => typeof form[key] !== 'string' || !form[key].trim())) return 'Complete all seven required fields.'
  if (form.name.trim().length > 120) return 'Use an experiment name of 120 characters or fewer.'
  if (!['Single-factor paired', 'Baseline'].includes(form.type)) return 'Select a supported experiment type.'
  const job = researchJobs.find(row => row.id === form.job)
  if (!job || form.weight !== job.weights || form.protocol !== job.protocol) return 'Select compatible frozen job, weights and protocol versions.'
  for (const [field, name] of [['neutral', 'Neutral ML'], ['biased', 'Biased ML']]) {
    if (!models.some(model => `${model.id} / ${model.version}` === form[field] && model.name === name && model.status === 'Active' && model.protocol === form.protocol)) return 'Select compatible active Neutral and Biased model versions.'
  }
  return ''
}
