import { accountRoles } from './admin-users.js'

export const permissionGroups = [
  { id: 'candidate.jobs', label: 'Published Jobs', roles: ['Candidate'], children: [ ['candidate.jobs.view', 'View published jobs'] ] },
  { id: 'candidate.cases', label: 'My Simulations', roles: ['Candidate'], children: [ ['candidate.cases.view', 'View own simulations'], ['candidate.cases.run', 'Run a simulation'], ['candidate.cases.delete', 'Delete own simulation'] ] },
  { id: 'hr.jobs', label: 'Job Management', roles: ['HR'], children: [ ['hr.jobs.view', 'View managed jobs'], ['hr.jobs.write', 'Create and edit job drafts'], ['hr.jobs.publish', 'Publish job versions'] ] },
  { id: 'hr.cases', label: 'Authorised Cases', roles: ['HR'], children: [ ['hr.cases.view', 'View authorised case metadata'] ] },
  { id: 'research.experiments', label: 'Experiments', roles: ['Researcher'], children: [ ['research.experiments.view', 'View authorised experiments'], ['research.experiments.write', 'Create and configure experiments'], ['research.experiments.run', 'Run local experiment validation'], ['research.experiments.assign', 'Assign Research reviews'] ] },
  { id: 'research.subjects', label: 'Research Subjects', roles: ['Researcher'], children: [ ['research.subjects.view', 'View authorised subjects'], ['research.subjects.assign', 'Assign Primary reviews'] ] },
  { id: 'research.models', label: 'Research Models', roles: ['Researcher'], children: [ ['research.models.view', 'View model metadata'] ] },
  { id: 'research.metrics', label: 'Experiment Metrics', roles: ['Researcher'], children: [ ['research.metrics.view', 'View available experiment metrics'] ] },
  { id: 'reviewer.tasks', label: 'My Review Tasks', roles: ['HR', 'Researcher'], children: [ ['reviewer.tasks.view', 'View assigned review tasks'], ['reviewer.tasks.submit', 'Submit independent reviews'] ] },
  { id: 'admin.users', label: 'User Management', roles: ['Admin'], children: [ ['admin.users.view', 'View account metadata'], ['admin.users.write', 'Add and edit directory entries'], ['admin.users.status', 'Change account status'] ] },
  { id: 'admin.models', label: 'Model Versions', roles: ['Admin'], children: [ ['admin.models.view', 'View model version metadata'], ['admin.models.register', 'Register offline package metadata'], ['admin.models.approve', 'Approve tested packages'], ['admin.models.activate', 'Activate approved versions'], ['admin.models.retire', 'Retire with a compatible replacement'] ] },
  { id: 'admin.protocols', label: 'Frozen Protocols', roles: ['Admin'], children: [ ['admin.protocols.view', 'View frozen protocol metadata'] ] },
  { id: 'admin.health', label: 'Service Health', roles: ['Admin'], children: [ ['admin.health.view', 'View runtime health metadata'] ] },
  { id: 'admin.failures', label: 'Failed Jobs & Errors', roles: ['Admin'], children: [ ['admin.failures.view', 'View safe failure metadata'] ] },
  { id: 'admin.audit', label: 'Audit Logs', roles: ['Admin'], children: [ ['admin.audit.view', 'View safe audit metadata'] ] },
  { id: 'admin.roles', label: 'Roles & Permissions', roles: ['Admin'], children: [ ['admin.roles.view', 'View role configuration'], ['admin.roles.edit', 'Edit role permissions'], ['admin.roles.assign', 'Assign directory roles'] ] },
].map(group => ({ ...group, children: group.children.map(([id, label]) => ({ id, label })) }))
const descriptions = {
  Admin: 'Manage account metadata and role configuration. Case content still requires separate authorisation.',
  HR: 'Manage jobs and authorised case metadata; perform assigned human reviews.',
  Researcher: 'Manage authorised experiments and subjects; view model metadata and perform assigned reviews.',
  Candidate: 'Browse published jobs and manage own simulations.',
}
export function rolePermissionTree(role) {
  return permissionGroups.filter(group => group.roles.includes(role)).map(group => ({ id: group.id, label: group.label, disabled: role === 'Admin', children: group.children.map(row => ({ ...row, disabled: role === 'Admin' })) }))
}
export function allowedPermissions(role) { return rolePermissionTree(role).flatMap(group => group.children.map(row => row.id)) }
export function createAdminRoles() {
  return accountRoles.map(name => ({ name, description: descriptions[name], status: 'Active', permissions: allowedPermissions(name), revision: 0 }))
}
export function validateRoleConfiguration(form) {
  if (!accountRoles.includes(form.name)) return 'Select a supported account role.'
  if (typeof form.description !== 'string' || !form.description.trim() || form.description.trim().length > 500) return 'Enter a description of 1 to 500 characters.'
  const allowed = allowedPermissions(form.name)
  if (!Array.isArray(form.permissions) || form.permissions.some(id => !allowed.includes(id)) || new Set(form.permissions).size !== form.permissions.length) return 'Select valid menu and action permissions for this role.'
  const selected = new Set(form.permissions)
  if (permissionGroups.some(group => group.children.slice(1).some(row => selected.has(row.id)) && !selected.has(group.children[0].id))) return 'Select the module View permission before enabling its actions.'
  if (form.name === 'Admin' && allowed.some(id => !selected.has(id))) return 'Core Admin permissions are protected in this demonstration.'
  return ''
}
export function roleProjection(row, accounts) {
  const { name, description, status, revision } = row
  return { name, description, status, revision, permissions: [...row.permissions], userCount: accounts.filter(account => account.role === name).length }
}
export function filterAdminRoles(rows, keyword = '') {
  const query = keyword.trim().toLowerCase()
  return rows.filter(row => [row.name, row.description].some(value => value.toLowerCase().includes(query)))
}
export function permissionModules(permissions) { return permissionGroups.filter(group => permissions.includes(group.children[0].id)).map(group => group.label) }
