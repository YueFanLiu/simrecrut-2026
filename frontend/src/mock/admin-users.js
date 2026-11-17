export const accountRoles = ['Admin', 'HR', 'Researcher', 'Candidate']
export const accountGroups = ['Platform', 'Research', 'Company A', 'Company B', 'Individuals']
export const accountStatuses = ['Active', 'Inactive']
export function requireAdmin(user) {
  if (user?.role !== 'Admin') throw new Error('An Admin demo session is required to manage users.')
}
export function createAdminUsers() {
  return [
    ['admin', 'Demo Admin User', 'Admin', 'Platform', 'Active'],
    ['researcher', 'Demo Researcher User', 'Researcher', 'Research', 'Active'],
    ['hr', 'Demo HR User', 'HR', 'Company A', 'Active'],
    ['demo', 'Demo Candidate User', 'Candidate', 'Individuals', 'Active'],
    ['alex_morgan', 'Alex Morgan', 'HR', 'Company A', 'Active'],
    ['jordan_lee', 'Jordan Lee', 'Researcher', 'Research', 'Active'],
    ['taylor_reed', 'Taylor Reed', 'HR', 'Company B', 'Inactive'],
    ['sam_parker', 'Sam Parker', 'Candidate', 'Individuals', 'Active'],
    ['casey_brown', 'Casey Brown', 'Researcher', 'Research', 'Active'],
    ['morgan_blake', 'Morgan Blake', 'HR', 'Company B', 'Active'],
    ['robin_smith', 'Robin Smith', 'Admin', 'Platform', 'Active'],
    ['jamie_clark', 'Jamie Clark', 'Candidate', 'Individuals', 'Inactive'],
  ].map(([username, displayName, role, group, status], i) => ({ id: `USER-${String(i + 1).padStart(3, '0')}`, username, displayName,
    email: `${username}@simrecrut.test`, role, group, status, createdAt: '2026-09-15T14:20:00.000Z',
    lastLogin: status === 'Active' ? `2026-10-0${8 - i % 6}T09:12:00.000Z` : null, revision: 0 }))
}
// Return account metadata only. Role/group membership is not a case data grant.
export function accountProjection(row) {
  const { id, username, displayName, email, role, group, status, createdAt, lastLogin, revision } = row
  return { id, username, displayName, email, role, group, status, createdAt, lastLogin, revision }
}
export function filterAdminUsers(rows, { keyword = '', role = '', status = '', group = '' } = {}) {
  const query = keyword.trim().toLowerCase()
  return rows.filter(row => (!query || [row.username, row.displayName, row.email, row.role].some(value => value.toLowerCase().includes(query)))
    && (!role || row.role === role) && (!status || row.status === status) && (!group || row.group === group))
}
export function validateAccount(form, rows, id = null) {
  const errors = {}
  const existing = rows.find(row => row.id === id)
  if ((!existing || form.username !== existing.username) && (typeof form.username !== 'string' || !/^[A-Za-z0-9_]{3,20}$/.test(form.username.trim()))) errors.username = 'Use 3-20 characters: letters, numbers or underscores.'
  if (typeof form.displayName !== 'string' || !form.displayName.trim() || form.displayName.trim().length > 60) errors.displayName = 'Enter a display name of 1 to 60 characters.'
  if (typeof form.email !== 'string' || form.email.trim().length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Enter a valid email address of up to 100 characters.'
  if (!accountRoles.includes(form.role)) errors.role = 'Select a supported account role.'
  if (!accountGroups.includes(form.group)) errors.group = 'Select a supported account group.'
  for (const key of ['username', 'email']) if (typeof form[key] === 'string' && rows.some(row => row.id !== id && row[key].toLowerCase() === form[key].trim().toLowerCase())) errors[key] = `This ${key} is already used in the demo directory.`
  return errors
}
