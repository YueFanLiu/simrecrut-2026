export const criteria = ['Skills', 'Experience', 'Education', 'Languages', 'Projects']

export function createJobForm(job = {}) {
  return {
    title: job.title || '', department: job.department || '', description: job.summary || '',
    employmentType: 'Full-time', experienceLevel: 'Junior (0-2 years)', location: '', positions: 1,
    requirements: [], activeCriteria: [...criteria], percentages: ['20', '20', '20', '20', '20'],
    extractedDescription: '',
  }
}

export function validateDescription(form) {
  const errors = []
  if (!form.title.trim() || form.title.length > 100) errors.push('Enter a job title of 1 to 100 characters.')
  if (!form.department.trim() || form.department.length > 100) errors.push('Enter a department of 1 to 100 characters.')
  if (!form.description.trim() || form.description.length > 2000) errors.push('Enter a description of 1 to 2000 characters.')
  if (!['Full-time', 'Part-time', 'Internship', 'Contract'].includes(form.employmentType)) errors.push('Select an employment type.')
  if (!['Junior (0-2 years)', 'Mid-level (3-5 years)', 'Senior (6+ years)'].includes(form.experienceLevel)) errors.push('Select an experience level.')
  if (!Number.isInteger(form.positions) || form.positions < 1 || form.positions > 100) errors.push('Number of positions must be an integer from 1 to 100.')
  if (form.location.length > 100) errors.push('Location must be at most 100 characters.')
  return errors
}

// Simple local sentence grouping only. This is not AI extraction or a matching engine.
export function extractMockRequirements(description) {
  return description.split(/(?<=[.!?])\s+|\n+/).map((text) => text.trim()).filter(Boolean).map((text, index) => {
    const category = /years?|experience/i.test(text) ? 'Experience'
      : /degree|education|diploma/i.test(text) ? 'Education'
        : /english|french|language/i.test(text) ? 'Languages'
          : /project|portfolio/i.test(text) ? 'Projects' : 'Skills'
    return { id: `proposal-${index + 1}`, category, text, equivalents: '', assessmentIncluded: true,
      mandatory: false, publicVisible: true, reviewed: false, evidenceExcerpt: text,
      evidenceLocation: `Description sentence ${index + 1}` }
  })
}

export function weightUnits(value) {
  const text = String(value).trim()
  // Five percentage decimals represent seven fractional decimal places exactly.
  if (!/^\d+(\.\d{1,5})?$/.test(text)) return null
  const [whole, fraction = ''] = text.split('.')
  const units = Number(whole) * 100000 + Number(fraction.padEnd(5, '0'))
  return Number.isSafeInteger(units) && units <= 10000000 ? units : null
}

export function validateWeights(form) {
  const errors = []
  const units = form.percentages.map(weightUnits)
  if (!form.activeCriteria.length) errors.push('Select at least one active assessment category.')
  if (units.some((value) => value === null)) errors.push('Weights must be from 0 to 100 with at most five percentage decimals.')
  else {
    if (units.reduce((sum, value) => sum + value, 0) !== 10000000) errors.push('Weight percentages must total exactly 100%. Values are not normalized automatically.')
    criteria.forEach((category, index) => {
      if (!form.activeCriteria.includes(category) && units[index] !== 0) errors.push(`${category}: an inactive category must have zero weight.`)
    })
  }
  return errors
}

export function validateJob(form) {
  const errors = [...validateDescription(form), ...validateWeights(form)]
  const assessed = form.requirements.filter((row) => row.assessmentIncluded)
  if (!assessed.length) errors.push('Include at least one reviewed requirement in assessment.')
  if (form.extractedDescription && form.extractedDescription !== form.description) errors.push('The description changed after extraction. Extract again or remove the old proposals and review new requirements.')
  const ids = new Set()
  for (const row of form.requirements) {
    if (ids.has(row.id)) errors.push('Each requirement must have a unique code.')
    ids.add(row.id)
    if (!criteria.includes(row.category) || !row.text.trim() || row.text.length > 2000) errors.push('Every requirement needs a valid category and text of 1 to 2000 characters.')
    if (!row.reviewed) errors.push(`${row.category}: confirm the requirement after checking its source.`)
    if (row.mandatory && (!row.assessmentIncluded || !form.activeCriteria.includes(row.category))) errors.push(`${row.category}: mandatory conditions must be included in an active category.`)
    if (row.assessmentIncluded && !form.activeCriteria.includes(row.category)) errors.push(`${row.category}: assessed requirements need an active category.`)
  }
  for (const category of form.activeCriteria) {
    if (!criteria.includes(category) || !assessed.some((row) => row.category === category)) errors.push(`${category}: add an assessed requirement or make this category inactive.`)
  }
  return { valid: !errors.length, errors: [...new Set(errors)], warnings: form.requirements.some((row) => !row.publicVisible)
    ? ['Hidden requirements are assessed when included, but are omitted from Candidate display.'] : [] }
}
