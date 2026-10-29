<script setup>
import { useRouter } from 'vue-router'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElDrawer, ElEmpty, ElForm, ElFormItem, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useAdminUsersStore } from '../../../store/modules/admin-users.js'
import { accountGroups, accountRoles, accountStatuses, filterAdminUsers, validateAccount } from '../../../mock/admin-users.js'
import { adminUsersScenario } from '../../../config/demo.js'

const router = useRouter()
const auth = useAuthStore(), store = useAdminUsersStore()
const loading = ref(true), loaded = ref(false), loadError = ref(''), actionError = ref(''), notice = ref(''), busy = ref(false)
const page = ref(1), pageSize = ref(10), selectedId = ref(null), editor = ref(false), statusTarget = ref(null), formRef = ref()
const filters = reactive({ keyword: '', role: '', status: '', group: '' })
const form = reactive({ id: null, revision: null, username: '', displayName: '', email: '', role: 'Candidate', group: 'Individuals' })
const rows = computed(() => loaded.value && auth.hasDemoRole('Admin') && adminUsersScenario !== 'empty' ? store.listUsers(auth.demoUser) : [])
const filtered = computed(() => filterAdminUsers(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const selected = computed(() => { try { return selectedId.value ? store.readUser(selectedId.value, auth.demoUser) : null } catch { return null } })
const rules = Object.fromEntries(['username', 'displayName', 'email', 'role', 'group'].map(key => [key, [{ validator: (_, value, callback) => {
  const error = validateAccount(form, store.users, form.id)[key]
  callback(error ? new Error(error) : undefined)
}, trigger: ['blur', 'change'] }]]))
const formatDate = value => value ? new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) : 'Not available'
let active = true, sequence = 0
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => filtered.value.length, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))) })
function resetFilters() { Object.assign(filters, { keyword: '', role: '', status: '', group: '' }) }
async function load() {
  const request = ++sequence, user = auth.demoUser
  const current = () => active && request === sequence && auth.demoUser === user
  loading.value = true; loaded.value = false; loadError.value = ''; selectedId.value = null
  try { await store.loadUsers(user, adminUsersScenario); if (current()) loaded.value = true }
  catch (failure) { if (current()) loadError.value = failure.message }
  finally { if (current()) loading.value = false }
}
async function openEditor(row = null) {
  if (busy.value) return
  Object.assign(form, row ? { id: row.id, revision: row.revision, username: row.username, displayName: row.displayName, email: row.email, role: row.role, group: row.group }
    : { id: null, revision: null, username: '', displayName: '', email: '', role: 'Candidate', group: 'Individuals' })
  actionError.value = ''; notice.value = ''; editor.value = true
  await nextTick(); formRef.value?.clearValidate()
}
async function save() {
  if (busy.value || !await formRef.value.validate().catch(() => false)) return
  const user = auth.demoUser
  busy.value = true; actionError.value = ''
  try {
    await store.saveUser({ ...form }, user, adminUsersScenario)
    if (active && auth.demoUser === user) { notice.value = form.id ? 'User updated in the demo directory.' : 'User added to the demo directory. No login credentials were created.'; editor.value = false }
  } catch (failure) { if (active && auth.demoUser === user) actionError.value = failure.message }
  finally { if (active && auth.demoUser === user) busy.value = false }
}
function confirmStatus(row) { if (!busy.value) { actionError.value = ''; notice.value = ''; statusTarget.value = { ...row, nextStatus: row.status === 'Active' ? 'Inactive' : 'Active' } } }
async function changeStatus() {
  if (busy.value || !statusTarget.value) return
  const target = { ...statusTarget.value }, user = auth.demoUser
  busy.value = true; actionError.value = ''
  try {
    await store.changeStatus(target.id, target.revision, target.nextStatus, user, adminUsersScenario)
    if (active && auth.demoUser === user) { notice.value = `Account status changed to ${target.nextStatus} in the demo directory.`; statusTarget.value = null }
  } catch (failure) { if (active && auth.demoUser === user) actionError.value = failure.message }
  finally { if (active && auth.demoUser === user) busy.value = false }
}
onBeforeUnmount(() => { active = false; sequence += 1 })
load()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="users-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / Users</p><h1 id="users-title">User Management</h1><p>Manage mock account metadata and account status.</p></div><ElButton :loading="loading" :disabled="loading || busy" @click="load">Refresh</ElButton></div>
      <ElAlert type="info" :closable="false" title="Frontend demo only. Changes affect this in-memory directory, not login credentials or real permissions. Role and group membership do not grant access to case data." />
      <div class="filters"><ElInput v-model="filters.keyword" aria-label="Search users" placeholder="Search name, username, email or role" clearable /><ElSelect v-model="filters.role" aria-label="Filter by role" placeholder="All Roles"><ElOption label="All Roles" value="" /><ElOption v-for="value in accountRoles" :key="value" :value="value" :label="value" /></ElSelect><ElSelect v-model="filters.status" aria-label="Filter by account status" placeholder="All Statuses"><ElOption label="All Statuses" value="" /><ElOption v-for="value in accountStatuses" :key="value" :value="value" :label="value" /></ElSelect><ElSelect v-model="filters.group" aria-label="Filter by group" placeholder="All Groups"><ElOption label="All Groups" value="" /><ElOption v-for="value in accountGroups" :key="value" :value="value" :label="value" /></ElSelect><ElButton @click="resetFilters">Reset Filters</ElButton><ElButton type="primary" :disabled="loading || !!loadError || busy || adminUsersScenario === 'empty'" @click="openEditor()">Add User</ElButton></div>
      <p class="filter-note">Filters apply to loaded mock accounts. Dates use UTC. Reviewer is an assigned responsibility of HR or Researcher.</p>
      <ElAlert v-if="notice" type="success" :closable="false" :title="notice" class="feedback" />
      <div v-if="loading" class="panel state" role="status"><p>Loading users...</p><ElSkeleton :rows="7" animated /></div>
      <div v-else-if="loadError" class="panel state" role="alert"><h2>Users Unavailable</h2><p>{{ loadError }}</p><ElButton @click="load">Try Again</ElButton></div>
      <div v-else-if="!rows.length" class="panel state"><ElEmpty description="No users in the demo directory" /></div>
      <div v-else-if="!filtered.length" class="panel state"><ElEmpty description="No users match your filters" /><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" aria-label="User accounts table"><table><caption>{{ filtered.length }} matching mock accounts</caption><thead><tr><th scope="col">Name / Username</th><th scope="col">Email</th><th scope="col">Role</th><th scope="col">Group</th><th scope="col">Status</th><th scope="col">Last Login (UTC)</th><th scope="col">Actions</th></tr></thead><tbody><tr v-for="row in visible" :key="row.id"><th scope="row"><ElButton link type="primary" @click="selectedId = row.id">{{ row.displayName }}</ElButton><span class="secondary">{{ row.username }}</span></th><td>{{ row.email }}</td><td><ElTag>{{ row.role }}</ElTag></td><td>{{ row.group }}</td><td><ElTag :type="row.status === 'Active' ? 'success' : 'info'">{{ row.status }}</ElTag></td><td>{{ formatDate(row.lastLogin) }}</td><td><div class="actions"><ElButton size="small" :disabled="busy" @click="openEditor(row)">Edit</ElButton><ElButton size="small" :disabled="busy || row.username === auth.demoUser?.username" @click="confirmStatus(row)">{{ row.status === 'Active' ? 'Disable' : 'Enable' }}</ElButton></div></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="row in visible" :key="row.id" class="panel"><div class="card-heading"><h2><ElButton link type="primary" @click="selectedId = row.id">{{ row.displayName }}</ElButton></h2><ElTag :type="row.status === 'Active' ? 'success' : 'info'">{{ row.status }}</ElTag></div><p>{{ row.username }}<br />{{ row.email }}<br />{{ row.role }} · {{ row.group }}<br />Last Login: {{ formatDate(row.lastLogin) }}</p><div class="actions"><ElButton :disabled="busy" @click="openEditor(row)">Edit</ElButton><ElButton :disabled="busy || row.username === auth.demoUser?.username" @click="confirmStatus(row)">{{ row.status === 'Active' ? 'Disable' : 'Enable' }}</ElButton></div></article></div>
        <div class="pagination"><span>Showing {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} of {{ filtered.length }} users</span><ElSelect v-model="pageSize" aria-label="Users per page"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="`${size} / page`" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
      <article class="panel notes"><h2>Notes</h2><ul><li>Only the Admin demo account can open this page.</li><li>Disabling a directory entry preserves its metadata and does not delete historical records.</li><li>You cannot disable your own account or remove the last active Admin.</li><li>Role configuration is available in Roles &amp; Permissions. Case grants remain separate.</li><li>Directory changes reset on sign out or reload. They do not alter the existing demo login accounts.</li></ul><ElButton @click="router.push({ name: 'AdminRoles' })">Roles &amp; Permissions</ElButton></article>
      <ElDrawer :model-value="!!selectedId" title="User Details" size="min(380px, 100vw)" @close="selectedId = null"><template v-if="selected"><h2>{{ selected.displayName }}</h2><ElTag :type="selected.status === 'Active' ? 'success' : 'info'">{{ selected.status }}</ElTag><dl><dt>Account ID</dt><dd>{{ selected.id }}</dd><dt>Username</dt><dd>{{ selected.username }}</dd><dt>Email</dt><dd>{{ selected.email }}</dd><dt>Role</dt><dd>{{ selected.role }}</dd><dt>Group</dt><dd>{{ selected.group }}</dd><dt>Last Login (UTC)</dt><dd>{{ formatDate(selected.lastLogin) }}</dd><dt>Created At (UTC)</dt><dd>{{ formatDate(selected.createdAt) }}</dd></dl><ElAlert type="info" :closable="false" title="Account metadata only. Case grants and permission details are outside this page." /><div class="drawer-actions"><ElButton :disabled="busy" @click="openEditor(selected)">Edit User</ElButton><ElButton :disabled="busy || selected.username === auth.demoUser?.username" @click="confirmStatus(selected)">{{ selected.status === 'Active' ? 'Disable User' : 'Enable User' }}</ElButton></div></template><p v-else>This account is no longer available.</p></ElDrawer>
      <ElDialog v-model="editor" :title="form.id ? 'Edit User' : 'Add User'" width="min(540px, calc(100vw - 32px))" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" :before-close="done => { if (!busy) done() }">
        <p>Update the mock directory. No password, login account or real permission is created.</p><ElAlert v-if="actionError" type="error" :closable="false" :title="actionError" />
        <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="busy" @submit.prevent="save"><ElFormItem label="Username" prop="username"><ElInput v-model="form.username" aria-label="Username" maxlength="20" :disabled="!!form.id" /></ElFormItem><ElFormItem label="Display Name" prop="displayName"><ElInput v-model="form.displayName" aria-label="Display Name" maxlength="60" /></ElFormItem><ElFormItem label="Email" prop="email"><ElInput v-model="form.email" aria-label="Email" maxlength="100" /></ElFormItem><ElFormItem label="Role" prop="role"><ElSelect v-model="form.role" aria-label="Account role" :disabled="form.username === auth.demoUser?.username"><ElOption v-for="value in accountRoles" :key="value" :value="value" :label="value" /></ElSelect></ElFormItem><ElFormItem label="Group" prop="group"><ElSelect v-model="form.group" aria-label="Account group"><ElOption v-for="value in accountGroups" :key="value" :value="value" :label="value" /></ElSelect></ElFormItem><div class="dialog-actions"><ElButton :disabled="busy" @click="editor = false">Cancel</ElButton><ElButton type="primary" native-type="submit" :loading="busy" :disabled="busy">Save User</ElButton></div></ElForm>
      </ElDialog>
      <ElDialog :model-value="!!statusTarget" :title="statusTarget?.nextStatus === 'Inactive' ? 'Disable User' : 'Enable User'" width="min(480px, calc(100vw - 32px))" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" :before-close="done => { if (!busy) done() }" @close="statusTarget = null">
        <p>Change {{ statusTarget?.displayName }} to {{ statusTarget?.nextStatus }} in the demo directory? Existing metadata is retained. Demo login credentials remain unchanged.</p><ElAlert v-if="actionError" type="error" :closable="false" :title="actionError" /><template #footer><ElButton :disabled="busy" @click="statusTarget = null">Cancel</ElButton><ElButton type="primary" :loading="busy" :disabled="busy" @click="changeStatus">Confirm Status Change</ElButton></template>
      </ElDialog>
    </section>
  </AdminLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -.7px; } h2 { font-size: 18px; color: #132941; overflow-wrap: anywhere; } p, li, dl { color: #586b83; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; } .breadcrumb, .filter-note { font-size: 12px; } .heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; } .panel { padding: 20px; border-radius: 12px; border: 1px solid #e1eaf5; background: white; min-width: 0; } .filters { display: grid; grid-template-columns: minmax(180px,1fr) repeat(3,140px) auto auto; gap: 12px; margin-top: 24px; } .el-button { margin-left: 0; } .state { text-align: center; padding: 36px 20px; } .feedback { margin-bottom: 16px; } .table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid #176bdf; } table { width: 100%; min-width: 1080px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; } caption { text-align: left; font-size: 12px; padding-bottom: 16px; } th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; overflow-wrap: anywhere; } thead th { background: #f3f7fd; color: #132941; } .secondary { display: block; font-size: 12px; font-weight: normal; } .actions, .drawer-actions, .dialog-actions { display: flex; flex-wrap: wrap; gap: 10px; } .drawer-actions { margin-top: 24px; } .dialog-actions { justify-content: flex-end; margin-top: 24px; } .mobile-list { display: none; } .card-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; align-items: center; } .card-heading h2 { margin: 0; min-width: 0; max-width: 100%; } .card-heading .el-button { white-space: normal; height: auto; max-width: 100%; text-align: left; } .card-heading :deep(.el-button > span) { display: block; overflow-wrap: anywhere; white-space: normal; max-width: 100%; } .pagination { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 16px; color: #586b83; font-size: 12px; margin: 24px 0; } .pagination .el-select { width: 120px; } .notes { margin-top: 24px; background: #f0f6ff; } .notes ul { padding-left: 20px; } dt { font-weight: 600; color: #132941; } dd { margin: 0 0 12px; } .el-form { margin-top: 20px; }
@media (max-width: 1250px) { .filters { grid-template-columns: repeat(3,minmax(0,1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } .filters { grid-template-columns: repeat(2,minmax(0,1fr)); } h1 { font-size: 25px; } }
@media (max-width: 420px) { .filters { grid-template-columns: minmax(0,1fr); } .pagination { justify-content: center; } }
</style>
