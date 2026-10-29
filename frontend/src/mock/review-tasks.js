export const reviewStatuses = ['Pending', 'Submitted']
export const reviewConditions = ['Standard', 'Instructed-Bias']
export function createReviewTasks(now = Date.now()) {
  const rows = ['hr', 'researcher'].flatMap((assignedTo, i) => [
    { reviewId: `REV-${i ? 'R' : 'H'}-001`, job: 'Software Engineer v3', condition: 'Standard', purpose: 'Primary', status: 'Pending' },
    { reviewId: `REV-${i ? 'R' : 'H'}-002`, job: 'Backend Developer v1', condition: 'Instructed-Bias', purpose: 'Research', status: 'Pending' },
    { reviewId: `REV-${i ? 'R' : 'H'}-003`, job: 'Data Analyst v2', condition: 'Standard', purpose: 'Primary', status: 'Submitted', decision: 'Accept', reason: 'Private submitted example.' },
  ].map((row, j) => ({ ...row, assignedTo, assignedAt: `2026-10-0${8 - j}T10:00:00.000Z`,
    caseId: `CASE-${i + 1}0${j + 1}`, experimentId: row.purpose === 'Research' ? 'EXP-003' : null,
    access: { allowed: true, expiresAt: null }, privacy: 'ACTIVE', purgeAt: new Date(now + 30 * 86400000).toISOString() })))
  return [...rows,
    { ...rows[0], reviewId: 'UNASSIGNED', assignedTo: 'another-hr' },
    { ...rows[0], reviewId: 'REVOKED', access: { allowed: false, expiresAt: null } },
    { ...rows[0], reviewId: 'EXPIRED', purgeAt: new Date(now).toISOString() },
    { ...rows[0], reviewId: 'PURGING', privacy: 'PURGING' },
  ]
}
export function selectReviewTaskSummaries(entries, user, now = Date.now()) {
  if (!['HR', 'Researcher'].includes(user?.role)) throw new Error('An HR or Researcher demo session is required to view assigned review tasks.')
  return entries.filter(row => row.assignedTo === user.username && row.access?.allowed === true
    && (row.access.expiresAt === null || Date.parse(row.access.expiresAt) > now)
    && row.privacy === 'ACTIVE' && (row.purgeAt === null || Date.parse(row.purgeAt) > now)
    && reviewStatuses.includes(row.status) && reviewConditions.includes(row.condition)
    && ['Primary', 'Research'].includes(row.purpose))
    .map(({ reviewId, job, condition, purpose, status, assignedAt, experimentId }) => ({ reviewId, job, condition, purpose, status, assignedAt, experimentId }))
}
export function researchAssignmentTasks(research) {
  const tasks = []
  for (const experiment of research.experiments) for (const review of experiment.reviews) {
    const subject = research.subjects.find(row => row.id === review.subjectId)
    if (subject) tasks.push({ ...review, reviewId: review.id, job: experiment.job, experimentId: experiment.id,
      privacy: subject.privacy, purgeAt: subject.purgeAt })
  }
  for (const subject of research.subjects) for (const review of subject.primaryReviews || []) tasks.push({ ...review,
    reviewId: `${subject.id}-PRIMARY-${review.condition}`, job: subject.name, experimentId: null,
    privacy: subject.privacy, purgeAt: subject.purgeAt })
  return tasks
}
export function filterReviewTasks(rows, { keyword = '', status = '', condition = '', purpose = '', sort = 'latest' } = {}) {
  const search = keyword.trim().toLowerCase()
  return rows.filter(row => (!search || [row.reviewId, row.job, row.experimentId].some(value => String(value || '').toLowerCase().includes(search)))
    && (!status || row.status === status) && (!condition || row.condition === condition) && (!purpose || row.purpose === purpose))
    .sort((a, b) => (sort === 'oldest' ? 1 : -1) * (Date.parse(a.assignedAt) - Date.parse(b.assignedAt)) || a.reviewId.localeCompare(b.reviewId, 'en'))
}
