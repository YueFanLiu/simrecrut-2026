import { useAdminModelsStore } from './admin-models.js'
import { defineStore } from 'pinia'
import { useAdminRolesStore } from './admin-roles.js'
import { useAdminUsersStore } from './admin-users.js'
import { loginDemo } from '../../mock/auth.js'
import { readDemoSession, saveDemoSession } from '../../utils/demo-session.js'
import { useCasesStore } from './cases.js'
import { useHrJobsStore } from './hr-jobs.js'
import { useHrCasesStore } from './hr-cases.js'


import { useResearchModuleStore } from './research-module.js'

import { useReviewTasksStore } from './review-tasks.js'

export const useAuthStore = defineStore('auth', {
  state: () => ({ demoUser: readDemoSession() }),
  getters: {
    isDemoSignedIn: (state) => Boolean(state.demoUser),
  },
  actions: {
    hasDemoRole(role) {
      return this.demoUser?.role === role
    },
    async login(credentials) {
      this.demoUser = await loginDemo(credentials)
      saveDemoSession(this.demoUser)
    },
    logout() {
      useCasesStore().clearCases()
      useHrJobsStore().clearJobs()
      useHrCasesStore().clearCases()
      useResearchModuleStore().clearResearch()
      useReviewTasksStore().clearTasks()
      useAdminUsersStore().clearUsers()
      useAdminRolesStore().clearRoles()
      useAdminModelsStore().clearModels()
      this.demoUser = null
      saveDemoSession(null)
    },
  },
})
