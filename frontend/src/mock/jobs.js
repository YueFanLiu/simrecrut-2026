import { readDemoPublications } from './shared-demo.js'

const jobVersions = [
  {
    jobId: 'software-engineer', title: 'Software Engineer', company: 'TechCorp',
    location: 'Paris, France', employmentType: 'Full-time', category: 'Tech',
    currentVersion: '1.2', publishedAt: '2026-10-08', status: 'Published', isCurrentPublished: true,
    summary: 'Build and maintain web applications with Java, Vue and modern development tools.',
  },
  {
    jobId: 'data-analyst', title: 'Data Analyst', company: 'DataInsight',
    location: 'Paris, France', employmentType: 'Full-time', category: 'Data',
    currentVersion: '1.0', publishedAt: '2026-10-06', status: 'Published', isCurrentPublished: true,
    summary: 'Analyze business data with SQL and Python, and communicate findings through clear visualizations.',
  },
  {
    jobId: 'ai-research-intern', title: 'AI Research Intern', company: 'AI Lab',
    location: 'Paris, France', employmentType: 'Internship', category: 'Research',
    currentVersion: '1.1', publishedAt: '2026-10-04', status: 'Published', isCurrentPublished: true,
    summary: 'Support machine learning experiments, model evaluation and responsible AI research.',
  },
  {
    jobId: 'product-manager', title: 'Product Manager', company: 'InnovateCo',
    location: 'Lyon, France', employmentType: 'Full-time', category: 'Management',
    currentVersion: '1.0', publishedAt: '2026-09-28', status: 'Published', isCurrentPublished: true,
    summary: 'Define product priorities and coordinate delivery with design and engineering teams.',
  },
  {
    jobId: 'ux-designer', title: 'UI/UX Designer', company: 'CreativeStudio',
    location: 'Remote', employmentType: 'Full-time', category: 'Design',
    currentVersion: '1.3', publishedAt: '2026-09-25', status: 'Published', isCurrentPublished: true,
    summary: 'Create accessible interfaces, interactive prototypes and practical design systems.',
  },
  {
    jobId: 'backend-developer', title: 'Backend Developer', company: 'CloudSystems',
    location: 'Paris, France', employmentType: 'Full-time', category: 'Tech',
    currentVersion: '1.0', publishedAt: '2026-09-20', status: 'Published', isCurrentPublished: true,
    summary: 'Develop reliable services with Java, Spring Boot and relational databases.',
  },
  {
    jobId: 'research-assistant', title: 'Research Assistant', company: 'Fairness Institute',
    location: 'Lyon, France', employmentType: 'Part-time', category: 'Research',
    currentVersion: '1.0', publishedAt: '2026-09-18', status: 'Published', isCurrentPublished: true,
    summary: 'Prepare research datasets and support the analysis of recruitment assessment methods.',
  },
  {
    jobId: 'frontend-intern', title: 'Frontend Developer Intern', company: 'WebWorks',
    location: 'Remote', employmentType: 'Internship', category: 'Tech',
    currentVersion: '1.0', publishedAt: '2026-09-15', status: 'Published', isCurrentPublished: true,
    summary: 'Help build responsive Vue interfaces and improve usability through focused testing.',
  },
  {
    jobId: 'software-engineer', title: 'Software Engineer', company: 'TechCorp',
    currentVersion: '1.1', publishedAt: '2026-09-01', status: 'Published', isCurrentPublished: false,
    summary: 'Historical version excluded from the Candidate list.',
  },
  {
    jobId: 'software-engineer', title: 'Software Engineer Draft', company: 'TechCorp',
    currentVersion: '1.3', status: 'Draft', isCurrentPublished: false,
    summary: 'A newer draft must not replace the current published version.',
  },
  {
    jobId: 'retired-position', title: 'Retired Position', status: 'Retired', isCurrentPublished: false,
  },
]

/** Return only current Published versions and explicitly listed Candidate-facing fields. */
export function selectPublishedJobSummaries(versions) {
  return versions.filter((job) => job.status === 'Published' && job.isCurrentPublished)
    .map((job) => ({
      jobId: job.jobId,
      title: job.title,
      company: job.company,
      location: job.location,
      employmentType: job.employmentType,
      category: job.category,
      currentVersion: job.currentVersion,
      publishedAt: job.publishedAt,
      summary: job.summary,
    }))
}

/** Local demo boundary; replace with the approved Candidate list API in a later phase. */
export async function loadPublishedJobs(scenario = 'normal') {
  await new Promise((resolve) => setTimeout(resolve, 350))
  if (scenario === 'error') throw new Error('Unable to load published jobs. Please try again.')
  if (scenario === 'empty') return []
  return selectPublishedJobSummaries([...jobVersions, ...readDemoPublications()])
}
