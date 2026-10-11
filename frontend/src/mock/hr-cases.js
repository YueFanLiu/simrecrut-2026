import { createHrDetailFixture } from './hr-case-detail.js'
import { createPublishedHistory } from './hr-job-versions.js'
import { createManagedJobs } from './hr-jobs.js'

const processingLabels = {
  REVIEW_REQUIRED: 'Review required', CONFIRMED: 'Ready to run', AUTOMATED_RUNNING: 'Running',
  AUTOMATED_COMPLETE: 'Complete', FAILED_RETRYABLE: 'Failed (retryable)', FAILED_FINAL: 'Failed',
  AWAITING_UPLOAD: 'Awaiting upload', UPLOADED: 'Uploading', EXTRACTING: 'Extracting',
}

export function createHrCases(now = Date.now()) {
  const purgeAt = new Date(now + 30 * 86400000).toISOString()
  const rows = [
    ['hr-case-008', 'hr-job-004', 'Software Engineer', '1.2', 'AUTOMATED_COMPLETE', 'Pending', 'Pending'],
    ['hr-case-007', 'hr-job-008', 'Data Analyst', '1.0', 'AUTOMATED_COMPLETE', 'Submitted', 'Pending'],
    ['hr-case-006', 'hr-job-006', 'Project Manager', '1.1', 'AUTOMATED_RUNNING', 'Not assigned', 'Not assigned'],
    ['hr-case-005', 'hr-job-004', 'Software Engineer', '1.1', 'REVIEW_REQUIRED', 'Not assigned', 'Not assigned'],
    ['hr-case-004', 'external-job', 'Customer Support Specialist', '1.0', 'AUTOMATED_COMPLETE', 'Not assigned', 'Not assigned'],
    ['hr-case-003', 'hr-job-002', 'Product Manager', '1.0', 'FAILED_RETRYABLE', 'Not assigned', 'Not assigned'],
    ['hr-case-002', 'hr-job-008', 'Data Analyst', '1.0', 'CONFIRMED', 'Not assigned', 'Not assigned'],
    ['hr-case-001', 'hr-job-004', 'Software Engineer', '1.0', 'AUTOMATED_COMPLETE', 'Submitted', 'Submitted'],
  ].map(([caseId, jobId, jobTitle, version, processingStatus, standard, instructedBias], index) => ({
    caseId, jobId, jobTitle, version, processingStatus, humanStatus: { standard, instructedBias },
    createdAt: `2026-10-${String(8 - index).padStart(2, '0')}T10:00:00Z`,
    purgeAt: processingStatus === 'REVIEW_REQUIRED' ? null : purgeAt, privacyState: 'ACTIVE',
    grants: jobId === 'external-job' ? [{ username: 'hr', expiresAt: new Date(now + 2 * 86400000).toISOString() }] : [],
  }))
  const hidden = (caseId, overrides) => ({ ...rows[0], caseId, ...overrides })
  const history = createPublishedHistory(createManagedJobs())
  for (const row of rows) {
    const version = history[row.jobId]?.find((snapshot) => snapshot.version === row.version)
    row.detail = createHrDetailFixture(row, version?.weights.map((weight) => weight.value))
  }
  return [...rows,
    hidden('ungranted-case', { jobId: 'private-job', grants: [] }),
    hidden('expired-grant-case', { jobId: 'private-job', grants: [{ username: 'hr', expiresAt: new Date(now - 1000).toISOString() }] }),
    hidden('expired-case', { purgeAt: new Date(now - 1000).toISOString() }),
    ...['DELETE_REQUESTED', 'PURGING', 'DELETED'].map((privacyState) => hidden(`private-${privacyState.toLowerCase()}`, { privacyState })),
  ]
}

export function selectHrCaseSummaries(cases, user, jobs, now = Date.now()) {
  if (user?.role !== 'HR') throw new Error('An HR demo session is required to view authorised cases.')
  const managed = new Set(jobs.filter((job) => job.owner === user.username).map((job) => job.jobId))
  const humanLabel = (value) => ['Pending', 'Submitted', 'Not assigned'].includes(value) ? value : 'Unavailable'
  return cases.filter((entry) => entry.privacyState === 'ACTIVE'
    && !['DELETE_REQUESTED', 'PURGING', 'DELETED'].includes(entry.processingStatus)
    && (entry.purgeAt === null || Date.parse(entry.purgeAt) > now)
    && (managed.has(entry.jobId) || entry.grants.some((grant) => grant.username === user.username
      && (grant.expiresAt === null || Date.parse(grant.expiresAt) > now))))
    .map((entry) => ({ caseId: entry.caseId, jobId: entry.jobId, jobTitle: entry.jobTitle,
      version: entry.version, processingStatus: entry.processingStatus,
      processingLabel: processingLabels[entry.processingStatus] || 'Unavailable',
      statusType: entry.processingStatus.startsWith('FAILED') ? 'danger'
        : entry.processingStatus === 'AUTOMATED_COMPLETE' ? 'success' : 'info',
      humanStatus: { standard: humanLabel(entry.humanStatus.standard), instructedBias: humanLabel(entry.humanStatus.instructedBias) },
      createdAt: entry.createdAt, purgeAt: entry.purgeAt,
      accessLabel: managed.has(entry.jobId) ? 'Managed job' : 'Explicit grant',
    }))
}

export function filterHrCases(rows, { keyword = '', jobId = '', sort = 'latest' }) {
  const query = keyword.trim().toLowerCase()
  return rows.filter((row) => (!jobId || row.jobId === jobId)
    && (!query || [row.caseId, row.jobTitle].some((value) => value.toLowerCase().includes(query))))
    .sort((a, b) => (sort === 'oldest' ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt))
      || a.caseId.localeCompare(b.caseId, 'en'))
}
