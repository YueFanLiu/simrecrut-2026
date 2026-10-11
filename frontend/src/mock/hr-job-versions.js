import { createJobForm, criteria, weightUnits } from './hr-job-wizard.js'

const copy = (value) => JSON.parse(JSON.stringify(value))

export function createPublishedHistory(jobs) {
  const history = {}
  for (const job of jobs.filter((item) => item.publishedVersion)) {
    const count = Number(job.publishedVersion.split('.')[1]) + 1
    history[job.jobId] = Array.from({ length: count }, (_, index) => {
      const version = `1.${index}`
      const form = createJobForm(job)
      const texts = [job.summary, 'Two years of relevant professional experience.',
        'A relevant degree or equivalent professional training.', 'Professional working English.',
        'A portfolio of completed relevant projects.']
      if (job.jobId === 'hr-job-004' && index === 0) texts[0] = 'Maintain web applications with Java.'
      form.description = texts.join('\n')
      form.requirements = criteria.map((category, row) => ({ id: `requirement-${row + 1}`, category,
        text: texts[row], equivalents: '', assessmentIncluded: true, mandatory: row === 0,
        publicVisible: true, reviewed: true, evidenceExcerpt: texts[row],
        evidenceLocation: `Description line ${row + 1}` }))
      form.percentages = index === 0 ? ['40', '30', '10', '10', '10']
        : index === 1 ? ['35', '30', '10', '10', '15'] : ['30', '25', '15', '15', '15']
      form.extractedDescription = form.description
      return { ...form, version, revision: index + 1, versionId: `${job.jobId}-v${version}`,
        createdAt: new Date(Date.parse(`${job.createdAt}T10:00:00Z`) + index * 86400000).toISOString(),
        createdBy: 'Demo HR User', changeSummary: index === 0 ? 'Initial reviewed job configuration.'
          : 'Updated professional requirements and assessment weights.',
        weights: criteria.map((category, i) => ({ category, value: weightUnits(form.percentages[i]) / 10000000 })) }
    })
  }
  return history
}

export function publishedJobForm(snapshot) {
  return copy(Object.fromEntries(Object.keys(createJobForm()).map((field) => [field, snapshot[field]])))
}

export function nextDraftVersion(versions) {
  const numbers = versions.map((entry) => entry.version.split('.').map(Number))
    .sort((a, b) => b[0] - a[0] || b[1] - a[1])
  return numbers.length ? `${numbers[0][0]}.${numbers[0][1] + 1}` : '0.1'
}

export async function waitForVersionDemo(scenario, operation) {
  await new Promise((resolve) => setTimeout(resolve, 400))
  if (scenario === `${operation}-error`) {
    const messages = { load: 'Unable to load job versions. Please try again.',
      detail: 'Unable to load this version. Please try again.',
      clone: 'Unable to create a new draft. The published version has not changed. Please try again.' }
    throw new Error(messages[operation])
  }
}
