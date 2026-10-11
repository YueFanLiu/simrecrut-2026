<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElDrawer, ElEmpty, ElOption, ElPagination, ElSelect, ElInput, ElSkeleton, ElTag } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useAdminModelsStore } from '../../../store/modules/admin-models.js'
import { filterAdminModels, modelStatuses, modelTypes, offlinePackages } from '../../../mock/admin-models.js'
import { adminModelsScenario } from '../../../config/demo.js'

const auth = useAuthStore(), store = useAdminModelsStore()
const loaded = ref(false), loading = ref(true), error = ref(''), feedback = ref(''), actionError = ref(''), busy = ref(false)
const selectedId = ref(null), registration = ref(false), packageId = ref(''), releaseTarget = ref(null), replacementId = ref(''), page = ref(1), pageSize = ref(10)
const filters = reactive({ keyword: '', type: '', status: '' })
const rows = computed(() => loaded.value && auth.hasDemoRole('Admin') && adminModelsScenario !== 'empty' ? store.listModels(auth.demoUser) : [])
const filtered = computed(() => filterAdminModels(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const selected = computed(() => { try { return selectedId.value ? store.readModel(selectedId.value, auth.demoUser) : null } catch { return null } })
const packages = computed(() => offlinePackages().filter(row => !rows.value.some(model => model.id === row.id)))
const chosenPackage = computed(() => packages.value.find(row => row.id === packageId.value))
const replacements = computed(() => rows.value.filter(row => row.type === releaseTarget.value?.type && row.status === 'APPROVED'))
const slots = computed(() => modelTypes.map(type => ({ type, versions: rows.value.filter(row => row.type === type).length, active: rows.value.find(row => row.type === type && row.status === 'ACTIVE' && store.slots[type] === row.id) })))
const statusType = status => ({ ACTIVE: 'success', APPROVED: 'primary', TESTED: 'warning', RETIRED: 'info' })[status]
const date = value => value ? new Date(value).toUTCString() : 'Not recorded'
let active = true, sequence = 0
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => filtered.value.length, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))) })
function resetFilters() { Object.assign(filters, { keyword: '', type: '', status: '' }) }
async function load() {
  const request = ++sequence, user = auth.demoUser
  const current = () => active && sequence === request && auth.demoUser === user
  loaded.value = false; loading.value = true; error.value = ''; selectedId.value = null
  try { await store.loadModels(user, adminModelsScenario); if (current()) loaded.value = true }
  catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
function openRegistration() { if (!busy.value) { actionError.value = ''; feedback.value = ''; packageId.value = ''; registration.value = true } }
async function register() {
  if (busy.value) return
  if (!packageId.value) { actionError.value = 'Select an offline demo package.'; return }
  const user = auth.demoUser
  busy.value = true; actionError.value = ''
  try { await store.registerPackage(packageId.value, user, adminModelsScenario); if (active && auth.demoUser === user) { feedback.value = 'Offline package metadata registered as TESTED in the demo. No file was uploaded or executed.'; registration.value = false } }
  catch (failure) { if (active && auth.demoUser === user) actionError.value = failure.message }
  finally { if (active && auth.demoUser === user) busy.value = false }
}
function openRelease(row, action) { if (!busy.value) { actionError.value = ''; feedback.value = ''; replacementId.value = ''; releaseTarget.value = { ...row, action } } }
async function release() {
  if (busy.value || !releaseTarget.value) return
  const target = { ...releaseTarget.value }, user = auth.demoUser
  busy.value = true; actionError.value = ''
  try { await store.release(target.id, target.revision, target.action, replacementId.value, user, adminModelsScenario); if (active && auth.demoUser === user) { feedback.value = target.action === 'approve' ? `${target.id} is APPROVED in the demo. Approval does not activate it.` : 'The demo active slot was switched atomically; the previous active version is RETIRED and its metadata is retained.'; releaseTarget.value = null } }
  catch (failure) { if (active && auth.demoUser === user) actionError.value = failure.message }
  finally { if (active && auth.demoUser === user) busy.value = false }
}
onBeforeUnmount(() => { active = false; sequence += 1 })
load()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="models-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / Models</p><h1 id="models-title">Model Versions &amp; Release</h1><p>Inspect offline model metadata and demonstrate release transitions.</p></div><div class="actions"><ElButton :loading="loading" :disabled="loading || busy" @click="load">Refresh</ElButton><ElButton type="primary" :disabled="loading || !!error || busy || !packages.length || adminModelsScenario === 'empty'" @click="openRegistration">Register Package</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demo only. All packages, checks and readiness states are synthetic. No training, inference, file upload or real release is performed. Released public model metadata is shared with Research in this browser tab." />
      <div v-if="!loading && !error" class="slot-cards"><article v-for="slot in slots" :key="slot.type" class="panel"><h2>{{ slot.type }}</h2><p>{{ slot.versions }} registered versions</p><ElTag :type="slot.active ? 'success' : 'info'">{{ slot.active ? '1 ACTIVE' : 'No active version available' }}</ElTag><p v-if="slot.active">{{ slot.active.id }} / {{ slot.active.version }}</p></article></div>
      <div class="filters"><ElInput v-model="filters.keyword" aria-label="Search model versions" placeholder="Search version ID, type, version or description" clearable /><ElSelect v-model="filters.type" aria-label="Filter by model type" placeholder="All Model Types"><ElOption label="All Model Types" value="" /><ElOption v-for="value in modelTypes" :key="value" :label="value" :value="value" /></ElSelect><ElSelect v-model="filters.status" aria-label="Filter by model status" placeholder="All Statuses"><ElOption label="All Statuses" value="" /><ElOption v-for="value in modelStatuses" :key="value" :label="value" :value="value" /></ElSelect><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <p class="filter-note">Lifecycle: TESTED → APPROVED → ACTIVE → RETIRED. Dates use UTC. Historical version metadata is retained.</p><ElAlert v-if="feedback" :title="feedback" type="success" :closable="false" class="feedback" />
      <div v-if="loading" class="panel state" role="status"><p>Loading model versions...</p><ElSkeleton :rows="7" animated /></div>
      <div v-else-if="error" class="panel state" role="alert"><h2>Model Versions Unavailable</h2><p>{{ error }}</p><ElButton @click="load">Try Again</ElButton></div>
      <div v-else-if="!rows.length" class="panel state"><ElEmpty description="No model versions available" /></div>
      <div v-else-if="!filtered.length" class="panel state"><ElEmpty description="No model versions match your filters" /><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <template v-else>
        <article class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" aria-label="Model versions table"><table><caption>{{ filtered.length }} matching mock model versions</caption><thead><tr><th scope="col">Version ID</th><th scope="col">Model Type</th><th scope="col">Version</th><th scope="col">Description</th><th scope="col">Created At (UTC)</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody><tr v-for="row in visible" :key="row.id"><th scope="row">{{ row.id }}</th><td>{{ row.type }}</td><td>{{ row.version }}</td><td>{{ row.description }}</td><td>{{ date(row.createdAt) }}</td><td><ElTag :type="statusType(row.status)">{{ row.status }}</ElTag></td><td><div class="actions"><ElButton size="small" @click="selectedId = row.id">View</ElButton><ElButton v-if="row.status === 'TESTED'" size="small" :disabled="busy" @click="openRelease(row, 'approve')">Approve</ElButton><ElButton v-if="row.status === 'APPROVED'" size="small" :disabled="busy" @click="openRelease(row, 'activate')">Activate</ElButton><ElButton v-if="row.status === 'ACTIVE'" size="small" :disabled="busy || !rows.some(model => model.type === row.type && model.status === 'APPROVED')" @click="openRelease(row, 'retire')">Retire</ElButton></div></td></tr></tbody></table></div></article>
        <div class="mobile-list"><article v-for="row in visible" :key="row.id" class="panel"><div class="card-heading"><h2>{{ row.id }}</h2><ElTag :type="statusType(row.status)">{{ row.status }}</ElTag></div><p>{{ row.type }} / {{ row.version }}<br />{{ row.description }}<br />Created: {{ date(row.createdAt) }}</p><div class="actions"><ElButton @click="selectedId = row.id">View</ElButton><ElButton v-if="row.status === 'TESTED'" :disabled="busy" @click="openRelease(row, 'approve')">Approve</ElButton><ElButton v-if="row.status === 'APPROVED'" :disabled="busy" @click="openRelease(row, 'activate')">Activate</ElButton><ElButton v-if="row.status === 'ACTIVE'" :disabled="busy || !rows.some(model => model.type === row.type && model.status === 'APPROVED')" @click="openRelease(row, 'retire')">Retire</ElButton></div></article></div>
        <div class="pagination"><span>Showing {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} of {{ filtered.length }} models</span><ElSelect v-model="pageSize" aria-label="Models per page"><ElOption v-for="size in [5, 10, 20]" :key="size" :label="`${size} / page`" :value="size" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
      <article class="panel notes"><h2>Release Notes</h2><ul><li>Registration records predefined offline package metadata as TESTED. Approval and activation are separate steps.</li><li>Activating an APPROVED version replaces the current version of the same type and retires the previous version.</li><li>Retire requires an APPROVED compatible replacement. An active core slot cannot be left empty.</li><li>Package evidence and readiness are local fixtures. No real package integrity or service availability is checked.</li><li>Evaluation metrics remain unavailable; no accuracy or fairness score is invented.</li><li>Model metadata survives sign out and reload in this tab. Existing experiment versions and case results remain pinned; new Research drafts use compatible active versions.</li></ul></article>
      <ElDrawer :model-value="!!selectedId" title="Model Version Details" size="min(460px, 100vw)" @close="selectedId = null"><template v-if="selected"><h2>{{ selected.id }}</h2><ElTag :type="statusType(selected.status)">{{ selected.status }}</ElTag><dl><template v-for="(label, key) in { type: 'Model Type', version: 'Version', description: 'Description', architecture: 'Architecture', parameters: 'Parameters', schema: 'Feature Schema', preprocessing: 'Preprocessing', protocol: 'Frozen Protocol', cutoff: 'Cutoff', inputMode: 'Input Mode', manifest: 'Synthetic Manifest', checksum: 'Synthetic Checksum', trainingRun: 'Synthetic Training Run' }" :key="key"><dt>{{ label }}</dt><dd>{{ selected[key] }}</dd></template><dt>Created At (UTC)</dt><dd>{{ date(selected.createdAt) }}</dd><dt>Created By</dt><dd>{{ selected.createdBy }}</dd><dt>Approved / Activated / Retired (UTC)</dt><dd>{{ date(selected.approvedAt) }}<br />{{ date(selected.activatedAt) }}<br />{{ date(selected.retiredAt) }}</dd></dl><h3>Recorded Demo Checks</h3><ul><li>Integrity: {{ selected.checks.integrity ? 'Recorded as passed' : 'Incomplete' }}</li><li>Validation: {{ selected.checks.validation ? 'Recorded as passed' : 'Incomplete' }}</li><li>Final test evidence: {{ selected.checks.finalTest ? 'Recorded as complete' : 'Incomplete' }}</li><li>{{ selected.checks.validationReport }}</li><li>{{ selected.checks.finalTestReport }}</li><li>{{ selected.readiness }}</li></ul><h3>Evaluation Summary</h3><p>{{ selected.evaluation }}</p><ElAlert type="info" :closable="false" title="Metadata and checks are synthetic. This drawer exposes no artifact paths, model files, CV data or human review reasons." /></template><p v-else>This model version is unavailable.</p></ElDrawer>
      <ElDialog v-model="registration" title="Register Offline Package" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" :before-close="done => { if (!busy) done() }"><p>Select predefined synthetic package metadata. No files are uploaded or executed. Registration creates a TESTED version and does not activate it.</p><ElSelect v-model="packageId" aria-label="Offline package" placeholder="Select an offline demo package" :disabled="busy"><ElOption v-for="pkg in packages" :key="pkg.id" :label="`${pkg.id} / ${pkg.type} / ${pkg.version}`" :value="pkg.id" /></ElSelect><p v-if="chosenPackage">{{ chosenPackage.schema }} · {{ chosenPackage.protocol }}<br />Cutoff: {{ chosenPackage.cutoff }}<br />Synthetic validation and final-test report references recorded.</p><ElAlert v-if="actionError" :title="actionError" type="error" :closable="false" /><template #footer><ElButton :disabled="busy" @click="registration = false">Cancel</ElButton><ElButton type="primary" :loading="busy" :disabled="busy" @click="register">Register Metadata</ElButton></template></ElDialog>
      <ElDialog :model-value="!!releaseTarget" title="Confirm Model Release" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" :before-close="done => { if (!busy) done() }" @close="releaseTarget = null"><template v-if="releaseTarget"><p>Version: {{ releaseTarget.id }} / {{ releaseTarget.type }}</p><p v-if="releaseTarget.action === 'approve'">Approve the TESTED package using its recorded synthetic checks? Approval does not activate the version.</p><p v-else-if="releaseTarget.action === 'activate'">Activate this APPROVED version after the synthetic readiness check? The previous active version will become RETIRED. Historical metadata is retained.</p><template v-else><p>Retire this ACTIVE version by activating an APPROVED replacement of the same type. The slot will remain available in the demo.</p><ElSelect v-model="replacementId" aria-label="Replacement model version" placeholder="Select an APPROVED replacement" :disabled="busy"><ElOption v-for="row in replacements" :key="row.id" :label="`${row.id} / ${row.version}`" :value="row.id" /></ElSelect></template><ElAlert v-if="actionError" :title="actionError" type="error" :closable="false" /></template><template #footer><ElButton :disabled="busy" @click="releaseTarget = null">Cancel</ElButton><ElButton type="primary" :loading="busy" :disabled="busy" @click="release">Confirm Release</ElButton></template></ElDialog>
    </section>
  </AdminLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -.7px; } h2 { font-size: 18px; color: #132941; overflow-wrap: anywhere; } h3 { font-size: 15px; color: #132941; } p, li, dl { color: #586b83; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; } .heading, .actions { display: flex; flex-wrap: wrap; gap: 12px; } .heading { justify-content: space-between; align-items: center; margin-bottom: 20px; } .breadcrumb, .filter-note { font-size: 12px; } .panel { padding: 20px; border: 1px solid #e1eaf5; border-radius: 12px; background: white; min-width: 0; } .slot-cards { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; margin: 24px 0; } .slot-cards h2 { margin-top: 0; } .filters { display: grid; grid-template-columns: minmax(180px,1fr) 180px 160px auto; gap: 12px; margin-top: 24px; } .feedback { margin-bottom: 16px; } .state { padding: 36px 20px; text-align: center; } .table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid #176bdf; } table { width: 100%; min-width: 1080px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; } caption { text-align: left; padding-bottom: 16px; font-size: 12px; } th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; } thead th { color: #132941; background: #f3f7fd; } .el-button { margin-left: 0; } .mobile-list { display: none; } .card-heading { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: center; } .card-heading h2 { margin: 0; } .pagination { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 16px; color: #586b83; font-size: 12px; margin: 24px 0; } .pagination .el-select { width: 120px; } .notes { margin-top: 24px; background: #f0f6ff; } ul { padding-left: 20px; } dt { font-weight: 600; color: #132941; } dd { margin: 0 0 12px; overflow-wrap: anywhere; }
@media (max-width: 1050px) { .filters { grid-template-columns: repeat(2,minmax(0,1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } h1 { font-size: 25px; } }
@media (max-width: 420px) { .slot-cards, .filters { grid-template-columns: minmax(0,1fr); } .pagination { justify-content: center; } }
</style>
