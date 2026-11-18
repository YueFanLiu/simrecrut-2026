import { defineStore } from 'pinia'
import { createResearchExperiments, selectResearchExperiments } from '../../mock/research-experiments.js'

export const useResearchExperimentsStore = defineStore('researchExperiments', {
  state: () => ({ experiments: createResearchExperiments() }),
  actions: {
    listExperiments(user, now = Date.now()) { return selectResearchExperiments(this.experiments, user, now) },
    async loadExperiments(user, scenario = 'normal') {
      if (user?.role !== 'Researcher') throw new Error('A Researcher demo session is required to view experiments.')
      await new Promise((resolve) => setTimeout(resolve, 400))
      if (scenario === 'error') throw new Error('Unable to load experiments. Please try again.')
      return scenario === 'empty' ? [] : this.listExperiments(user)
    },
    clearExperiments() { this.$reset() },
  },
})
