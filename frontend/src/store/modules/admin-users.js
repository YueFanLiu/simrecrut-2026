import { defineStore } from 'pinia'
import { accountProjection, createAdminUsers, requireAdmin, validateAccount } from '../../mock/admin-users.js'
const wait = () => new Promise(resolve => setTimeout(resolve, 400))
export const useAdminUsersStore = defineStore('adminUsers', {
  state: () => ({ users: createAdminUsers() }),
  actions: {
    listUsers(user) { requireAdmin(user); return this.users.map(accountProjection) },
    readUser(id, user) {
      requireAdmin(user)
      const row = this.users.find(row => row.id === id)
      if (!row) throw new Error('This account is no longer available.')
      return accountProjection(row)
    },
    async loadUsers(user, scenario = 'normal') {
      requireAdmin(user); await wait()
      if (scenario === 'load-error') throw new Error('Unable to load users. Please try again.')
      return scenario === 'empty' ? [] : this.listUsers(user)
    },
    async saveUser(form, user, scenario = 'normal') {
      requireAdmin(user)
      const snapshot = { ...form }, rows = this.users
      const check = () => {
        requireAdmin(user)
        if (this.users !== rows) throw new Error('The demo session changed. Please reopen User Management.')
        const existing = snapshot.id ? this.users.find(row => row.id === snapshot.id) : null
        if (snapshot.id && (!existing || existing.revision !== snapshot.revision)) throw new Error('This account changed. Reopen it before saving.')
        if (existing && snapshot.username !== existing.username) throw new Error('An existing username cannot be changed.')
        const errors = validateAccount(snapshot, this.users, snapshot.id)
        if (Object.keys(errors).length) throw new Error(Object.values(errors)[0])
        if (existing?.username === user.username && snapshot.role !== existing.role) throw new Error('You cannot change your own Admin role.')
        if (existing?.role === 'Admin' && existing.status === 'Active' && snapshot.role !== 'Admin' && this.users.filter(row => row.role === 'Admin' && row.status === 'Active').length <= 1) throw new Error('Keep at least one active Admin account.')
        return existing
      }
      check(); await wait(); const existing = check()
      if (scenario === 'save-error') throw new Error('Unable to save this user. Your input has been kept. Please try again.')
      const values = { username: snapshot.username.trim(), displayName: snapshot.displayName.trim(), email: snapshot.email.trim().toLowerCase(), role: snapshot.role, group: snapshot.group }
      if (existing) Object.assign(existing, values, { revision: existing.revision + 1 })
      else this.users.unshift({ ...values, id: `USER-${String(Math.max(0, ...this.users.map(row => Number(row.id.slice(5)))) + 1).padStart(3, '0')}`, status: 'Active', createdAt: new Date().toISOString(), lastLogin: null, revision: 0 })
    },
    async changeStatus(id, expectedRevision, status, user, scenario = 'normal') {
      requireAdmin(user); const rows = this.users
      const check = () => {
        requireAdmin(user)
        const row = this.users.find(row => row.id === id)
        if (this.users !== rows || !row || row.revision !== expectedRevision) throw new Error('This account changed. Reopen it before changing its status.')
        if (!['Active', 'Inactive'].includes(status)) throw new Error('Select a supported account status.')
        if (row.username === user.username) throw new Error('You cannot change your own account status.')
        if (row.role === 'Admin' && row.status === 'Active' && status === 'Inactive' && this.users.filter(item => item.role === 'Admin' && item.status === 'Active').length <= 1) throw new Error('Keep at least one active Admin account.')
        return row
      }
      check(); await wait(); const row = check()
      if (scenario === 'status-error') throw new Error('Unable to change account status. Please try again.')
      row.status = status; row.revision += 1
    },
    clearUsers() { this.$reset() },
  },
})
