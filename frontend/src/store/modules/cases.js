import { defineStore } from 'pinia'
import { useAuthStore } from './auth.js'
import { loadJobDetail } from '../../mock/job-details.js'
import { createReviewDraft } from '../../mock/review-draft.js'
import { createSimulationResults, selectCandidateResults, submittedReview } from '../../mock/simulation-results.js'
import { selectCaseSummary, sortCaseSummaries } from '../../utils/case-history.js'
import { copyProfessionalDraft, emptyResearchAttributes, normalizeResearchAttributes, validateReviewDraft } from '../../utils/review-validation.js'

const pause = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))
const activeStatuses = ['UPLOADED', 'EXTRACTING']

export const useCasesStore = defineStore('cases', {
  state: () => ({ cases: {}, nextCaseNumber: 1 }),
  actions: {
    getOwnedCase(caseId, now = Date.now()) {
      const entry = this.cases[caseId]
      const username = useAuthStore().demoUser?.username
      return entry && entry.owner === username && Date.parse(entry.expiresAt) > now
        && !['PURGING', 'DELETED'].includes(entry.processingStatus)
        ? entry : null
    },
    async createCase(displayedJob, scenario = 'normal') {
      const owner = useAuthStore().demoUser?.username
      if (!owner) throw new Error('Sign in before starting a demo simulation.')
      const currentJob = await loadJobDetail(displayedJob.jobId)
      if (!currentJob || currentJob.jobVersionId !== displayedJob.jobVersionId || scenario === 'unavailable') {
        throw new Error('This job version is no longer available. Return to Jobs and select a published job.')
      }
      if (scenario === 'error') throw new Error('Unable to create the demo case. Please try again.')
      if (owner !== useAuthStore().demoUser?.username) throw new Error('Your demo session has ended. Sign in again.')
      const caseId = `demo-case-${this.nextCaseNumber++}`
      this.cases[caseId] = {
        caseId, owner, jobId: currentJob.jobId, jobVersionId: currentJob.jobVersionId,
        jobTitle: currentJob.title, version: currentJob.version, processingStatus: 'AWAITING_UPLOAD',
        createdAt: new Date().toISOString(), uploadedAt: null,
        jobSummary: { jobId: currentJob.jobId, jobVersionId: currentJob.jobVersionId,
          title: currentJob.title, company: currentJob.company, location: currentJob.location,
          employmentType: currentJob.employmentType, version: currentJob.version },
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        file: null, preview: null, failure: '', stage: '', retryCount: 0, operation: 0,
        reviewDraft: null, researchAllowed: false, researchAttributes: emptyResearchAttributes(),
        reviewRevision: 0, confirmedProfessional: null, confirmedAt: null,
      }
      return caseId
    },
    async uploadCv(caseId, metadata, scenario = 'normal') {
      const entry = this.getOwnedCase(caseId)
      if (!entry || entry.confirmedAt || !['AWAITING_UPLOAD', 'FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.processingStatus)) return
      entry.file = { name: metadata.name, size: metadata.size, pageCount: metadata.pageCount }
      entry.uploadedAt = new Date().toISOString()
      entry.preview = null
      entry.reviewDraft = null
      entry.failure = ''
      entry.retryCount = 0
      entry.processingStatus = 'UPLOADED'
      entry.stage = 'Upload'
      await this.processCase(caseId, scenario)
    },
    async processCase(caseId, scenario) {
      const entry = this.getOwnedCase(caseId)
      if (!entry) return
      const operation = ++entry.operation
      const isCurrent = () => this.getOwnedCase(caseId) === entry && entry.operation === operation
      await pause(600)
      if (!isCurrent()) return
      if (scenario !== 'upload-error' || entry.retryCount > 0) {
        entry.processingStatus = 'EXTRACTING'
        entry.stage = 'Extraction'
        await pause(1600)
        if (!isCurrent()) return
      }
      if (scenario === 'final') {
        entry.processingStatus = 'FAILED_FINAL'
        entry.failure = 'The demo extraction could not process this document. Choose a different PDF.'
      } else if (['retryable', 'upload-error'].includes(scenario) && entry.retryCount === 0) {
        entry.processingStatus = 'FAILED_RETRYABLE'
        entry.failure = scenario === 'upload-error'
          ? 'The demo upload failed because of a temporary storage error. Retry or choose a different PDF.'
          : 'The demo extraction service is temporarily unavailable. Retry or choose a different PDF.'
      } else {
        entry.processingStatus = 'REVIEW_REQUIRED'
        entry.stage = 'Ready for review'
        // Fixed sample facts are unrelated to the selected PDF; no real extraction occurs.
        entry.preview = { education: 'Bachelor degree', experience: '2 years', skills: ['Java', 'Vue', 'SQL'] }
        entry.failure = ''
      }
    },
    async retryCase(caseId, scenario = 'normal') {
      const entry = this.getOwnedCase(caseId)
      if (!entry || entry.confirmedAt || entry.processingStatus !== 'FAILED_RETRYABLE' || entry.retryCount >= 1) return
      entry.retryCount += 1
      entry.failure = ''
      entry.processingStatus = entry.stage === 'Upload' ? 'UPLOADED' : 'EXTRACTING'
      await this.processCase(caseId, scenario)
    },
    async loadReview(caseId, scenario = 'normal', researchAllowed = false) {
      const entry = this.getOwnedCase(caseId)
      if (!entry || entry.processingStatus !== 'REVIEW_REQUIRED') {
        throw new Error('This demo case is not ready for fact review.')
      }
      await pause(350)
      if (this.getOwnedCase(caseId) !== entry || entry.processingStatus !== 'REVIEW_REQUIRED') {
        throw new Error('The demo case is no longer available for review.')
      }
      if (scenario === 'load-error') throw new Error('Unable to load the demo draft. Please try again.')
      if (!entry.reviewDraft) {
        entry.reviewDraft = createReviewDraft(scenario)
        entry.researchAllowed = researchAllowed
      }
      return copyProfessionalDraft(entry.reviewDraft)
    },
    async saveReview(caseId, draft, scenario = 'normal') {
      const entry = this.getOwnedCase(caseId)
      if (!entry || entry.processingStatus !== 'REVIEW_REQUIRED') throw new Error('This draft can no longer be edited.')
      const errors = validateReviewDraft(draft)
      if (Object.keys(errors).length) throw new Error('Correct the highlighted values before saving.')
      const revision = entry.reviewRevision
      const snapshot = copyProfessionalDraft(draft)
      await pause(500)
      if (this.getOwnedCase(caseId) !== entry || entry.processingStatus !== 'REVIEW_REQUIRED' || entry.reviewRevision !== revision) {
        throw new Error('The demo case changed while saving. Reload the review page.')
      }
      if (scenario === 'save-error') throw new Error('Unable to save the demo draft. Your changes are still on this page. Please try again.')
      entry.reviewDraft = snapshot
      entry.reviewRevision += 1
    },
    async saveResearch(caseId, attributes, candidateConfirmed, scenario = 'normal') {
      const entry = this.getOwnedCase(caseId)
      if (!entry || entry.processingStatus !== 'REVIEW_REQUIRED' || !entry.researchAllowed) {
        throw new Error('Optional research details are not available for this case.')
      }
      const normalized = normalizeResearchAttributes(attributes)
      if (Object.values(normalized).some((value) => value.length > 80)) throw new Error('Optional values must be 80 characters or fewer.')
      if (Object.values(normalized).some((value) => value !== 'UNKNOWN') && !candidateConfirmed) {
        throw new Error('Confirm the optional details you chose to provide before saving.')
      }
      await pause(500)
      if (this.getOwnedCase(caseId) !== entry || entry.processingStatus !== 'REVIEW_REQUIRED') throw new Error('This case can no longer be edited.')
      if (scenario === 'save-error') throw new Error('Unable to save optional demo details. Please try again.')
      entry.researchAttributes = normalized
    },
    async confirmReview(caseId, draft, reviewed, scenario = 'normal') {
      const entry = this.getOwnedCase(caseId)
      if (!entry) throw new Error('This demo case is no longer available.')
      if (entry.processingStatus === 'CONFIRMED') return
      if (entry.processingStatus !== 'REVIEW_REQUIRED') throw new Error('This case is not ready for confirmation.')
      if (!reviewed || Object.keys(validateReviewDraft(draft, true)).length) {
        throw new Error('Resolve the required facts and confirm that you reviewed them.')
      }
      const revision = entry.reviewRevision
      await pause(600)
      if (this.getOwnedCase(caseId) === entry && entry.processingStatus === 'CONFIRMED') return
      if (this.getOwnedCase(caseId) !== entry || entry.processingStatus !== 'REVIEW_REQUIRED' || entry.reviewRevision !== revision) {
        throw new Error('The case changed before confirmation. Reload the review page.')
      }
      if (scenario === 'confirm-error') throw new Error('Unable to confirm the demo facts. Nothing was confirmed. Please try again.')
      // Fixed mock scoring does not need to retain the confirmed professional block.
      entry.confirmedProfessional = null
      entry.processingStatus = 'CONFIRMED'
      entry.confirmedAt = new Date().toISOString()
      entry.expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString()
      entry.file = null
      entry.preview = null
      entry.reviewDraft = null
      entry.reviewRevision += 1
    },
    async runSimulation(caseId, scenario = 'normal', retry = false) {
      const entry = this.getOwnedCase(caseId)
      if (!entry?.confirmedAt) return
      const assessment = entry.assessment
      if (retry) {
        if (entry.processingStatus !== 'FAILED_RETRYABLE' || !assessment?.retryable || assessment.retryCount >= 1) return
        assessment.retryCount += 1
      } else {
        if (entry.processingStatus !== 'CONFIRMED') return
        entry.assessment = {
          methods: ['Objective', 'Neutral ML', 'Biased ML'].map((name) => ({ name, status: 'Waiting' })),
          events: [], retryCount: 0, retryable: false, updatedAt: '',
        }
      }
      const operation = ++entry.operation
      const isCurrent = () => this.getOwnedCase(caseId) === entry && entry.operation === operation
      const update = (stage, message) => {
        entry.stage = stage
        entry.assessment.updatedAt = new Date().toISOString()
        entry.assessment.events.push({ message, time: entry.assessment.updatedAt })
      }
      entry.processingStatus = 'AUTOMATED_RUNNING'
      entry.failure = ''
      entry.assessment.retryable = false
      update('Preparing assessment', retry ? 'Demo retry accepted; completed methods retained.' : 'Demo run accepted; confirmed facts and job version retained.')
      // These delays animate fixed mock states; they do not estimate real processing time.
      for (const method of entry.assessment.methods) {
        if (method.status === 'Complete') continue
        if (!isCurrent()) return
        method.status = 'Running'
        update(method.name, `${method.name} demo stage started.`)
        await pause(1200)
        if (!isCurrent()) return
        if (method.name === 'Neutral ML' && (scenario === 'final' || (scenario === 'retryable' && !retry))) {
          method.status = 'Failed'
          entry.assessment.retryable = scenario === 'retryable'
          entry.processingStatus = scenario === 'retryable' ? 'FAILED_RETRYABLE' : 'FAILED_FINAL'
          entry.failure = scenario === 'retryable'
            ? 'The mock Neutral ML stage is temporarily unavailable. Retry resumes unfinished methods.'
            : 'The mock Neutral ML stage could not complete. Retry is unavailable for this failure.'
          update(method.name, `${method.name} demo stage failed; no score or decision was produced.`)
          return
        }
        method.status = 'Complete'
        update(method.name, `${method.name} demo stage completed.`)
      }
      if (!isCurrent()) return
      entry.results = createSimulationResults(entry)
      entry.processingStatus = 'AUTOMATED_COMPLETE'
      entry.expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString()
      update('Complete', 'All three automated demo stages completed. Human review is pending.')
    },
    async loadResults(caseId, scenario = 'normal', refreshHuman = false) {
      const entry = this.getOwnedCase(caseId)
      const ready = () => this.getOwnedCase(caseId) === entry
        && entry?.processingStatus === 'AUTOMATED_COMPLETE'
        && entry.assessment?.methods.length === 3
        && entry.assessment.methods.every((method) => method.status === 'Complete')
      if (!ready()) throw new Error('This demo case is not ready for results.')
      await pause(350)
      if (!ready()) throw new Error('The demo case is no longer available.')
      if (scenario === 'load-error' || (refreshHuman && scenario === 'refresh-error')) {
        throw new Error(refreshHuman ? 'Unable to refresh human results. Previously loaded results are unchanged.' : 'Unable to load demo results. Please try again.')
      }
      if (!entry.results) throw new Error('The automated demo results are incomplete.')
      if (!entry.results.humanInitialized) {
        if (scenario === 'unassigned') {
          entry.results.humans.standard = { status: 'Not assigned', decision: null, reason: null }
          entry.results.humans.instructedBias = { status: 'Not assigned', decision: null, reason: null }
        } else if (scenario === 'submitted') {
          entry.results.humans.standard = submittedReview('standard')
          entry.results.humans.instructedBias = submittedReview('instructedBias')
        }
        entry.results.humanInitialized = true
      }
      if (refreshHuman && scenario === 'human-update') entry.results.humans.standard = submittedReview('standard')
      return selectCandidateResults(entry)
    },
    listOwnedCases(now = Date.now()) {
      const entries = Object.values(this.cases).filter((entry) => this.getOwnedCase(entry.caseId, now))
      return sortCaseSummaries(entries.map(selectCaseSummary))
    },
    async loadHistory(scenario = 'normal') {
      const owner = useAuthStore().demoUser?.username
      if (!owner) throw new Error('Sign in to view your demo cases.')
      await pause(350)
      if (owner !== useAuthStore().demoUser?.username) throw new Error('Your demo session has ended.')
      if (scenario === 'error') throw new Error('Unable to load your demo simulations. Please try again.')
      return this.listOwnedCases()
    },
    async deleteSimulation(caseId, scenario = 'normal') {
      const entry = this.getOwnedCase(caseId)
      if (!entry) throw new Error('This demo case is no longer available.')
      if (scenario === 'delete-error') {
        await pause(450)
        throw new Error('Unable to delete the demo case. Nothing was deleted. Please try again.')
      }
      entry.processingStatus = 'PURGING'
      await pause(450)
      if (this.cases[caseId] === entry) delete this.cases[caseId]
    },
    deleteCase(caseId) {
      if (this.getOwnedCase(caseId)) delete this.cases[caseId]
    },
    clearCases() {
      // Remove references so pending demo work cannot restore data after sign out.
      this.cases = {}
    },
  },
})

export function isCaseProcessing(entry) {
  return Boolean(entry && activeStatuses.includes(entry.processingStatus))
}
