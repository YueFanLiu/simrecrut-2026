import { demoMode } from '../config/runtime.js'
const managedJobs = [
  { jobId: 'hr-job-008', owner: 'hr', title: 'Data Analyst', department: 'Data',
    createdAt: '2026-10-08', status: 'Published', publishedVersion: '1.0', draftVersion: null,
    summary: 'Analyze business data with SQL and Python.' },
  { jobId: 'hr-job-007', owner: 'hr', title: 'Backend Developer', department: 'Engineering',
    createdAt: '2026-10-06', status: 'Draft', publishedVersion: null, draftVersion: '0.1',
    summary: 'Develop Java services and relational database integrations.' },
  { jobId: 'hr-job-006', owner: 'hr', title: 'Project Manager', department: 'Management',
    createdAt: '2026-10-05', status: 'Published', publishedVersion: '1.1', draftVersion: null,
    summary: 'Coordinate project delivery and communicate milestones.' },
  { jobId: 'hr-job-005', owner: 'hr', title: 'UX Designer', department: 'Design',
    createdAt: '2026-10-03', status: 'Draft', publishedVersion: null, draftVersion: '0.2',
    summary: 'Design accessible interfaces and test usability.' },
  { jobId: 'hr-job-004', owner: 'hr', title: 'Software Engineer', department: 'Engineering',
    createdAt: '2026-10-01', status: 'Published', publishedVersion: '1.2', draftVersion: '1.3',
    summary: 'Maintain web applications with Java and Vue.' },
  { jobId: 'hr-job-003', owner: 'hr', title: 'Research Assistant', department: 'Research',
    createdAt: '2026-09-29', status: 'Draft', publishedVersion: null, draftVersion: '0.1',
    summary: 'Prepare recruitment research datasets and reports.' },
  { jobId: 'hr-job-002', owner: 'hr', title: 'Product Manager', department: 'Management',
    createdAt: '2026-09-25', status: 'Published', publishedVersion: '1.0', draftVersion: null,
    summary: 'Define product priorities and gather user feedback.' },
  { jobId: 'hr-job-001', owner: 'hr', title: 'Frontend Intern', department: 'Engineering',
    createdAt: '2026-09-20', status: 'Draft', publishedVersion: null, draftVersion: '0.1',
    summary: 'Build responsive Vue interfaces and improve accessibility.' },
  { jobId: 'other-managed-job', owner: 'other-hr', title: 'Private Job', department: 'Private',
    createdAt: '2026-10-09', status: 'Draft', draftVersion: '0.1', summary: 'Excluded from this HR demo.' },
]

export function selectManagedJobSummaries(jobs, username) {
  return jobs.filter((job) => job.owner === username).map((job) => ({
    jobId: job.jobId, title: job.title, department: job.department,
    createdAt: job.createdAt, status: job.status, summary: job.summary,
    publishedVersion: job.publishedVersion || null, draftVersion: job.draftVersion || null,
  }))
}

export function createManagedJobs() { return structuredClone(managedJobs) }

export async function loadHrJobs(user, scenario = 'normal', source = managedJobs) {
  if (!demoMode) return (await import('../api/jobs.js')).managedJobs()
  if (user?.role !== 'HR') throw new Error('This demo workspace is available to HR accounts only.')
  await new Promise((resolve) => setTimeout(resolve, 350))
  if (scenario === 'error') throw new Error('Unable to load managed jobs. Please try again.')
  return scenario === 'empty' ? [] : selectManagedJobSummaries(source, user.username)
}

export function filterHrJobs(jobs, { keyword = '', status = '', sort = 'latest' }) {
  const query = keyword.trim().toLowerCase()
  const filtered = jobs.filter((job) => (!status || job.status === status)
    && (!query || [job.title, job.department, job.summary].some((value) => value.toLowerCase().includes(query))))
  return filtered.sort((a, b) => (sort === 'oldest'
    ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt))
    || a.jobId.localeCompare(b.jobId, 'en'))
}
