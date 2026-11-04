<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElCheckboxGroup, ElDialog, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import AdminLayout from '../../layouts/AdminLayout.vue'
import * as api from '../../api/administration.js'

const route = useRoute()
const accountsPage = computed(() => route.name === 'AdminUsers')
const roleCodes = ['CANDIDATE', 'HR', 'RESEARCHER', 'ADMIN']
const accounts = ref([])
const roles = ref([])
const catalogue = ref([])
const form = ref(null)
const modal = ref('')
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const notice = ref('')
const keyword = ref('')
let sequence = 0

const allowedPermissions = computed(() => catalogue.value.filter(item => item.allowedRoles.includes(form.value?.roleCode)))

async function load() {
  const request = ++sequence
  loading.value = true
  error.value = ''
  try {
    if (accountsPage.value) {
      const filters = keyword.value.trim() ? { keyword: keyword.value.trim() } : {}
      const rows = await api.users(filters)
      if (request === sequence) accounts.value = rows
    } else {
      const rows = await api.roles()
      if (request === sequence) roles.value = rows
    }
  } catch (failure) {
    if (request === sequence) error.value = failure.message
  } finally {
    if (request === sequence) loading.value = false
  }
}

async function open(action, row) {
  if (busy.value) return
  const request = sequence
  busy.value = true
  actionError.value = ''
  notice.value = ''
  try {
    if (action === 'create') {
      form.value = { username: '', email: '', roles: ['CANDIDATE'] }
    } else if (action === 'permissions') {
      const [current, options] = await Promise.all([api.readRolePermissions(row.code), api.permissions()])
      if (request !== sequence) return
      catalogue.value = options
      form.value = current
    } else {
      const account = await api.userDetail(row.userId)
      if (request !== sequence) return
      form.value = account
    }
    modal.value = action
  } catch (failure) {
    if (request === sequence) actionError.value = failure.message
  } finally {
    busy.value = false
  }
}

async function save() {
  if (busy.value) return
  const request = sequence
  const action = modal.value
  const record = form.value
  busy.value = true
  actionError.value = ''
  try {
    if (action === 'create') await api.createUser(record)
    if (action === 'email') await api.saveUser(record)
    if (action === 'roles') await api.assignRole(record.userId, record.revision, record.roles)
    if (action === 'status') await api.changeUserStatus(record.userId, record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', record.revision)
    if (action === 'permissions') await api.rolePermissions(record)
    if (request !== sequence) return
    modal.value = ''
    notice.value = ['create', 'email'].includes(action)
      ? 'Account saved. The user can verify their mailbox and set a password from the password reset page.'
      : 'Changes saved.'
    await load()
  } catch (failure) {
    if (request === sequence) actionError.value = failure.message
  } finally {
    busy.value = false
  }
}

watch(accountsPage, () => { modal.value = ''; actionError.value = ''; notice.value = ''; load() }, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>

<template>
  <AdminLayout>
    <h1>{{ accountsPage ? 'User Management' : 'Roles and Permissions' }}</h1>
    <ElAlert v-if="error || (!modal && actionError)" :title="error || actionError" type="error" :closable="false" />
    <p v-if="notice" role="status">{{ notice }}</p>
    <form v-if="accountsPage" class="toolbar" @submit.prevent="load">
      <ElInput v-model="keyword" aria-label="Search by username or email" placeholder="Search by username or email" maxlength="100" />
      <ElButton native-type="submit" :loading="loading" :disabled="busy">Search</ElButton>
      <ElButton type="primary" :disabled="busy" @click="open('create')">Create Account</ElButton>
    </form>
    <ElButton v-else :loading="loading" :disabled="busy" @click="load">Reload Roles</ElButton>
    <ElTable v-if="accountsPage" v-loading="loading" :data="accounts" row-key="userId">
      <ElTableColumn prop="username" label="Username" />
      <ElTableColumn prop="email" label="Email" />
      <ElTableColumn label="Roles"><template #default="{ row }">{{ row.roles.join(', ') }}</template></ElTableColumn>
      <ElTableColumn prop="status" label="Status" />
      <ElTableColumn label="Mailbox"><template #default="{ row }">{{ row.emailVerified ? 'Verified' : 'Unverified' }}</template></ElTableColumn>
      <ElTableColumn label="Actions" min-width="270">
        <template #default="{ row }">
          <ElButton size="small" :disabled="busy" @click="open('email', row)">Edit Email</ElButton>
          <ElButton size="small" :disabled="busy" @click="open('roles', row)">Edit Roles</ElButton>
          <ElButton size="small" :disabled="busy" @click="open('status', row)">{{ row.status === 'ACTIVE' ? 'Disable' : 'Enable' }}</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElTable v-else v-loading="loading" :data="roles" row-key="code">
      <ElTableColumn prop="name" label="Role" />
      <ElTableColumn prop="code" label="Code" />
      <ElTableColumn prop="description" label="Description" />
      <ElTableColumn label="Actions"><template #default="{ row }">
        <ElButton :disabled="busy" @click="open('permissions', row)">Edit Permissions</ElButton>
      </template></ElTableColumn>
    </ElTable>

    <ElDialog :model-value="Boolean(modal)" :title="({ create: 'Create Account', email: 'Edit Email', roles: 'Edit Roles', status: 'Change Account Status', permissions: 'Edit Role Permissions' })[modal]"
      :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="modal = ''">
      <ElForm v-if="form" label-position="top" :disabled="busy" @submit.prevent="save">
        <ElFormItem v-if="modal === 'create'" label="Username"><ElInput v-model="form.username" maxlength="64" required /></ElFormItem>
        <ElFormItem v-if="['create', 'email'].includes(modal)" label="Email"><ElInput v-model="form.email" type="email" maxlength="254" required /></ElFormItem>
        <ElFormItem v-if="['create', 'roles'].includes(modal)" label="Roles">
          <ElSelect v-model="form.roles" multiple><ElOption v-for="role in roleCodes" :key="role" :value="role" /></ElSelect>
        </ElFormItem>
        <p v-if="modal === 'status'">{{ form.status === 'ACTIVE' ? 'Disable' : 'Enable' }} {{ form.username }}?</p>
        <template v-if="modal === 'permissions'">
          <p>{{ form.roleCode }}</p>
          <ElCheckboxGroup v-model="form.permissions" class="permissions">
            <ElCheckbox v-for="item in allowedPermissions" :key="item.code" :value="item.code"
              :disabled="form.roleCode === 'ADMIN' && item.protectedAdminPermission">{{ item.name }} ({{ item.code }})</ElCheckbox>
          </ElCheckboxGroup>
        </template>
        <ElAlert v-if="actionError" :title="actionError" type="error" :closable="false" />
        <div class="toolbar">
          <ElButton :disabled="busy" @click="modal = ''">Cancel</ElButton>
          <ElButton type="primary" native-type="submit" :loading="busy" :disabled="['create', 'roles'].includes(modal) && !form.roles.length">Save</ElButton>
        </div>
      </ElForm>
    </ElDialog>
  </AdminLayout>
</template>

<style scoped>
.toolbar { display: flex; gap: 12px; margin: 16px 0; }
.toolbar .el-input { max-width: 340px; }
.toolbar .el-button { margin: 0; }
.permissions { display: grid; }
.permissions :deep(.el-checkbox__label) { white-space: normal; }
</style>
