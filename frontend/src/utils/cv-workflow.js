// Only resource IDs cross between the library, job browser and case pages.
// Never carry PDF contents or arbitrary return URLs in route queries.
const scalar = value => typeof value === 'string' ? value : ''
export const cvContext = query => ({
  caseId: /^[A-Za-z0-9_-]{1,100}$/.test(scalar(query?.caseId)) ? query.caseId : '',
  resumeId: /^[a-f0-9]{32}$/.test(scalar(query?.resumeId)) ? query.resumeId : '',
})
export const resumeQuery = id => cvContext({ resumeId: id }).resumeId ? { resumeId: id } : {}
export const caseQuery = id => cvContext({ caseId: id }).caseId ? { caseId: id } : {}
export const cvReviewRoute = (id, caseId) => ({ name: 'ConnectedResumeReview', params: { id }, query: caseQuery(caseId) })
export const cvRevisionRoute = (id, caseId) => ({ name: 'CreateResumeRevision', params: { id }, query: caseQuery(caseId) })
export const cvSelectionRoute = (caseId, resumeId) => ({ name: 'CvUpload', params: { caseId }, query: resumeQuery(resumeId) })
export const jobsForResume = resumeId => ({ name: 'PublishedJobs', query: resumeQuery(resumeId) })

export function reusableCv(row, now = Date.now()) {
  return Boolean(row && (row.confirmed === true || row.confirmed === 1)
    && row.status === 'CONFIRMED'
    && (!row.expires_at || Date.parse(row.expires_at) > now))
}

const started = row => row && row.processingStatus !== 'AWAITING_UPLOAD'
// An ambiguous response must not duplicate an attachment or apply a different CV
// to a case that already advanced. A known failure releases the operation key.
export function createCvAttachment({ readCase, attach }) {
  const pending = new Map()
  return async (caseId, resumeId) => {
    const current = await readCase(caseId)
    if (started(current)) return { row: current, alreadyStarted: true }
    const signature = JSON.stringify([caseId, resumeId])
    const key = pending.get(signature) || crypto.randomUUID()
    pending.set(signature, key)
    try {
      const row = await attach(caseId, resumeId, key)
      pending.delete(signature)
      return { row, alreadyStarted: false }
    } catch (error) {
      if (error.status >= 400 && error.status < 500) {
        pending.delete(signature)
        if (error.status === 409) {
          const latest = await readCase(caseId)
          if (started(latest)) return { row: latest, alreadyStarted: true }
        }
      }
      throw error
    }
  }
}
