import { defineStore } from 'pinia'
import { createHrCases, selectHrCaseSummaries } from '../../mock/hr-cases.js'
import { selectHrCaseDetail } from '../../mock/hr-case-detail.js'

export const useHrCasesStore = defineStore('hrCases', {
  state: () => ({ cases: createHrCases() }),
  actions: {
    listCases(user, jobs, now = Date.now()) { return selectHrCaseSummaries(this.cases, user, jobs, now) },
    readDetail(user, jobs, caseId, scenario = 'normal', now = Date.now()) {
      const summary = this.listCases(user, jobs, now).find((entry) => entry.caseId === caseId)
      if (!summary) throw new Error('This case is unavailable or access is no longer authorised.')
      return selectHrCaseDetail(this.cases.find((entry) => entry.caseId === caseId), summary, scenario)
    },
    async loadCases(user, jobs, scenario = 'normal') {
      if (user?.role !== 'HR') throw new Error('An HR demo session is required to view authorised cases.')
      await new Promise((resolve) => setTimeout(resolve, 400))
      if (scenario === 'error') throw new Error('Unable to load authorised cases. Please try again.')
      return scenario === 'empty' ? [] : this.listCases(user, jobs)
    },
    clearCases() { this.$reset() },
  },
})
