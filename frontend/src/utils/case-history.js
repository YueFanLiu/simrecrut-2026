const statusLabels = {
  AWAITING_UPLOAD: 'Awaiting upload', UPLOADED: 'Uploading', EXTRACTING: 'Extracting',
  REVIEW_REQUIRED: 'Review required', CONFIRMED: 'Ready to run',
  AUTOMATED_RUNNING: 'Running', AUTOMATED_COMPLETE: 'Complete',
  FAILED_RETRYABLE: 'Failed (retryable)', FAILED_FINAL: 'Failed',
}

export function getCaseDestination(entry) {
  const params = { caseId: entry.caseId }
  if (['AWAITING_UPLOAD', 'UPLOADED', 'EXTRACTING'].includes(entry.processingStatus)) {
    return { name: 'CvUpload', params }
  }
  if (entry.processingStatus === 'REVIEW_REQUIRED') return { name: 'CvReview', params }
  if (['CONFIRMED', 'AUTOMATED_RUNNING'].includes(entry.processingStatus)) {
    return { name: 'SimulationProgress', params }
  }
  if (entry.processingStatus === 'AUTOMATED_COMPLETE') return { name: 'SimulationResults', params }
  if (['FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.processingStatus)) {
    return { name: entry.confirmedAt ? 'SimulationProgress' : 'CvUpload', params }
  }
  return null
}

function humanSummary(review) {
  if (review?.status === 'Submitted' && ['Accept', 'Reject'].includes(review.decision) && review.reason?.trim()) {
    return review.decision
  }
  if (review?.status === 'Submitted') return 'Unavailable'
  return review?.status === 'Not assigned' ? 'Not assigned' : 'Pending'
}

export function selectCaseSummary(entry) {
  const results = entry.results
  const ready = entry.processingStatus === 'AUTOMATED_COMPLETE'
    && entry.assessment?.methods.length === 3
    && entry.assessment.methods.every((method) => method.status === 'Complete')
    && Number.isFinite(results?.objective?.displayScore)
    && results.objective.displayScore >= 0 && results.objective.displayScore <= 100
    && [results.neutral, results.biased].every((method) => Number.isFinite(method?.acceptanceScore)
      && method.acceptanceScore >= 0 && method.acceptanceScore <= 1)
    && [results.objective, results.neutral, results.biased].every((method) => ['Accept', 'Reject'].includes(method.decision))
  // List rows contain public summaries only, never drafts, evidence, reasons or private parameters.
  return {
    caseId: entry.caseId, jobTitle: entry.jobTitle, version: entry.version,
    createdAt: entry.createdAt || null, uploadedAt: entry.uploadedAt || null,
    expiresAt: entry.expiresAt, status: statusLabels[entry.processingStatus] || 'Unavailable',
    statusType: entry.processingStatus.startsWith('FAILED') ? 'danger'
      : entry.processingStatus === 'AUTOMATED_COMPLETE' ? 'success' : 'info',
    objective: ready ? results.objective.displayScore : null,
    neutral: ready ? results.neutral.acceptanceScore : null,
    biased: ready ? results.biased.acceptanceScore : null,
    standardHuman: ready ? humanSummary(results.humans?.standard) : null,
    instructedBiasHuman: ready ? humanSummary(results.humans?.instructedBias) : null,
    destination: getCaseDestination(entry),
    actionLabel: entry.processingStatus === 'AUTOMATED_COMPLETE' ? 'View Results' : 'Continue',
  }
}

export function sortCaseSummaries(rows) {
  const timestamp = (entry) => Date.parse(entry.uploadedAt || entry.createdAt) || 0
  return [...rows].sort((a, b) => timestamp(b) - timestamp(a)
    || b.caseId.localeCompare(a.caseId, 'en', { numeric: true }))
}
