// Public demo publications survive role switching in the same browser tab.
// Never store CV contents, evidence, credentials or research attributes here.
const key = 'simrecrut-public-jobs-v1'
let memory = []
let modelMemory = null

export function shareDemoModels(models) {
  modelMemory = models.map(({ id, type, version, status, schema, protocol, inputMode, cutoff }) =>
    ({ id, name: type, version, status: status[0] + status.slice(1).toLowerCase(), schema, protocol,
      mode: inputMode, cutoff, evaluation: 'Unavailable; synthetic package metadata only.' }))
  try { sessionStorage.setItem('simrecrut-public-models-v1', JSON.stringify(modelMemory)) }
  catch { /* Keep public model metadata in memory. */ }
}

export function readSharedDemoModels() {
  try {
    const saved = JSON.parse(sessionStorage.getItem('simrecrut-public-models-v1') || 'null')
    if (Array.isArray(saved)) modelMemory = saved
  } catch { /* Use existing public model metadata. */ }
  return modelMemory ? structuredClone(modelMemory) : null
}

export function readDemoPublications() {
  try {
    const rows = JSON.parse(sessionStorage.getItem(key) || '[]')
    if (Array.isArray(rows)) memory = rows.filter(row => row?.jobId && row?.profile && row.status === 'Published')
  } catch { /* Storage is optional; the demo remains usable in memory. */ }
  return structuredClone(memory)
}

export function publishDemoJob(job, version) {
  const rows = readDemoPublications().filter(row => row.jobId !== job.jobId)
  const requirements = version.requirements.filter(row => row.publicVisible).map(row => row.text)
  rows.push({ jobId: job.jobId, title: version.title, company: 'Demo Company',
    location: version.location || 'Not specified', employmentType: version.employmentType,
    category: version.department, currentVersion: version.version,
    publishedAt: version.createdAt.slice(0, 10), status: 'Published', isCurrentPublished: true,
    summary: version.description,
    profile: { description: version.description, responsibilities: requirements,
      qualifications: requirements, experience: version.experienceLevel,
      education: 'See public requirements', language: 'See public requirements',
      industry: version.department, companySize: 'Not specified' } })
  memory = rows
  try { sessionStorage.setItem(key, JSON.stringify(rows)) } catch { /* Use the in-memory catalog. */ }
}
