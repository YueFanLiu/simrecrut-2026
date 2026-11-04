// Page contracts for published jobs and the HR version workflow.
import { pageItems, pageRequest, resourceId as id } from './page-request.js'
import { criteria, validateWeights, weightUnits } from '../mock/hr-job-wizard.js'
export const publishedJobs = () => pageItems('/api/v1/candidate/jobs')
export const jobDetail = job => pageRequest(`/api/v1/candidate/jobs/${id(job)}`)
const hrJob = job => `/api/v1/hr/jobs/${id(job)}`
const status = row => row.status === 'DRAFT' ? 'Draft' : 'Published'
const description = form => ({ title: form.title.trim(), description: form.description.trim() })
const same = (left, right) => JSON.stringify(left) === JSON.stringify(right)

function draftForm(row) {
  return {
    title: row.title, description: row.description,
    requirements: row.requirements.map(({ criterionType, requirementCode, payload, ...requirement }) => ({
      ...requirement, id: requirementCode, category: criteria.find(category => category.toUpperCase() === criterionType),
      text: requirement.evidenceExcerpt || requirementCode, payloadText: JSON.stringify(payload, null, 2),
    })),
    activeCriteria: criteria.filter(category => row.activeCriteria.includes(category.toUpperCase())),
    percentages: criteria.map(category => String(Math.round(row.weights[category.toLowerCase()] * 10000000) / 100000)),
  }
}

function requirementsFor(form, descriptionChanged) {
  return form.requirements.map(row => {
    let payload
    try { payload = JSON.parse(row.payloadText) } catch {
      throw new Error(`Enter valid JSON for requirement ${row.id}.`)
    }
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      throw new Error(`Requirement ${row.id} needs a JSON object.`)
    }
    return {
      criterionType: row.category.toUpperCase(), requirementCode: row.id, payload,
      assessmentIncluded: row.assessmentIncluded, mandatory: row.mandatory, publicVisible: row.publicVisible,
      reviewed: !descriptionChanged && row.reviewed,
      evidenceExcerpt: descriptionChanged ? null : row.evidenceExcerpt || null,
      evidenceLocation: descriptionChanged ? null : row.evidenceLocation || null,
      warnings: descriptionChanged ? ['The description changed. Review this requirement against the new source.'] : row.warnings,
    }
  })
}

export const managedJobs = async () => (await pageItems('/api/v1/hr/jobs')).map(row => ({
  ...row, status: status(row), department: '', createdAt: row.createdAt.slice(0, 10),
  draftVersion: row.status === 'DRAFT' ? row.version : null,
  publishedVersion: row.status === 'PUBLISHED' ? row.version : null,
}))
export async function jobDraft(job) {
  const row = await pageRequest(hrJob(job))
  return { revision: row.revision, form: draftForm(row), extraction: row.extraction }
}
export const createJob = (form, key) => pageRequest('/api/v1/hr/jobs', 'post', description(form), false, key)

export async function saveJob(job, form, revision, onSaved) {
  const url = hrJob(job)
  let row = await pageRequest(url)
  if (row.revision !== revision) throw new Error('The draft changed on the server. Reload it before saving again.')
  const basic = description(form)
  const descriptionChanged = row.description !== basic.description
  const requirements = requirementsFor(form, descriptionChanged)
  const activeCriteria = criteria.filter(category => form.activeCriteria.includes(category)).map(category => category.toUpperCase())
  const weights = Object.fromEntries(criteria.map((category, index) => [category.toLowerCase(), weightUnits(form.percentages[index]) / 10000000]))
  const currentForm = draftForm(row)
  const weightsChanged = !same(currentForm.activeCriteria, criteria.filter(category => form.activeCriteria.includes(category)))
    || !same(currentForm.percentages.map(weightUnits), form.percentages.map(weightUnits))
    || form.percentages.some(value => weightUnits(value) === null)
  if (weightsChanged) {
    const errors = validateWeights(form)
    if (errors.length) throw new Error(errors.join(' '))
  }
  async function put(path, fields, invalidated = false) {
    const body = { ...fields, revision: row.revision }
    row = await pageRequest(url + path, 'put', body)
    onSaved?.(row.revision, invalidated)
  }
  if (!same(basic, description(row))) await put('', basic, descriptionChanged)
  if (!same(requirements, requirementsFor(draftForm(row), false))) await put('/requirements', { requirements })
  if (weightsChanged) await put('/weights', { activeCriteria, weights })
  return { revision: row.revision, form: draftForm(row) }
}

export async function extractJob(job, key, revision, isCurrent = () => true) {
  const url = hrJob(job)
  let state = await pageRequest(`${url}/extraction-status`)
  if (state.status !== 'RUNNING' && !(state.status === 'COMPLETE' && state.revision !== revision)) {
    state = await pageRequest(`${url}/extract-requirements`, 'post', {}, false, key)
  }
  const deadline = Date.now() + 310000
  while (state.status === 'RUNNING' && isCurrent() && Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 1500))
    if (!isCurrent()) return
    state = await pageRequest(`${url}/extraction-status`)
  }
  if (!isCurrent()) return
  if (state.status !== 'COMPLETE') throw new Error(state.message || 'Extraction is still processing. Check its status again.')
  return jobDraft(job)
}
export const validateJob = job => pageRequest(`/api/v1/hr/jobs/${id(job)}/validate`, 'post', {})
export const publishJob = (job, revision, key) => pageRequest(`${hrJob(job)}/publish`, 'post', { revision }, false, key)
export async function jobVersions(job) {
  const rows = await pageRequest(`${hrJob(job)}/versions`)
  const draft = rows.find(row => row.status === 'DRAFT')
  const published = rows.filter(row => row.status === 'PUBLISHED').sort((a, b) => b.version - a.version)[0]
  return { jobId: job, title: (draft || published)?.title, draftVersion: draft?.version, publishedVersion: published?.version,
    versions: rows.map(row => ({ ...row, versionId: row.jobVersionId, status: status(row), current: row === published })) }
}
export async function jobVersion(job, version) {
  const row = await pageRequest(`${hrJob(job)}/versions/${id(version)}`)
  return { ...row, ...draftForm(row), versionId: row.jobVersionId, status: status(row) }
}
export const cloneJob = (job, version, key) => pageRequest(`${hrJob(job)}/drafts`, 'post', { sourceJobVersionId: version }, false, key)

export function publicRequirementLabel({ criterionType, requirementCode, mandatory, payload }) {
  const details = Object.values(payload).map(value => Array.isArray(value)
    ? value.map(item => typeof item === 'object' ? Object.values(item).join(' ') : item).join(', ')
    : typeof value === 'boolean' ? '' : value).filter(Boolean).join(' · ')
  return `${criterionType} · ${requirementCode}${mandatory ? ' (mandatory)' : ''}${details ? ' · ' + details : ''}`
}
