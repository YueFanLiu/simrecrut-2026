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
      if (!demoMode) {
        const roles = this.demoUser?.roles || []
        return roles.includes('admin') || roles.some(value => String(value).toLowerCase() === role.toLowerCase())
      }
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
      const labels = { admin: 'Admin', hr: 'HR', researcher: 'Researcher', candidate: 'Candidate' }
      const role = user.roles.map(value => labels[String(value).toLowerCase()]).find(Boolean) || 'Connected'
      const identity = { username: user.username, displayName: user.name, role, roles: user.roles }
      if (JSON.stringify(this.demoUser) !== JSON.stringify(identity)) this.demoUser = identity
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
        try { await useUserStore().LogOut() } finally {
          useCasesStore().clearCases(); useHrJobsStore().clearJobs(); useHrCasesStore().clearCases()
          useResearchModuleStore().clearResearch(); useReviewTasksStore().clearTasks()
          useAdminUsersStore().clearUsers(); useAdminRolesStore().clearRoles(); useAdminModelsStore().clearModels()
          this.demoUser = null
        }
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
