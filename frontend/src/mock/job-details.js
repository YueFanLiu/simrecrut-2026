import { demoMode } from '../config/runtime.js'
import { loadPublishedJobs } from './jobs.js'
import { readDemoPublications } from './shared-demo.js'

const profiles = {
  'software-engineer': {
    description: 'We are looking for a software engineer to develop and maintain web applications. You will work in a collaborative team and contribute to the design, development and deployment of modern web solutions.',
    responsibilities: ['Develop and maintain web applications.', 'Participate in technical design and code review.', 'Collaborate with product and design teams.', 'Improve application performance and reliability.'],
    qualifications: ['Experience with Java, Vue and relational databases.', 'An understanding of version control and software testing.', 'Clear communication and practical problem-solving skills.'],
    experience: '2+ years', education: 'Bachelor or higher', language: 'English (B2 or above)',
    industry: 'Information Technology', companySize: '100-500 employees',
  },
  'data-analyst': {
    description: 'Join our analytics team to turn business data into useful insights. You will prepare datasets, explore trends and communicate findings to colleagues.',
    responsibilities: ['Prepare and validate business datasets.', 'Analyze trends with SQL and Python.', 'Create clear reports and visualizations.'],
    qualifications: ['Practical SQL and Python skills.', 'An understanding of statistics and data quality.', 'Ability to explain analytical findings clearly.'],
    experience: '1+ year', education: 'Bachelor or higher', language: 'English (B2 or above)',
    industry: 'Data Analytics', companySize: '50-100 employees',
  },
  'ai-research-intern': {
    description: 'Support an applied AI research team studying model quality and responsible use. This internship combines experiment preparation, evaluation and research reporting.',
    responsibilities: ['Prepare reproducible machine learning experiments.', 'Evaluate model behavior and document findings.', 'Discuss research results with the team.'],
    qualifications: ['Currently studying computer science or a related subject.', 'Basic Python and machine learning knowledge.', 'Interest in responsible AI research.'],
    experience: 'Student projects welcome', education: 'Currently enrolled in a degree',
    language: 'English (B2 or above)', industry: 'AI Research', companySize: '10-50 employees',
  },
  'product-manager': {
    description: 'Help a product team prioritize useful improvements and coordinate delivery. Work closely with users, designers and engineers to define clear outcomes.',
    responsibilities: ['Gather user needs and define product priorities.', 'Write clear delivery goals.', 'Coordinate work across design and engineering.'],
    qualifications: ['Experience coordinating digital products.', 'Strong written and verbal communication.', 'Ability to balance user needs and delivery constraints.'],
    experience: '3+ years', education: 'Bachelor or higher', language: 'English (B2 or above)',
    industry: 'Digital Products', companySize: '100-500 employees',
  },
  'ux-designer': {
    description: 'Design accessible interfaces for digital products. You will explore user needs, build prototypes and work with developers to improve usability.',
    responsibilities: ['Create interface designs and interactive prototypes.', 'Gather feedback through usability studies.', 'Maintain practical design guidelines.'],
    qualifications: ['A portfolio of interface design work.', 'Experience with prototyping and accessibility.', 'Ability to collaborate with developers.'],
    experience: '2+ years', education: 'Relevant degree or equivalent experience',
    language: 'English (B2 or above)', industry: 'Design Services', companySize: '10-50 employees',
  },
  'backend-developer': {
    description: 'Build reliable application services with an experienced engineering team. Contribute to service design, data access and practical testing.',
    responsibilities: ['Develop and maintain Java services.', 'Design reliable database interactions.', 'Review code and investigate service issues.'],
    qualifications: ['Experience with Java and Spring Boot.', 'Practical relational database knowledge.', 'An understanding of automated testing.'],
    experience: '2+ years', education: 'Bachelor or higher', language: 'English (B2 or above)',
    industry: 'Cloud Software', companySize: '100-500 employees',
  },
  'research-assistant': {
    description: 'Support research into recruitment assessment methods. Help prepare research materials and communicate results in clear, reproducible reports.',
    responsibilities: ['Prepare and document research datasets.', 'Support analysis of assessment methods.', 'Contribute to research reports and discussions.'],
    qualifications: ['Basic statistics and data analysis knowledge.', 'Careful documentation habits.', 'Interest in recruitment research.'],
    experience: 'Research projects welcome', education: 'Bachelor or higher',
    language: 'English (B2 or above)', industry: 'Research', companySize: '10-50 employees',
  },
  'frontend-intern': {
    description: 'Join a web team to develop responsive interfaces and learn through code review. Work on small, focused improvements to the user experience.',
    responsibilities: ['Build Vue components with guidance from the team.', 'Improve responsive layouts.', 'Check usability and document interface issues.'],
    qualifications: ['Basic HTML, CSS and JavaScript skills.', 'Familiarity with Vue through student projects.', 'Willingness to learn and receive feedback.'],
    experience: 'Student projects welcome', education: 'Currently enrolled in a degree',
    language: 'English (B2 or above)', industry: 'Web Development', companySize: '10-50 employees',
  },
}

/** Local public detail shape; requirements will follow the agreed API schema in a later phase. */
export function selectPublicJobDetail(job, profile) {
  return {
    jobId: job.jobId, jobVersionId: `${job.jobId}-v${job.currentVersion}`,
    version: job.currentVersion, title: job.title, company: job.company,
    location: job.location, employmentType: job.employmentType, category: job.category,
    publishedAt: job.publishedAt, description: profile.description,
    requirements: {
      responsibilities: [...profile.responsibilities], qualifications: [...profile.qualifications],
      experience: profile.experience, education: profile.education, language: profile.language,
    },
    applicationDeadline: '2026-12-31',
    industry: profile.industry, companySize: profile.companySize,
  }
}

/** Resolve the current published version only; unknown or unavailable jobs return null. */
export async function loadJobDetail(jobId, scenario = 'normal') {
  if (!demoMode) return (await import('../api/jobs.js')).jobDetail(jobId)
  const jobs = await loadPublishedJobs(scenario === 'unavailable' ? 'empty' : 'normal')
  if (scenario === 'error') throw new Error('Unable to load job details. Please try again.')
  const job = jobs.find((entry) => entry.jobId === jobId)
  const profile = profiles[jobId] || readDemoPublications().find(row => row.jobId === jobId)?.profile
  return job && profile ? selectPublicJobDetail(job, profile) : null
}
