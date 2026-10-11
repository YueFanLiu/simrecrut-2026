import { defineStore } from 'pinia'
import { recordDemoAudit } from '../../mock/admin-audit.js'
import { resolveReviewTask, validateReviewSubmission } from '../../mock/review-task-detail.js'
import { createReviewTasks, researchAssignmentTasks, selectReviewTaskSummaries } from '../../mock/review-tasks.js'

export const useReviewTasksStore = defineStore('reviewTasks', {
  state: () => ({ tasks: createReviewTasks() }),
  actions: {
    listTasks(user, research, now = Date.now()) { return selectReviewTaskSummaries([...this.tasks, ...researchAssignmentTasks(research)], user, now) },
    async loadTasks(user, research, scenario = 'normal') {
      if (!['HR', 'Researcher'].includes(user?.role)) throw new Error('An HR or Researcher demo session is required to view assigned review tasks.')
      await new Promise(resolve => setTimeout(resolve, 400))
      if (scenario === 'error') throw new Error('Unable to load review tasks. Please try again.')
      return scenario === 'empty' ? [] : this.listTasks(user, research)
    },
    readDetail(id, user, research, now = Date.now()) { return resolveReviewTask(this.tasks, research, id, user, now).detail },
    async loadDetail(id, user, research, scenario = 'normal') {
      await new Promise(resolve => setTimeout(resolve, 400))
      if (scenario === 'load-error') throw new Error('Unable to load this review task. Please try again.')
      return this.readDetail(id, user, research)
    },
    async submitReview(id, form, user, research, scenario = 'normal') {
      const failure = validateReviewSubmission(form)
      if (failure) throw new Error(failure)
      const payload = { decision: form.decision, reason: form.reason.trim() }
      const initial = resolveReviewTask(this.tasks, research, id, user)
      if (initial.record.status !== 'Pending') throw new Error('This review has already been submitted and cannot be changed.')
      if (initial.detail.controlled?.instructionAvailable === false) throw new Error('Controlled instructions are unavailable. Please contact the assigner.')
      await new Promise(resolve => setTimeout(resolve, 600))
      const current = resolveReviewTask(this.tasks, research, id, user)
      if (current.record !== initial.record) throw new Error('The task changed while submitting. Please reopen it.')
      if (current.record.status !== 'Pending') throw new Error('This review has already been submitted and cannot be changed.')
      if (scenario === 'submit-error') throw new Error('Unable to submit this review. Your input has been kept. Please try again.')
      Object.assign(current.record, payload, { status: 'Submitted', submittedAt: new Date().toISOString() })
      recordDemoAudit('REVIEW_SUBMITTED', 'Review Task', id, user.username)
      return this.readDetail(id, user, research)
    },
    clearTasks() { this.$reset() },
  },
})
