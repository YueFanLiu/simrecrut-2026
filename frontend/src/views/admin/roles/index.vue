<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDialog, ElDrawer, ElEmpty, ElInput, ElOption, ElSelect, ElSkeleton, ElTag, ElTree } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useAdminRolesStore } from '../../../store/modules/admin-roles.js'
import { useAdminUsersStore } from '../../../store/modules/admin-users.js'
import { allowedPermissions, filterAdminRoles, permissionGroups, permissionModules, rolePermissionTree, validateRoleConfiguration } from '../../../mock/admin-roles.js'
import { adminRolesScenario } from '../../../config/demo.js'

const auth = useAuthStore(), store = useAdminRolesStore(), users = useAdminUsersStore(), router = useRouter()
const loading = ref(true), loaded = ref(false), error = ref(''), feedback = ref(''), keyword = ref(''), busy = ref(false), editor = ref(false), assignment = ref(false), actionError = ref(''), treeRef = ref()
const form = reactive({ name: '', description: '', revision: 0, permissions: [] })
const assignmentRole = ref(''), accountId = ref(''), accountSnapshot = ref(null)
const rows = computed(() => loaded.value && auth.hasDemoRole('Admin') && adminRolesScenario !== 'empty' ? store.listRoles(auth.demoUser) : [])
const filtered = computed(() => filterAdminRoles(rows.value, keyword.value))
const accounts = computed(() => auth.hasDemoRole('Admin') ? users.listUsers(auth.demoUser) : [])
const tree = computed(() => rolePermissionTree(form.name))
const preview = computed(() => permissionModules(form.permissions))
const matrix = computed(() => permissionGroups.map(group => ({ id: group.id, label: group.label, view: group.children[0].id })))
let active = true, sequence = 0
async function load() {
  const request = ++sequence, user = auth.demoUser
  const current = () => active && request === sequence && auth.demoUser === user
  loaded.value = false; loading.value = true; error.value = ''
  try { await Promise.all([store.loadRoles(user, adminRolesScenario), users.loadUsers(user)]); if (current()) loaded.value = true }
  catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
async function openEditor(row) {
  if (busy.value) return
  Object.assign(form, { name: row.name, description: row.description, revision: row.revision, permissions: [...row.permissions] })
  actionError.value = ''; feedback.value = ''; editor.value = true
  await nextTick(); treeRef.value?.setCheckedKeys(form.permissions)
}
function updateSelection() { form.permissions = (treeRef.value?.getCheckedKeys(true) || []).filter(id => allowedPermissions(form.name).includes(id)) }
async function save() {
  if (busy.value) return
  updateSelection()
  actionError.value = validateRoleConfiguration(form)
  if (actionError.value) return
  const user = auth.demoUser
  busy.value = true
  try { await store.savePermissions({ ...form, permissions: [...form.permissions] }, user, adminRolesScenario); if (active && auth.demoUser === user) { feedback.value = `${form.name} permissions saved in the demonstration. Live routes and case grants remain unchanged.`; editor.value = false } }
  catch (failure) { if (active && auth.demoUser === user) actionError.value = failure.message }
  finally { if (active && auth.demoUser === user) busy.value = false }
}
function openAssignment(row) { if (!busy.value) { assignmentRole.value = row.name; accountId.value = ''; accountSnapshot.value = null; actionError.value = ''; feedback.value = ''; assignment.value = true } }
function selectAccount(id) { accountSnapshot.value = accounts.value.find(row => row.id === id) || null; actionError.value = '' }
async function assign() {
  if (busy.value) return
  if (!accountSnapshot.value) { actionError.value = 'Select an account.'; return }
  if (accountSnapshot.value.role === assignmentRole.value) { actionError.value = 'Choose an account not already assigned to this role.'; return }
  const user = auth.demoUser, target = { ...accountSnapshot.value }
  busy.value = true; actionError.value = ''
  try { await store.assignRole(target.id, target.revision, assignmentRole.value, user, adminRolesScenario); if (active && auth.demoUser === user) { feedback.value = `${target.displayName} is now ${assignmentRole.value} in the mock account directory.`; assignment.value = false } }
  catch (failure) { if (active && auth.demoUser === user) actionError.value = failure.message }
  finally { if (active && auth.demoUser === user) busy.value = false }
}
onBeforeUnmount(() => { active = false; sequence += 1 })
load()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="roles-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / Roles</p><h1 id="roles-title">Roles &amp; Permissions</h1><p>Configure mock menu and action permissions for the existing account roles.</p></div><div class="actions"><ElButton @click="router.push({ name: 'AdminUsers' })">Users</ElButton><ElButton :loading="loading" :disabled="loading || busy" @click="load">Refresh</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demo only. Saved permissions update this matrix and module preview. They do not change live routes, login roles or case data grants. Reviewer is an assigned responsibility of HR or Researcher." />
      <div class="search"><ElInput v-model="keyword" aria-label="Search roles" placeholder="Search role name or description" clearable /><ElButton @click="keyword = ''">Clear Search</ElButton></div>
      <ElAlert v-if="feedback" :title="feedback" type="success" :closable="false" class="feedback" />
      <div v-if="loading" class="panel state" role="status"><p>Loading roles...</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state" role="alert"><h2>Roles Unavailable</h2><p>{{ error }}</p><ElButton @click="load">Try Again</ElButton></div>
      <div v-else-if="!rows.length" class="panel state"><ElEmpty description="No roles available in this demonstration" /></div>
      <div v-else-if="!filtered.length" class="panel state"><ElEmpty description="No roles match your search" /><ElButton @click="keyword = ''">Clear Search</ElButton></div>
      <template v-else>
        <div class="role-cards"><article v-for="row in filtered" :key="row.name" class="panel role-card"><h2>{{ row.name }}</h2><p>{{ row.description }}</p><p>{{ row.userCount }} directory users</p><ElButton :disabled="busy" @click="openEditor(row)">Edit Permissions</ElButton></article></div>
        <article class="panel role-list"><h2>Role List</h2><div class="table-scroll" role="region" tabindex="0" aria-label="Role list table"><table><thead><tr><th scope="col">Role Name</th><th scope="col">Description</th><th scope="col">User Count</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody><tr v-for="row in filtered" :key="row.name"><th scope="row">{{ row.name }}</th><td>{{ row.description }}</td><td>{{ row.userCount }}</td><td><ElTag type="success">{{ row.status }}</ElTag></td><td><div class="actions"><ElButton size="small" :disabled="busy" @click="openEditor(row)">Edit Permissions</ElButton><ElButton size="small" :disabled="busy" @click="openAssignment(row)">Assign Role</ElButton></div></td></tr></tbody></table></div></article>
        <article class="panel matrix"><h2>Permissions Matrix</h2><p>Saved module View permissions. A menu entry does not authorise access to an individual resource.</p><div class="table-scroll" role="region" tabindex="0" aria-label="Permissions matrix"><table><thead><tr><th scope="col">Module</th><th v-for="role in rows" :key="role.name" scope="col">{{ role.name }}</th></tr></thead><tbody><tr v-for="module in matrix" :key="module.id"><th scope="row">{{ module.label }}</th><td v-for="role in rows" :key="role.name">{{ role.permissions.includes(module.view) ? 'Allowed' : 'Not allowed' }}</td></tr></tbody></table></div></article>
      </template>
      <article class="panel notes"><h2>Notes</h2><ul><li>The supported account roles are Admin, HR, Researcher and Candidate. No Platform or standalone Reviewer role is created.</li><li>Only permissions relevant to a role are configurable. An action requires its module View permission.</li><li>Core Admin permissions are protected to keep administration available in the demo.</li><li>Assign Role replaces an account's mock directory role and updates User Management. Current Admin and last active Admin protections still apply.</li><li>Case grants, ownership, retention and review assignment remain separate from menu configuration.</li><li>Sign out or reload restores permission fixtures. Use the administration menu to inspect model versions, protocols, service health, failures and audit examples.</li></ul></article>
      <ElDrawer v-model="editor" title="Role Details" size="min(460px, 100vw)" destroy-on-close :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" :before-close="done => { if (!busy) done() }">
        <template v-if="editor"><h2>{{ form.name }}</h2><ElTag type="success">Active</ElTag><div class="description"><label for="role-description">Description</label><ElInput id="role-description" v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit :disabled="busy" aria-label="Role description" /></div><h3>Menu and Action Permissions</h3><p v-if="form.name === 'Admin'">Core Admin permissions are read-only.</p><ElTree ref="treeRef" :data="tree" node-key="id" show-checkbox default-expand-all :props="{ label: 'label', children: 'children', disabled: node => busy || node.disabled }" :default-checked-keys="form.permissions" :class="{ saving: busy }" aria-label="Role permissions tree" @check="updateSelection" /><h3>Module Visibility Preview</h3><ul v-if="preview.length"><li v-for="module in preview" :key="module">{{ module }}</li></ul><p v-else>No modules selected.</p><p>This preview demonstrates menu visibility only. It does not grant case access or change the approved application routes.</p><ElAlert v-if="actionError" :title="actionError" type="error" :closable="false" /><div class="drawer-actions"><ElButton :disabled="busy" @click="editor = false">Cancel</ElButton><ElButton type="primary" :loading="busy" :disabled="busy" @click="save">Save Changes</ElButton></div></template>
      </ElDrawer>
      <ElDialog v-model="assignment" :title="`Assign ${assignmentRole} Role`" width="min(500px, calc(100vw - 32px))" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" :before-close="done => { if (!busy) done() }">
        <p>Select a mock directory account. Saving replaces its existing directory role. Login credentials and case grants remain unchanged.</p><label for="role-account">Account</label><ElSelect id="role-account" v-model="accountId" aria-label="Account to assign" placeholder="Select an account" :disabled="busy" filterable @change="selectAccount"><ElOption v-for="account in accounts" :key="account.id" :value="account.id" :label="`${account.displayName} (${account.username})`" :disabled="account.username === auth.demoUser?.username" /></ElSelect><p v-if="accountSnapshot">Current role: {{ accountSnapshot.role }}<br />New role: {{ assignmentRole }}</p><ElAlert v-if="actionError" :title="actionError" type="error" :closable="false" /><template #footer><ElButton :disabled="busy" @click="assignment = false">Cancel</ElButton><ElButton type="primary" :loading="busy" :disabled="busy" @click="assign">Save Assignment</ElButton></template>
      </ElDialog>
    </section>
  </AdminLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -.7px; } h2 { font-size: 18px; color: #132941; overflow-wrap: anywhere; } h3 { color: #132941; font-size: 15px; } p, li { color: #586b83; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; } .heading, .actions, .search, .drawer-actions { display: flex; flex-wrap: wrap; gap: 12px; } .heading { justify-content: space-between; align-items: center; margin-bottom: 20px; } .breadcrumb { font-size: 12px; } .panel { padding: 20px; border-radius: 12px; border: 1px solid #e1eaf5; background: white; min-width: 0; } .search { margin: 24px 0; } .search .el-input { flex: 1; min-width: 180px; } .role-cards { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 16px; } .role-card { display: flex; flex-direction: column; } .role-card h2 { margin-top: 0; } .role-card p:first-of-type { flex: 1; } .role-card .el-button { align-self: flex-start; } .role-list, .matrix, .notes { margin-top: 24px; } .notes { background: #f0f6ff; } .table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid #176bdf; } table { width: 100%; min-width: 720px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; } th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; } thead th { background: #f3f7fd; color: #132941; } .role-list th:first-child { min-width: 100px; } .role-list td:last-child { min-width: 230px; } .el-button { margin-left: 0; } .description { margin-top: 20px; } label { display: block; color: #132941; font-size: 14px; margin: 0 0 12px; } .drawer-actions { margin-top: 24px; justify-content: flex-end; } ul { padding-left: 20px; } .feedback { margin-bottom: 20px; } .state { text-align: center; padding: 36px 20px; } .saving { pointer-events: none; opacity: .6; } :deep(.el-tree-node__content) { height: auto; min-height: 32px; padding-top: 4px; padding-bottom: 4px; align-items: flex-start; } :deep(.el-tree-node__label) { white-space: normal; overflow-wrap: anywhere; min-width: 0; padding-right: 8px; } :deep(.el-tree-node__expand-icon) { flex-shrink: 0; } :deep(.el-tree-node__content > .el-checkbox) { flex-shrink: 0; } .matrix th:first-child { min-width: 190px; }
@media (max-width: 1150px) { .role-cards { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media (max-width: 520px) { .role-cards { grid-template-columns: minmax(0,1fr); } h1 { font-size: 25px; } }
</style>
