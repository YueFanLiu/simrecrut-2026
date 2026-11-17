import { pageItems, pageRequest, resourceId as id, utcTimestamp } from './page-request.js'

export { utcTimestamp as caseExpiry }

const candidateCases = '/api/v1/candidate/cases'
const candidateCase = value => `${candidateCases}/${id(value)}`

export const caseList = async () => (await pageItems(candidateCases)).map(row => ({
  ...row, createdAt: utcTimestamp(row.createdAt), expiresAt: utcTimestamp(row.purgeAt),
}))
export const caseDetail = value => pageRequest(`${candidateCase(value)}/status`)
export const caseReview = value => pageRequest(`${candidateCase(value)}/draft`)
export const caseResearch = value => pageRequest(`${candidateCase(value)}/research-attributes`)
export const saveCaseDraft = (value, data, key) => pageRequest(`${candidateCase(value)}/draft`, 'put', data, false, key)
export const saveCaseResearch = (value, data, key) => pageRequest(`${candidateCase(value)}/research-attributes`, 'put', data, false, key)
export const createCase = (jobVersionId, key) => pageRequest(candidateCases, 'post', { jobVersionId }, false, key)
export function uploadCaseCv(value, file, key) {
  const data = new FormData()
  data.append('file', file)
  return pageRequest(`${candidateCase(value)}/cv`, 'post', data, false, key)
}
export const caseAction = (value, action, data = {}, key) => pageRequest(`${candidateCase(value)}/${action}`, 'post', data, false, key)
export const deleteCase = (value, key) => pageRequest(candidateCase(value), 'delete', undefined, false, key)

export async function caseResults(value) {
  const { objective, neutralMl, biasedMl, human, privacy, ...result } = await pageRequest(`${candidateCase(value)}/results`)
  return {
    ...result, objective, neutral: neutralMl, biased: biasedMl, humans: human,
    expiresAt: utcTimestamp(privacy.purgeAt), canDeleteNow: privacy.canDeleteNow,
  }
}

const criteria = ['Skills', 'Experience', 'Education', 'Languages', 'Projects']
const label = value => value?.replaceAll('_', ' ').toLowerCase().replace(/(^| )\w/g, match => match.toUpperCase())

export function professionalSummary(professional) {
  return Object.fromEntries(Object.entries(professional).map(([group, facts]) => [
    group, facts.map(({ evidence, ...fact }) => Object.entries(fact)
      .filter(([, value]) => value != null && value !== false && value !== '' && (!Array.isArray(value) || value.length))
      .map(([field, value]) => `${field.replace(/([A-Z])/g, ' $1')}: ${value}`).join(', ')).join(' · ') || 'None recorded',
  ]))
}

export const hrCases = async () => (await pageItems('/api/v1/hr/cases')).map(row => ({
  ...row, version: row.jobVersionId, purgeAt: utcTimestamp(row.expiresAt),
  processingLabel: label(row.processingStatus), humanLabel: label(row.humanStatus), statusType: 'info',
  accessLabel: row.resultsRestricted ? 'Blind review required' : 'Authorised',
}))

export async function hrCase(value) {
  const row = await pageRequest(`/api/v1/hr/cases/${id(value)}`)
  const { job, professional, matching, weights, contributions, objective, neutralMl, biasedMl, human, versions } = row
  const ml = result => result ? { status: 'Complete', score: result.acceptanceScore, decision: result.decision } : { status: 'Unavailable' }
  const review = condition => {
    const item = human.find(result => result.condition === condition)
    return item ? { ...item, status: label(item.status) } : { status: 'Not assigned' }
  }
  return {
    caseId: row.caseId, jobTitle: job.title, version: job.version, accessLabel: 'Authorised', resultsVisible: true,
    professional: Object.entries(professionalSummary(professional)).map(([group, value]) => ({ category: label(group), value })),
    versions: { ...versions, matching: versions.matchingRuleVersion, protocol: versions.protocolVersion,
      neutral: versions.neutralModelVersion, biased: versions.biasedModelVersion },
    matching: Array.isArray(matching?.values) && Array.isArray(contributions) ? {
      mandatory: matching.mandatoryPass === 1 ? 'Passed' : 'Failed',
      cutoff: objective?.threshold ?? null,
      rows: criteria.map((category, index) => ({
        category, match: matching.values[index], weight: weights[index], contribution: contributions[index],
      })),
    } : null,
    objective: objective ? { status: 'Complete', score: objective.displayScore, decision: objective.decision } : { status: 'Unavailable' },
    neutral: ml(neutralMl), biased: ml(biasedMl),
    standard: review('STANDARD'),
    instructedBias: review('INSTRUCTED_BIAS'),
  }
}
