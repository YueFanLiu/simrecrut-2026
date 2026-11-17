const pendingReview = () => ({ status: 'Pending', decision: null, reason: null })

export function createSimulationResults(entry) {
  // Fixed illustrative outputs, unrelated to CV facts, job requirements or research values.
  return {
    completedAt: new Date().toISOString(),
    objective: {
      displayScore: 78, decision: 'Accept',
      strengths: ['Sample professional skills meet the example requirements.', 'Sample experience supports the example role.'],
      gaps: ['The example profile has limited project evidence.'],
    },
    neutral: { acceptanceScore: 0.74, decision: 'Accept' },
    biased: {
      acceptanceScore: 0.41, decision: 'Reject',
      attributeSummary: entry.researchAllowed ? { ...entry.researchAttributes } : null,
    },
    humans: { standard: pendingReview(), instructedBias: pendingReview() },
    humanInitialized: false,
  }
}

export function submittedReview(condition) {
  return condition === 'standard'
    ? { status: 'Submitted', decision: 'Accept', reason: 'Mock review: the sample professional profile meets the example role requirements.' }
    : { status: 'Submitted', decision: 'Reject', reason: 'Mock review: the assigned experimental instruction changed this simulated decision.' }
}

function selectHumanReview(review) {
  if (review.status !== 'Submitted') {
    return { status: review.status === 'Not assigned' ? 'Not assigned' : 'Pending', decision: null, reason: null }
  }
  if (!['Accept', 'Reject'].includes(review.decision) || !review.reason?.trim()) {
    throw new Error('A completed human result is missing its decision or reason.')
  }
  return { status: 'Submitted', decision: review.decision, reason: review.reason }
}

export function selectCandidateResults(entry) {
  const results = entry.results
  if (!results || !Number.isFinite(results.objective?.displayScore)
    || results.objective.displayScore < 0 || results.objective.displayScore > 100
    || [results.neutral, results.biased].some((method) => !Number.isFinite(method?.acceptanceScore)
      || method.acceptanceScore < 0 || method.acceptanceScore > 1)
    || [results.objective, results.neutral, results.biased].some((method) => !['Accept', 'Reject'].includes(method?.decision))) {
    throw new Error('The automated demo results are incomplete. No replacement score or decision is shown.')
  }
  // Explicit public fields prevent internal weights, thresholds and reviewer identities from leaking.
  const data = {
    caseId: entry.caseId, job: {
      jobId: entry.jobSummary.jobId, jobVersionId: entry.jobSummary.jobVersionId,
      title: entry.jobSummary.title, company: entry.jobSummary.company,
      location: entry.jobSummary.location, employmentType: entry.jobSummary.employmentType,
      version: entry.jobSummary.version,
    }, expiresAt: entry.expiresAt,
    completedAt: results.completedAt,
    objective: { displayScore: results.objective.displayScore, decision: results.objective.decision,
      strengths: [...results.objective.strengths], gaps: [...results.objective.gaps] },
    neutral: { acceptanceScore: results.neutral.acceptanceScore, decision: results.neutral.decision },
    biased: { acceptanceScore: results.biased.acceptanceScore, decision: results.biased.decision },
    humans: { standard: selectHumanReview(results.humans.standard),
      instructedBias: selectHumanReview(results.humans.instructedBias) },
  }
  if (entry.researchAllowed && results.biased.attributeSummary) {
    const attributes = results.biased.attributeSummary
    data.biased.attributeSummary = {
      schoolPrestige: attributes.schoolPrestige, referral: attributes.referral,
      gender: attributes.gender, ethnicity: attributes.ethnicity,
    }
  }
  return data
}
