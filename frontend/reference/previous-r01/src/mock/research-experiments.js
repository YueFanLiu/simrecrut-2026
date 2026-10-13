export const experimentStatuses = ['Draft', 'Running', 'Completed']

export function createResearchExperiments(now = Date.now()) {
  const names = ['School Prestige Comparison', 'Referral Comparison', 'Gender Comparison',
    'Professional Reference Study', 'Controlled Profile Comparison', 'Model Version Comparison', 'Baseline Comparison']
  const rows = names.map((name, i) => ({
    experimentId: `experiment-00${7 - i}`, name,
    description: 'Fixed example configuration for a recruitment comparison.',
    jobId: i % 2 ? 'research-job-002' : 'research-job-001',
    jobTitle: i % 2 ? 'Data Analyst' : 'Software Engineer',
    versions: { job: i % 2 ? '1.0' : '1.2', weights: i % 2 ? 'data-weights-1' : 'software-weights-1',
      protocol: 'mock-protocol-1', neutral: 'mock-neutral-1', biased: 'mock-biased-1' },
    subjectCount: [12, 8, 10, 0, 6, 0, 4][i],
    status: ['Completed', 'Running', 'Completed', 'Draft', 'Running', 'Draft', 'Completed'][i],
    createdAt: `2026-10-${String(8 - i).padStart(2, '0')}T10:00:00.000Z`,
    owner: i === 4 ? 'another-researcher' : 'researcher',
    grants: i === 4 ? [{ username: 'researcher', expiresAt: new Date(now + 86400000).toISOString() }] : [],
  }))
  return [...rows,
    { ...rows[0], experimentId: 'ungranted-experiment', name: 'Restricted Experiment', owner: 'another-researcher', grants: [] },
    { ...rows[0], experimentId: 'expired-experiment', name: 'Expired Experiment', owner: 'another-researcher',
      grants: [{ username: 'researcher', expiresAt: new Date(now).toISOString() }] },
  ]
}

export function selectResearchExperiments(entries, user, now = Date.now()) {
  if (user?.role !== 'Researcher') throw new Error('A Researcher demo session is required to view experiments.')
  return entries.filter((entry) => entry.owner === user.username || entry.grants.some((grant) =>
    grant.username === user.username && (grant.expiresAt === null || Date.parse(grant.expiresAt) > now)))
    .map((entry) => ({ experimentId: entry.experimentId, name: entry.name, description: entry.description,
      jobId: entry.jobId, jobTitle: entry.jobTitle, subjectCount: entry.subjectCount,
      status: entry.status, createdAt: entry.createdAt,
      versions: { job: entry.versions.job, weights: entry.versions.weights, protocol: entry.versions.protocol,
        neutral: entry.versions.neutral, biased: entry.versions.biased },
    }))
}

export function filterResearchExperiments(rows, { keyword = '', status = '', jobId = '', sort = 'latest' } = {}) {
  const search = keyword.trim().toLowerCase()
  return rows.filter((row) => (!search || [row.experimentId, row.name, row.description].some((value) => value.toLowerCase().includes(search)))
    && (!status || row.status === status) && (!jobId || row.jobId === jobId))
    .sort((a, b) => (sort === 'oldest' ? 1 : -1) * (Date.parse(a.createdAt) - Date.parse(b.createdAt))
      || a.experimentId.localeCompare(b.experimentId, 'en'))
}
