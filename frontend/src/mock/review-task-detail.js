import { researchAssignmentTasks, selectReviewTaskSummaries } from './review-tasks.js'

const requirements = {
  'Software Engineer v3': ['Java or Python proficiency', 'Web development and REST APIs', 'SQL databases', 'Teamwork and problem solving'],
  'Backend Developer v1': ['Backend development experience', 'REST APIs and SQL databases', 'Automated testing and debugging'],
  'Data Analyst v2': ['Python and SQL', 'Data cleaning and analysis', 'Clear reporting and communication'],
}
const fields = ['experience', 'education', 'skills', 'projects', 'languages']
const demoProfessional = { experience: '3 years developing web applications and REST APIs; 6 months as a software development intern.', education: "Bachelor's degree in Computer Science. Institution name withheld.", skills: 'Java, Python, SQL, Spring Boot, Docker and Linux', projects: 'Web platform: Java, Vue and MySQL. Data analysis tool: Python and Pandas.', languages: 'English B2' }
export function validateReviewSubmission({ decision, reason }) {
  if (!['Accept', 'Reject'].includes(decision)) return 'Select Accept or Reject.'
  if (typeof reason !== 'string' || !reason.trim() || reason.trim().length > 2000) return 'Enter a reason of 1 to 2000 characters.'
  return ''
}
// Resolve the original record so submission updates R assignments, not an adapter copy.
export function resolveReviewTask(tasks, research, id, user, now = Date.now()) {
  const summary = selectReviewTaskSummaries([...tasks, ...researchAssignmentTasks(research)], user, now).find(row => row.reviewId === id)
  if (!summary) throw new Error('This task is unavailable or access is no longer authorised.')
  let record = tasks.find(row => row.reviewId === id), professional = demoProfessional, controlled = null
  if (record) {
    if (record.condition === 'Instructed-Bias') controlled = { instructionAvailable: true, attribute: 'Referral', value: 'Category B (synthetic)', instruction: 'For this instructed-bias demo only, favour the assigned referral category when making your decision. Record your reasoning. This is an experimental instruction, not a real hiring policy.' }
  } else {
    for (const experiment of research.experiments) {
      const review = experiment.reviews.find(row => row.id === id)
      if (!review) continue
      const subject = research.subjects.find(row => row.id === review.subjectId)
      const pair = experiment.pairs.find(row => [`${row.id}-baseline`, `${row.id}-variant`].includes(review.target))
      record = review
      professional = pair ? (review.target.endsWith('-variant') ? pair.variantProfessional : pair.professional) : subject?.professional
      if (pair && review.condition === 'Instructed-Bias') controlled = { instructionAvailable: typeof review.instruction === 'string' && Boolean(review.instruction.trim()), attribute: pair.factor, value: review.target.endsWith('-variant') ? pair.variant : pair.baseline, instruction: review.instruction?.trim() || 'No instruction was retained for this assignment. Ask the assigner to clarify before submitting.' }
      break
    }
    if (!record) for (const subject of research.subjects) {
      record = (subject.primaryReviews || []).find(row => `${subject.id}-PRIMARY-${row.condition}` === id)
      if (record) { professional = subject.professional; break }
    }
  }
  if (!record) throw new Error('This task is unavailable or access is no longer authorised.')
  const detail = { ...summary, professional: Object.fromEntries(fields.map(key => [key, typeof professional?.[key] === 'string' ? professional[key] : 'Not available'])),
    requirements: [...(requirements[summary.job] || ['Public job requirements are unavailable for this version.'])],
    rubric: ['Compare the professional facts with the public job requirements.', 'Select Accept or Reject and explain the relevant evidence or gaps.', 'Do not infer the candidate identity. Other assessments are hidden.'] }
  if (summary.condition === 'Instructed-Bias') detail.controlled = controlled || { instructionAvailable: false, attribute: 'Unavailable', value: 'Unavailable', instruction: 'No controlled attributes or instructions were retained for this assignment. Ask the assigner to clarify before submitting.' }
  if (summary.status === 'Submitted') {
    detail.submission = validateReviewSubmission(record) ? null : { decision: record.decision, reason: record.reason, submittedAt: record.submittedAt || null }
  }
  return { record, detail }
}
