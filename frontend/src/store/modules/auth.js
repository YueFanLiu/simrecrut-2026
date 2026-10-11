import { useAdminModelsStore } from './admin-models.js'
import { defineStore } from 'pinia'
import { useAdminRolesStore } from './admin-roles.js'
import { useAdminUsersStore } from './admin-users.js'
import { demoMode } from '../../config/runtime.js'
import { loginDemo } from '../../mock/auth.js'
import { readDemoSession, saveDemoSession } from '../../utils/demo-session.js'
import { useCasesStore } from './cases.js'
import { useHrJobsStore } from './hr-jobs.js'
import { useHrCasesStore } from './hr-cases.js'


import { useResearchModuleStore } from './research-module.js'

import { useReviewTasksStore } from './review-tasks.js'

export const useAuthStore = defineStore('auth', {
  state: () => ({ demoUser: demoMode ? readDemoSession() : null }),
  getters: {
    isDemoSignedIn: (state) => Boolean(state.demoUser),
  },
  actions: {
    hasDemoRole(role) {
      return this.demoUser?.role === role
    },
    // Connected identity comes from RuoYi; demo sessions never authorize API writes.
    async restoreConnectedSession() {
      if (demoMode) return
      const { getToken } = await import('../../utils/auth.js')
      if (!getToken()) { this.demoUser = null; return }
      const { default: useUserStore } = await import('./user.js')
      const user = useUserStore()
      if (!user.loaded) await user.GetInfo()
      this.demoUser = { username: user.name, displayName: user.name, role: 'Connected', roles: user.roles }
    },
    async login(credentials) {
      if (!demoMode) {
        const { default: useUserStore } = await import('./user.js')
        await useUserStore().Login({ username: credentials.identifier, password: credentials.password, code: credentials.code, uuid: credentials.uuid })
        await this.restoreConnectedSession()
        return
      }
      this.demoUser = await loginDemo(credentials)
      saveDemoSession(this.demoUser)
    },
    async logout() {
      if (!demoMode) {
        const { default: useUserStore } = await import('./user.js')
        try { await useUserStore().LogOut() } finally { this.demoUser = null }
        return
      }
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
