import { criteria } from './hr-job-wizard.js'

export function createHrDetailFixture(entry, weights = [0.4, 0.3, 0.15, 0.1, 0.05]) {
  const confirmed = ['CONFIRMED', 'AUTOMATED_COMPLETE', 'AUTOMATED_RUNNING', 'FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.processingStatus)
  const started = ['AUTOMATED_COMPLETE', 'AUTOMATED_RUNNING', 'FAILED_RETRYABLE'].includes(entry.processingStatus)
  const matches = [0.8, 1, 0.6, 0.5, 0.4]
  // Layout fixture arithmetic only, unrelated to any CV or real matching process.
  const contributions = weights.map((weight, i) => Math.round(weight * matches[i] * 1e7) / 1e7)
  const score = Math.round(contributions.reduce((sum, value) => sum + value, 0) * 1e7) / 1e7
  const complete = entry.processingStatus === 'AUTOMATED_COMPLETE'
  const human = (status, biased) => ({ status, decision: status === 'Submitted' ? (biased ? 'Reject' : 'Accept') : null,
    reason: status === 'Submitted' ? 'Illustrative submitted review of the mock professional profile.' : null })
  return {
    professional: confirmed ? { Skills: 'Java, Python, SQL', Experience: '3 relevant years',
      Education: "Bachelor's degree", Languages: 'English B2', Projects: '2 relevant projects' } : null,
    matching: started ? { rows: criteria.map((category, i) => ({ category, weight: weights[i], match: matches[i], contribution: contributions[i] })),
      mandatory: 'Passed', cutoff: 0.7 } : null,
    objective: started ? { status: 'Complete', score: Math.round(score * 10000000) / 100000, decision: score >= 0.7 ? 'Accept' : 'Reject' } : { status: 'Not started' },
    neutral: complete ? { status: 'Complete', score: 0.82, decision: 'Accept' }
      : { status: entry.processingStatus === 'AUTOMATED_RUNNING' ? 'Running' : entry.processingStatus.startsWith('FAILED') ? 'Failed' : 'Not started' },
    biased: complete ? { status: 'Complete', score: 0.46, decision: 'Reject' } : { status: 'Not started' },
    standard: human(entry.humanStatus.standard, false), instructedBias: human(entry.humanStatus.instructedBias, true),
    versions: { jobVersionId: `${entry.jobId}-v${entry.version}`, weights: `job-${entry.version}-weights`,
      matching: 'mock-matching-1', protocol: 'mock-protocol-1', neutral: 'mock-neutral-1', biased: 'mock-biased-1' },
    resultsVisible: true,
  }
}

function automatedResult(value, maximum) {
  if (value?.status !== 'Complete') return { status: ['Running', 'Failed', 'Not started'].includes(value?.status) ? value.status : 'Unavailable', score: null, decision: null }
  if (!Number.isFinite(value.score) || value.score < 0 || value.score > maximum || !['Accept', 'Reject'].includes(value.decision)) {
    return { status: 'Unavailable', score: null, decision: null }
  }
  return { status: 'Complete', score: value.score, decision: value.decision }
}
function humanResult(value) {
  if (value?.status !== 'Submitted') return { status: ['Pending', 'Not assigned'].includes(value?.status) ? value.status : 'Unavailable', decision: null, reason: null }
  if (!['Accept', 'Reject'].includes(value.decision) || typeof value.reason !== 'string' || !value.reason.trim()) return { status: 'Unavailable', decision: null, reason: null }
  return { status: 'Submitted', decision: value.decision, reason: value.reason }
}

export function selectHrCaseDetail(entry, summary, scenario = 'normal') {
  const source = entry.detail
  if (!source) throw new Error('The professional case snapshot is unavailable.')
  const visible = source.resultsVisible === true && scenario !== 'blind'
  const data = { ...summary,
    professional: source.professional ? criteria.map((category) => ({ category, value: source.professional[category] || 'Not available' })) : null,
    versions: { jobVersionId: source.versions.jobVersionId, weights: source.versions.weights,
      matching: source.versions.matching, protocol: source.versions.protocol,
      neutral: source.versions.neutral, biased: source.versions.biased },
    resultsVisible: visible,
  }
  if (!visible) return data
  const rows = source.matching?.rows
  const validMatching = Array.isArray(rows) && rows.length === 5 && rows.every((row, i) => row.category === criteria[i]
    && [row.weight, row.match, row.contribution].every((value) => Number.isFinite(value) && value >= 0 && value <= 1))
  data.matching = validMatching ? { rows: rows.map(({ category, weight, match, contribution }) => ({ category, weight, match, contribution })),
    mandatory: ['Passed', 'Failed', 'Unresolved'].includes(source.matching.mandatory) ? source.matching.mandatory : 'Unresolved',
    cutoff: Number.isFinite(source.matching.cutoff) && source.matching.cutoff >= 0 && source.matching.cutoff <= 1 ? source.matching.cutoff : null } : null
  data.objective = automatedResult(source.objective, 100)
  data.neutral = automatedResult(source.neutral, 1)
  data.biased = automatedResult(source.biased, 1)
  data.standard = humanResult(source.standard)
  data.instructedBias = humanResult(source.instructedBias)
  return data
}
