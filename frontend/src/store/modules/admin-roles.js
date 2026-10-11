// Connected mode uses server data; fixtures remain limited to explicit demos.
import { demoMode } from '../../config/runtime.js'
import { defineStore } from 'pinia'
import { requireAdmin } from '../../mock/admin-users.js'
import { createAdminRoles, roleProjection, validateRoleConfiguration } from '../../mock/admin-roles.js'
import { useAdminUsersStore } from './admin-users.js'
export const useAdminRolesStore = defineStore('adminRoles', {
  state: () => ({ roles: demoMode ? createAdminRoles() : [] }),
  actions: {
    listRoles(user) {
      if (!demoMode) { return this.roles } requireAdmin(user); const accounts = useAdminUsersStore().listUsers(user); return this.roles.map(row => roleProjection(row, accounts)) },
    readRole(name, user) {
      const row = this.listRoles(user).find(role => role.name === name)
      if (!row) throw new Error('This role is no longer available.')
      return row
    },
    async loadRoles(user, scenario = 'normal') {
      if (!demoMode) { this.roles = await (await import('../../api/administration.js')).roles(); return this.roles }
      requireAdmin(user); await new Promise(resolve => setTimeout(resolve, 400))
      if (scenario === 'load-error') throw new Error('Unable to load roles. Please try again.')
      return scenario === 'empty' ? [] : this.listRoles(user)
    },
    async savePermissions(form, user, scenario = 'normal') {
      if (!demoMode) { await (await import('../../api/administration.js')).rolePermissions(form); await this.loadRoles(user); return }
      requireAdmin(user)
      const snapshot = { ...form, permissions: Array.isArray(form.permissions) ? [...form.permissions] : form.permissions }, rows = this.roles
      const check = () => {
        requireAdmin(user)
        const row = this.roles.find(role => role.name === snapshot.name)
        if (this.roles !== rows || !row || row.revision !== snapshot.revision) throw new Error('This role changed. Reopen it before saving.')
        const failure = validateRoleConfiguration(snapshot)
        if (failure) throw new Error(failure)
        return row
      }
      check(); await new Promise(resolve => setTimeout(resolve, 400)); const row = check()
      if (scenario === 'save-error') throw new Error('Unable to save permissions. Your selection has been kept. Please try again.')
      Object.assign(row, { description: snapshot.description.trim(), permissions: [...snapshot.permissions], revision: row.revision + 1 })
    },
    async assignRole(accountId, expectedRevision, role, user, scenario = 'normal') {
      if (!demoMode) {
        await (await import('../../api/administration.js')).assignRole(accountId, expectedRevision, role)
        await Promise.all([this.loadRoles(user), useAdminUsersStore().loadUsers(user)])
        return
      }
      requireAdmin(user); this.readRole(role, user)
      const users = useAdminUsersStore(), account = users.readUser(accountId, user)
      if (account.revision !== expectedRevision) throw new Error('This account changed. Reopen role assignment before saving.')
      await users.saveUser({ ...account, role }, user, scenario === 'assign-error' ? 'save-error' : 'normal')
    },
    clearRoles() { this.$reset() },
  },
})
