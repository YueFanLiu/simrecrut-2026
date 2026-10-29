// Connected mode uses server data; fixtures remain limited to explicit demos.
import { demoMode } from '../../config/runtime.js'
import { defineStore } from 'pinia'
import { createResearchResults } from '../../mock/research-results.js'
import { readSharedDemoModels } from '../../mock/shared-demo.js'
import { createResearchModule, eligibleResearchSubject, hasResearchAccess, researchFactors,
  researchExperimentProjection, researchSubjectProjection, validateResearchConfiguration } from '../../mock/research-module.js'

export const useResearchModuleStore = defineStore('researchModule', {
  state: () => demoMode ? createResearchModule() : { experiments: [], subjects: [], models: [], jobs: [] },
  getters: { availableModels: state => demoMode ? readSharedDemoModels() || state.models : state.models },
  actions: {
    listExperiments(user, now = Date.now()) {
      if (!demoMode) { return this.experiments }
      const eligible = new Set(this.listSubjects(user, now).map(row => row.id))
      return this.experiments.filter(row => hasResearchAccess(row, user, now)).map(row => {
        const pairs = row.pairs.filter(pair => eligible.has(pair.subjectId))
        const targets = new Set([...pairs.flatMap(pair => [`${pair.id}-baseline`, `${pair.id}-variant`]), ...row.subjectIds.filter(id => eligible.has(id))])
        return researchExperimentProjection({ ...row, subjectIds: row.subjectIds.filter(id => eligible.has(id)), pairs,
          reviews: row.reviews.filter(review => targets.has(review.target)),
          outputs: row.outputs?.filter(output => eligible.has(output.subjectId)) })
      })
    },
    listSubjects(user, now = Date.now()) {
      if (!demoMode) { return this.subjects } return this.subjects.filter(row => eligibleResearchSubject(row, user, now)).map(researchSubjectProjection) },
    readExperiment(id, user) {
      const row = this.experiments.find(row => row.id === id)
      if (!row || (demoMode && !hasResearchAccess(row, user))) throw new Error('This experiment is unavailable or access is no longer authorised.')
      return row
    },
    createExperiment(form, user) {
      if (!demoMode) { return import('../../api/research.js').then(api => api.createExperiment(form)).then(row => row.id) }
      if (user?.role !== 'Researcher') throw new Error('A Researcher demo session is required.')
      const error = validateResearchConfiguration(form, this.availableModels)
      if (error) throw new Error(error)
      const id = `EXP-${String(Math.max(0, ...this.experiments.map(row => Number(row.id.slice(4)) || 0)) + 1).padStart(3, '0')}`
      const row = { id, name: form.name.trim(), type: form.type, job: form.job, weight: form.weight, protocol: form.protocol,
        neutral: form.neutral, biased: form.biased, owner: user.username, grants: [], subjects: 0, status: 'Draft',
        date: new Date().toISOString().slice(0, 10), subjectIds: [], factor: 'Gender', pairs: [], reviews: [], execution: 'Not started' }
      this.experiments.unshift(row)
      return id
    },
    draft(id, user) { const row = this.readExperiment(id, user); if (row.status !== 'Draft') throw new Error('Only a draft experiment can be changed.'); return row },
    saveSubjects(id, ids, user) {
      if (!demoMode) { return import('../../api/research.js').then(api => api.experimentAction(id, 'subjects', { subjectIds: ids })) }
      const row = this.draft(id, user), unique = [...new Set(ids)]
      if (!unique.length || unique.some(id => !this.listSubjects(user).some(subject => subject.id === id))) throw new Error('Select currently eligible and authorised subjects.')
      row.subjectIds = unique; row.subjects = unique.length; row.pairs = []; row.reviews = []
    },
    createVariants(id, factor, user) {
      if (!demoMode) { return import('../../api/research.js').then(api => api.experimentAction(id, 'variants', { factor })) }
      const row = this.draft(id, user)
      if (row.type !== 'Single-factor paired' || !researchFactors.includes(factor)) throw new Error('Select one supported controlled attribute for a paired experiment.')
      if (!row.subjectIds.length || row.subjectIds.some(id => !this.listSubjects(user).some(subject => subject.id === id))) throw new Error('Save eligible subjects before creating pairs.')
      row.factor = factor
      row.pairs = row.subjectIds.map(id => {
        const subject = this.listSubjects(user).find(subject => subject.id === id)
        return { id: `${row.id}-${id}-pair`, subjectId: id, factor, baseline: 'Category A', variant: 'Category B',
          professional: structuredClone(subject.professional), variantProfessional: structuredClone(subject.professional) }
      })
      row.reviews = []
    },
    async runExperiment(id, user) {
      if (!demoMode) { return (await import('../../api/research.js')).experimentAction(id, 'run') }
      const row = this.draft(id, user), failure = validateResearchConfiguration(row, this.availableModels)
      if (failure) throw new Error(failure)
      const validate = () => {
        this.readExperiment(id, user)
        if (!row.subjectIds.length || row.subjectIds.some(id => !this.listSubjects(user).some(subject => subject.id === id))) throw new Error('Subject access expired or selection is missing. Review the subject selection.')
        if (row.type === 'Single-factor paired' && (row.pairs.length !== row.subjectIds.length || row.pairs.some(pair =>
          !row.subjectIds.includes(pair.subjectId) || pair.factor !== row.factor || JSON.stringify(pair.professional) !== JSON.stringify(pair.variantProfessional)))) throw new Error('Create valid single-factor pairs with identical professional facts before running.')
      }
      validate(); row.status = 'Running'; row.execution = 'Validating the local demonstration configuration...'
      await new Promise(resolve => setTimeout(resolve, 600))
      if (!this.experiments.includes(row)) return
      try { validate(); row.outputs = createResearchResults(row); row.status = 'Completed'; row.execution = 'Synthetic demo completed. Outputs and metrics are illustrative fixtures, not model evaluations.' }
      catch (failure) { row.status = 'Failed'; row.execution = failure.message }
    },
    assignReviews(id, reviewer, condition, user) {
      if (!demoMode) { return import('../../api/research.js').then(api => api.experimentAction(id, 'reviews', { reviewer, condition })) }
      const row = this.readExperiment(id, user)
      if (!['reviewer-hr', 'reviewer-researcher'].includes(reviewer) || !['Standard', 'Instructed-Bias'].includes(condition)) throw new Error('Select an eligible demo reviewer and review condition.')
      const targets = row.type === 'Single-factor paired' ? row.pairs.flatMap(pair => [`${pair.id}-baseline`, `${pair.id}-variant`]) : row.subjectIds
      if (!targets.length || row.subjectIds.some(id => !this.listSubjects(user).some(subject => subject.id === id))) throw new Error('Save authorised subjects and create variants before assigning reviews.')
      for (const target of targets) if (!row.reviews.some(review => review.target === target && review.condition === condition)) row.reviews.push({ id: `${row.id}-REV-${row.reviews.length + 1}`, target, condition, reviewer, purpose: 'Research', status: 'Pending',
        subjectId: row.type === 'Single-factor paired' ? row.pairs.find(pair => target.startsWith(pair.id + '-')).subjectId : target,
        assignedTo: reviewer === 'reviewer-hr' ? 'hr' : 'researcher', assignedAt: new Date().toISOString(),
        access: { allowed: true, expiresAt: null } })
    },
    assignPrimaryReview(subjectId, reviewer, condition, user) {
      if (!demoMode) { return import('../../api/research.js').then(api => api.primaryReview(subjectId, { reviewer, condition })) }
      const row = this.subjects.find(row => row.id === subjectId)
      if (!row || row.source !== 'Authorized Candidate Cases' || !eligibleResearchSubject(row, user)) throw new Error('An authorised retained Candidate case is required for a Primary review.')
      if (!['reviewer-hr', 'reviewer-researcher'].includes(reviewer) || !['Standard', 'Instructed-Bias'].includes(condition)) throw new Error('Select an eligible demo reviewer and review condition.')
      row.primaryReviews ||= []
      if (!row.primaryReviews.some(review => review.condition === condition)) row.primaryReviews.push({ condition, reviewer, purpose: 'Primary', status: 'Pending', assignedTo: reviewer === 'reviewer-hr' ? 'hr' : 'researcher',
        assignedAt: new Date().toISOString(), access: { allowed: true, expiresAt: null } })
      return row.primaryReviews.length
    },
    async loadResearch(resources = ['experiments', 'subjects', 'models', 'jobs']) {
      const api = await import('../../api/research.js')
      for (const key of resources) this[key] = []
      const rows = await Promise.all(resources.map(key => api[key]()))
      resources.forEach((key, index) => { this[key] = rows[index] })
    },
    clearResearch() { this.$reset() },
  },
})
