import { projectCaseCheckpoints } from '../utils/demo-session.js'
import { createSimulationResults } from '../mock/simulation-results.js'

export function installDemoCheckpoints({ store }) {
  if (store.$id === 'researchModule') {
    const key = 'simrecrut-experiment-checkpoints-v1'
    try {
      const saved = JSON.parse(sessionStorage.getItem(key) || 'null')
      if (Array.isArray(saved)) {
        store.experiments = saved.map(row => ({ ...row, reviews: [],
          status: row.status === 'Running' ? 'Draft' : row.status,
          execution: row.status === 'Running' ? 'Demo interrupted. Run again after checking subject access.' : row.execution,
          pairs: row.pairs.map(pair => {
            const subject = store.subjects.find(subject => subject.id === pair.subjectId)
            return { ...pair, professional: subject?.professional || {}, variantProfessional: subject?.professional || {} }
          }) }))
      }
    } catch { /* Keep bundled experiments when storage is unavailable. */ }
    store.$subscribe((mutation, state) => {
      const rows = state.experiments.map(({ id, name, type, job, weight, protocol, neutral, biased, owner, grants,
        subjects, status, date, subjectIds, factor, pairs, execution, outputs }) => ({
        id, name, type, job, weight, protocol, neutral, biased, owner, grants, subjects, status, date, subjectIds, factor,
        pairs: pairs.map(({ id, subjectId, factor, baseline, variant }) => ({ id, subjectId, factor, baseline, variant })),
        execution, outputs }))
      try { sessionStorage.setItem(key, JSON.stringify(rows)) }
      catch { /* Experiments remain usable in memory. */ }
    }, { detached: true, flush: 'sync' })
    return
  }
  if (['hrJobs', 'adminModels'].includes(store.$id)) {
    const key = `simrecrut-${store.$id}-metadata-v1`
    const restore = () => {
      try {
        const saved = JSON.parse(sessionStorage.getItem(key) || 'null')
        if (saved && Object.keys(store.$state).every(field => field in saved)) store.$patch(saved)
      } catch { /* Keep the bundled metadata if storage is unavailable. */ }
    }
    restore()
    let resetting = false
    store.$onAction(({ name, after }) => {
      if (!['clearJobs', 'clearModels'].includes(name)) return
      resetting = true
      after(() => { restore(); resetting = false })
    })
    store.$subscribe((mutation, state) => {
      if (resetting) return
      try { sessionStorage.setItem(key, JSON.stringify(state)) }
      catch { /* HR configuration and model metadata remain usable in memory. */ }
    }, { detached: true, flush: 'sync' })
    return
  }
  if (store.$id !== 'cases') return
  const key = 'simrecrut-case-checkpoints-v1'
  try {
    const saved = JSON.parse(sessionStorage.getItem(key) || 'null')
    if (saved?.cases && Number.isInteger(saved.nextCaseNumber)) {
      // Re-project storage before restoring: private fields cannot enter the store.
      const safe = projectCaseCheckpoints(saved)
      for (const entry of Object.values(safe.cases)) {
        Object.assign(entry, { operation: 0, file: null, preview: null, reviewDraft: null,
          confirmedProfessional: null, researchAllowed: false, researchAttributes: {}, reviewRevision: 0, failure: '', stage: '' })
        if (entry.processingStatus === 'AUTOMATED_RUNNING') {
          entry.processingStatus = 'FAILED_RETRYABLE'
          entry.failure = 'The demo run was interrupted by a reload. Retry to continue unfinished stages.'
          entry.assessment.retryable = entry.assessment.retryCount < 1
          entry.assessment.methods.forEach(method => { if (method.status === 'Running') method.status = 'Waiting' })
        }
        if (entry.processingStatus === 'AUTOMATED_COMPLETE') {
          entry.results = createSimulationResults(entry)
          entry.results.completedAt = entry.assessment.updatedAt
        }
      }
      store.$patch(safe)
    }
  } catch { /* Invalid or unavailable storage starts with empty case history. */ }
  store.$subscribe((mutation, state) => {
    try { sessionStorage.setItem(key, JSON.stringify(projectCaseCheckpoints(state))) }
    catch { /* Storage errors cannot block local demo operations. */ }
  }, { detached: true, flush: 'sync' })
}
