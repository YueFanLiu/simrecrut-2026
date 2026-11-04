// Connected mode uses server data; fixtures remain limited to explicit demos.
import { demoMode } from '../../config/runtime.js'
import { defineStore } from 'pinia'
import { shareDemoModels } from '../../mock/shared-demo.js'
import { recordDemoAudit } from '../../mock/admin-audit.js'
import { requireAdmin } from '../../mock/admin-users.js'
import { checkModelPackage, checkRegistry, createAdminModels, modelProjection, offlinePackages, projectModels } from '../../mock/admin-models.js'
const wait = () => new Promise(resolve => setTimeout(resolve, 500))
export const useAdminModelsStore = defineStore('adminModels', {
  state: () => ({ ...(demoMode ? createAdminModels() : { models: [], slots: {} }), packages: demoMode ? offlinePackages() : [] }),
  actions: {
    listModels(user) {
      if (!demoMode) { return this.models } return projectModels(this.models, user) },
    readModel(id, user) { const row = this.listModels(user).find(row => row.id === id); if (!row) throw new Error('This model version is unavailable.'); return row },
    async loadModels(user, scenario = 'normal') {
      if (!demoMode) {
        this.models = await (await import('../../api/administration.js')).models()
        this.slots = Object.fromEntries(this.models.filter(row => row.status === 'ACTIVE').map(row => [row.type, row.id]))
        return this.models
      }
      requireAdmin(user); await wait()
      if (scenario === 'load-error') throw new Error('Unable to load model versions. Please try again.')
      return scenario === 'empty' ? [] : this.listModels(user)
    },
    async loadPackages() {
      if (!demoMode) this.packages = await (await import('../../api/administration.js')).modelPackages()
      return this.packages
    },
    async registerPackage(id, user, scenario = 'normal') {
      if (!demoMode) { await (await import('../../api/administration.js')).registerModel(id); await this.loadModels(user); return }
      requireAdmin(user); const rows = this.models, source = offlinePackages().find(row => row.id === id)
      const check = () => {
        requireAdmin(user)
        if (this.models !== rows) throw new Error('The demo session changed. Please reopen Model Versions.')
        if (!source) throw new Error('Select a predefined offline demo package.')
        if (this.models.some(row => row.id === source.id || row.type === source.type && row.version === source.version)) throw new Error('This package version is already registered.')
        const failure = checkModelPackage(source); if (failure) throw new Error(failure)
      }
      check(); await wait(); check()
      if (scenario === 'register-error') throw new Error('Unable to register package metadata. Please try again.')
      this.models.unshift({ ...source, evidence: { ...source.evidence }, createdAt: new Date().toISOString(), createdBy: user.username })
      shareDemoModels(this.models)
    },
    async release(id, revision, action, replacementId, user, scenario = 'normal') {
      if (!demoMode) { const result = await (await import('../../api/administration.js')).releaseModel(id, revision, action, replacementId); await this.loadModels(user); return result }
      requireAdmin(user); const rows = this.models
      const initial = this.models.find(row => row.id === id), originalSlot = initial && this.slots[initial.type]
      const check = () => {
        requireAdmin(user); checkRegistry(this.models, this.slots)
        const row = this.models.find(row => row.id === id)
        if (this.models !== rows || !row || row.revision !== revision || this.slots[row.type] !== originalSlot) throw new Error('Model release state changed. Reopen this version before continuing.')
        if (action === 'approve') {
          if (row.status !== 'TESTED') throw new Error('Only a TESTED package can be approved.')
          const failure = checkModelPackage(row); if (failure) throw new Error(failure)
          return { row }
        }
        if (!['activate', 'retire'].includes(action)) throw new Error('Select a supported model release action.')
        if (action === 'activate' && row.status !== 'APPROVED') throw new Error('Only an APPROVED package can be activated.')
        if (action === 'retire' && row.status !== 'ACTIVE') throw new Error('Only the current ACTIVE version can be retired with a replacement.')
        const next = action === 'activate' ? row : this.models.find(item => item.id === replacementId)
        if (!next || next.id === row.id && action === 'retire' || next.type !== row.type || next.status !== 'APPROVED') throw new Error('Select a compatible APPROVED replacement of the same model type.')
        const failure = checkModelPackage(next); if (failure) throw new Error(failure)
        if (!next.demoReady) throw new Error('The synthetic inference readiness check failed. The active slot has not changed.')
        return { row, next, previous: this.models.find(item => item.id === originalSlot) }
      }
      check(); await wait(); const { row, next, previous } = check()
      if (scenario === 'release-error') throw new Error('Unable to complete the model release. All versions and active slots have been kept unchanged.')
      const now = new Date().toISOString()
      if (action === 'approve') Object.assign(row, { status: 'APPROVED', approvedAt: now, revision: row.revision + 1 })
      else {
        Object.assign(previous, { status: 'RETIRED', retiredAt: now, revision: previous.revision + 1 })
        Object.assign(next, { status: 'ACTIVE', activatedAt: now, revision: next.revision + 1 })
        this.slots[row.type] = next.id
      }
      shareDemoModels(this.models)
      recordDemoAudit(action === 'approve' ? 'MODEL_APPROVED' : 'MODEL_ACTIVATED', 'Model Version',
        action === 'retire' ? next.id : row.id, user.username)
      return modelProjection(row)
    },
    clearModels() { this.$reset() },
  },
})
