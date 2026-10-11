import { defineStore } from 'pinia'
import { publishDemoJob } from '../../mock/shared-demo.js'
import { recordDemoAudit } from '../../mock/admin-audit.js'
import { createManagedJobs } from '../../mock/hr-jobs.js'
import { createJobForm, criteria, validateDescription, validateJob, weightUnits } from '../../mock/hr-job-wizard.js'
import { createPublishedHistory, nextDraftVersion, publishedJobForm } from '../../mock/hr-job-versions.js'

const copy = (value) => JSON.parse(JSON.stringify(value))

export const useHrJobsStore = defineStore('hrJobs', {
  state: () => ({ jobs: createManagedJobs(), drafts: {}, published: createPublishedHistory(createManagedJobs()), nextId: 1 }),
  actions: {
    ownedJob(user, jobId) {
      if (user?.role !== 'HR') throw new Error('An HR demo session is required.')
      const job = this.jobs.find((item) => item.jobId === jobId && item.owner === user.username)
      if (!job) throw new Error('This job is unavailable in your HR workspace.')
      return job
    },
    readDraft(user, jobId) {
      const job = this.ownedJob(user, jobId)
      if (!job.draftVersion) throw new Error('Published versions are read-only. No editable draft is available.')
      if (!this.drafts[jobId]) this.drafts[jobId] = { revision: 1, form: createJobForm(job) }
      return copy(this.drafts[jobId])
    },
    createDraft(user, form) {
      if (user?.role !== 'HR') throw new Error('An HR demo session is required.')
      const errors = validateDescription(form)
      if (errors.length) throw new Error(errors[0])
      const jobId = `demo-job-${this.nextId++}`
      this.jobs.push({ jobId, owner: user.username, title: form.title.trim(), department: form.department.trim(),
        summary: form.description.trim(), createdAt: new Date().toISOString().slice(0, 10),
        status: 'Draft', draftVersion: '0.1', publishedVersion: null })
      this.drafts[jobId] = { revision: 1, form: copy(form), createdAt: new Date().toISOString() }
      return jobId
    },
    saveDraft(user, jobId, form, revision) {
      const draft = this.readDraft(user, jobId)
      if (draft.revision !== revision) throw new Error('This draft changed elsewhere. Reload the saved draft before trying again. Your unsaved inputs have been kept.')
      const job = this.ownedJob(user, jobId)
      this.drafts[jobId] = { revision: revision + 1, form: copy(form),
        createdAt: draft.createdAt || null, sourceVersionId: draft.sourceVersionId || null }
      Object.assign(job, { title: form.title.trim(), department: form.department.trim(), summary: form.description.trim() })
      return revision + 1
    },
    publishDraft(user, jobId, revision) {
      const draft = this.readDraft(user, jobId)
      if (draft.revision !== revision) throw new Error('This draft changed elsewhere. Reload and validate the saved draft again.')
      const report = validateJob(draft.form)
      if (!report.valid) throw new Error(report.errors.join(' '))
      const job = this.ownedJob(user, jobId)
      const snapshot = { ...copy(draft.form), version: job.draftVersion, revision,
        versionId: `${jobId}-v${job.draftVersion}`, createdAt: new Date().toISOString(),
        createdBy: user.displayName || 'Demo HR User', changeSummary: 'Published the reviewed draft configuration.',
        weights: criteria.map((category, index) => ({ category, value: weightUnits(draft.form.percentages[index]) / 10000000 })) }
      this.published[jobId] = [...(this.published[jobId] || []), snapshot]
      job.publishedVersion = job.draftVersion
      job.draftVersion = null
      job.status = 'Published'
      delete this.drafts[jobId]
      publishDemoJob(job, snapshot)
      recordDemoAudit('JOB_PUBLISHED', 'Job Version', snapshot.versionId, user.username)
      return copy(snapshot)
    },
    readVersions(user, jobId) {
      const job = this.ownedJob(user, jobId)
      const versions = (this.published[jobId] || []).map((entry) => ({
        versionId: entry.versionId, version: entry.version, status: 'Published',
        createdAt: entry.createdAt, createdBy: entry.createdBy, changeSummary: entry.changeSummary,
        current: entry.version === job.publishedVersion,
      })).reverse()
      if (job.draftVersion) versions.unshift({ versionId: `${jobId}-draft`, version: job.draftVersion,
        status: 'Draft', createdAt: this.drafts[jobId]?.createdAt || null, createdBy: user.displayName || 'Demo HR User',
        changeSummary: 'Current editable draft. Publish through the job wizard.', current: false })
      return { jobId, title: job.title, department: job.department, publishedVersion: job.publishedVersion,
        draftVersion: job.draftVersion, versions: copy(versions) }
    },
    readVersion(user, jobId, versionId) {
      const job = this.ownedJob(user, jobId)
      if (versionId === `${jobId}-draft` && job.draftVersion) {
        const draft = this.readDraft(user, jobId)
        return { ...draft.form, revision: draft.revision, versionId, version: job.draftVersion, status: 'Draft',
          createdAt: draft.createdAt || null, createdBy: user.displayName || 'Demo HR User', changeSummary: 'Current editable draft.',
          weights: criteria.map((category, index) => ({ category,
            value: weightUnits(draft.form.percentages[index]) === null ? null : weightUnits(draft.form.percentages[index]) / 10000000 })) }
      }
      const version = (this.published[jobId] || []).find((entry) => entry.versionId === versionId)
      if (!version) throw new Error('This version is unavailable for the selected job.')
      return { ...copy(version), status: 'Published' }
    },
    cloneVersion(user, jobId, versionId) {
      const job = this.ownedJob(user, jobId)
      if (job.draftVersion) {
        const error = new Error(`An editable draft (${job.draftVersion}) already exists. Open it instead of creating another draft.`)
        error.code = 'DRAFT_EXISTS'
        throw error
      }
      const source = this.readVersion(user, jobId, versionId)
      if (source.status !== 'Published') throw new Error('Choose a published version as the source of a new draft.')
      const form = publishedJobForm(source)
      job.draftVersion = nextDraftVersion(this.published[jobId] || [])
      this.drafts[jobId] = { revision: 1, form, sourceVersionId: versionId, createdAt: new Date().toISOString() }
      return { jobId, version: job.draftVersion, revision: 1 }
    },
    clearJobs() { this.$reset() },
  },
})
